package net.corda.bridge.services.crl

import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.protonwrapper.netty.ProxyConfig
import net.corda.nodeapi.internal.proxy.ProxySettings
import net.corda.nodeapi.internal.proxy.ProxyUtils
import java.net.Authenticator
import java.net.Proxy
import java.net.URL
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.x509.*
import java.io.ByteArrayInputStream

/**
 * Responsible for fetching CRLs by performing remote communication using optional Proxy configuration provided.
 */
class CrlFetcher(val proxyConfig: ProxyConfig?) {

    companion object {

        private val logger = contextLogger()

        // Theoretically, there might be multiple CrlFetchers operating at the same time. Since we are setting static variable via `Authenticator.setDefault()`
        // it is a good idea to protect the remote call with synchronized block on class level.
        @Synchronized
        private fun retrieveCrl(url: URL, proxySettings: ProxySettings): X509CRL? {
            val factory = CertificateFactory.getInstance("X.509")
            return try {
                val (proxy, authenticator, additionalSetupFn) = proxySettings
                authenticator?.let { Authenticator.setDefault(it) }
                additionalSetupFn?.invoke()
                val conn = url.openConnection(proxy)
                conn.connectTimeout = Integer.getInteger("net.corda.bridge.services.crl.fetcher.connectTimeoutMs", 60 * 1000)
                conn.readTimeout = Integer.getInteger("net.corda.bridge.services.crl.fetcher.readTimeoutMs", 60 * 1000)
                conn.getInputStream().use {
                    factory.generateCRL(it) as X509CRL
                }
            } catch (ex: Exception) {
                logger.error("Failed to fetch CRL from: $url")
                null
            }
        }
    }

    private val proxySettings: ProxySettings = proxyConfig?.let { ProxyUtils.fromConfig(it) } ?: ProxySettings(Proxy.NO_PROXY, null)

    fun fetch(cert: X509Certificate): Set<X509CRL> {

        logger.debug("Checking CRLDPs for ${cert.subjectX500Principal}")

        val crldpExtBytes = cert.getExtensionValue(Extension.cRLDistributionPoints.id)
        if(crldpExtBytes == null) {
            logger.debug("No CRLDP ext")
            return emptySet()
        }

        val derObjCrlDP = ASN1InputStream(ByteArrayInputStream(crldpExtBytes)).readObject()
        val dosCrlDP = derObjCrlDP as DEROctetString
        val crldpExtOctetsBytes = dosCrlDP.octets
        val dpObj = ASN1InputStream(ByteArrayInputStream(crldpExtOctetsBytes)).readObject()
        val distPoint = CRLDistPoint.getInstance(dpObj)

        val dpNames = distPoint.distributionPoints.mapNotNull { it.distributionPoint }.filter { it.type == DistributionPointName.FULL_NAME }
        val generalNames = dpNames.flatMap { GeneralNames.getInstance(it.name).names.asList() }
        val crlUrls = generalNames.filter { it.tagNo == GeneralName.uniformResourceIdentifier}.map { DERIA5String.getInstance(it.name).string }.toSet().map { URL(it) }

        return crlUrls.mapNotNull { url ->
            retrieveCrl(url, proxySettings)
        }.toSet()
    }
}
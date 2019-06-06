package net.corda.bridge.services.crl

import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.protonwrapper.netty.ProxyConfig
import net.corda.nodeapi.internal.proxy.ProxySettings
import net.corda.nodeapi.internal.proxy.ProxyUtils
import sun.security.x509.CRLDistributionPointsExtension
import sun.security.x509.GeneralNameInterface.NAME_URI
import sun.security.x509.URIName
import sun.security.x509.X509CertImpl
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.security.cert.CertificateFactory
import java.net.*

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
                val (proxy, authenticator) = proxySettings
                authenticator?.let { Authenticator.setDefault(it) }
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

    fun fetch(certificate: X509Certificate): Set<X509CRL> {

        val certImpl = X509CertImpl.toImpl(certificate)
        logger.debug("Checking CRLDPs for ${certImpl.subjectX500Principal}")

        val ext = certImpl.crlDistributionPointsExtension
        if (ext == null) {
            logger.debug("No CRLDP ext")
            return emptySet()
        }

        val points = ext.get(CRLDistributionPointsExtension.POINTS)

        // Logic borrowed from sun.security.provider.certpath.DistributionPointFetcher.getCRLs()
        val uriNames = points.flatMap { point -> point.fullName.names().filter { it.type == NAME_URI }}.map { it.name as URIName }

        return uriNames.map { it.uri.toURL() }.mapNotNull { url ->
            retrieveCrl(url, proxySettings)
        }.toSet()
    }
}
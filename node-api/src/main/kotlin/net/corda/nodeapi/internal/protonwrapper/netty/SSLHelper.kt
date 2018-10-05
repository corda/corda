package net.corda.nodeapi.internal.protonwrapper.netty

import io.netty.handler.ssl.SslHandler
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.toHex
import net.corda.nodeapi.ArtemisTcpTransport
import net.corda.nodeapi.internal.crypto.toBc
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.*
import java.util.*
import javax.net.ssl.*

private const val HOSTNAME_FORMAT = "%s.corda.net"

internal class LoggingTrustManagerWrapper(val wrapped: X509ExtendedTrustManager) : X509ExtendedTrustManager() {
    companion object {
        val log = contextLogger()
    }

    private fun certPathToString(certPath: Array<out X509Certificate>?): String {
        if (certPath == null) {
            return "<empty certpath>"
        }
        val certs = certPath.map {
            val bcCert = it.toBc()
            val subject = bcCert.subject.toString()
            val issuer = bcCert.issuer.toString()
            val keyIdentifier = try {
                SubjectKeyIdentifier.getInstance(bcCert.getExtension(Extension.subjectKeyIdentifier).parsedValue).keyIdentifier.toHex()
            } catch (ex: Exception) {
                "null"
            }
            val authorityKeyIdentifier = try {
                AuthorityKeyIdentifier.getInstance(bcCert.getExtension(Extension.authorityKeyIdentifier).parsedValue).keyIdentifier.toHex()
            } catch (ex: Exception) {
                "null"
            }
            "  $subject[$keyIdentifier] issued by $issuer[$authorityKeyIdentifier]"
        }
        return certs.joinToString("\r\n")
    }


    private fun certPathToStringFull(chain: Array<out X509Certificate>?): String {
        if (chain == null) {
            return "<empty certpath>"
        }
        return chain.map { it.toString() }.joinToString(", ")
    }

    private fun logErrors(chain: Array<out X509Certificate>?, block: () -> Unit) {
        try {
            block()
        } catch (ex: CertificateException) {
            log.error("Bad certificate path ${ex.message}:\r\n${certPathToStringFull(chain)}")
            throw ex
        }
    }

    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        log.info("Check Client Certpath:\r\n${certPathToString(chain)}")
        logErrors(chain) { wrapped.checkClientTrusted(chain, authType, socket) }
    }

    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        log.info("Check Client Certpath:\r\n${certPathToString(chain)}")
        logErrors(chain) { wrapped.checkClientTrusted(chain, authType, engine) }
    }

    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        log.info("Check Client Certpath:\r\n${certPathToString(chain)}")
        logErrors(chain) { wrapped.checkClientTrusted(chain, authType) }
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        log.info("Check Server Certpath:\r\n${certPathToString(chain)}")
        logErrors(chain) { wrapped.checkServerTrusted(chain, authType, socket) }
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        log.info("Check Server Certpath:\r\n${certPathToString(chain)}")
        logErrors(chain) { wrapped.checkServerTrusted(chain, authType, engine) }
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        log.info("Check Server Certpath:\r\n${certPathToString(chain)}")
        logErrors(chain) { wrapped.checkServerTrusted(chain, authType) }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = wrapped.acceptedIssuers

}

internal fun createClientSslHelper(target: NetworkHostAndPort,
                                   expectedRemoteLegalNames: Set<CordaX500Name>,
                                   keyManagerFactory: KeyManagerFactory,
                                   trustManagerFactory: TrustManagerFactory): SslHandler {
    val sslContext = SSLContext.getInstance("TLS")
    val keyManagers = keyManagerFactory.keyManagers
    val trustManagers = trustManagerFactory.trustManagers.filterIsInstance(X509ExtendedTrustManager::class.java).map { LoggingTrustManagerWrapper(it) }.toTypedArray()
    sslContext.init(keyManagers, trustManagers, SecureRandom())
    val sslEngine = sslContext.createSSLEngine(target.host, target.port)
    sslEngine.useClientMode = true
    sslEngine.enabledProtocols = ArtemisTcpTransport.TLS_VERSIONS.toTypedArray()
    sslEngine.enabledCipherSuites = ArtemisTcpTransport.CIPHER_SUITES.toTypedArray()
    sslEngine.enableSessionCreation = true
    if (expectedRemoteLegalNames.size == 1) {
        val sslParameters = sslEngine.sslParameters
        sslParameters.serverNames = listOf(SNIHostName(x500toHostName(expectedRemoteLegalNames.single())))
        sslEngine.sslParameters = sslParameters
    }
    return SslHandler(sslEngine)
}

internal fun createServerSslHelper(keyManagerFactory: KeyManagerFactory,
                                   trustManagerFactory: TrustManagerFactory): SslHandler {
    val sslContext = SSLContext.getInstance("TLS")
    val keyManagers = keyManagerFactory.keyManagers
    val trustManagers = trustManagerFactory.trustManagers.filterIsInstance(X509ExtendedTrustManager::class.java).map { LoggingTrustManagerWrapper(it) }.toTypedArray()
    sslContext.init(keyManagers, trustManagers, SecureRandom())
    val sslEngine = sslContext.createSSLEngine()
    sslEngine.useClientMode = false
    sslEngine.needClientAuth = true
    sslEngine.enabledProtocols = ArtemisTcpTransport.TLS_VERSIONS.toTypedArray()
    sslEngine.enabledCipherSuites = ArtemisTcpTransport.CIPHER_SUITES.toTypedArray()
    sslEngine.enableSessionCreation = true
    return SslHandler(sslEngine)
}

internal fun x500toHostName(x500Name: CordaX500Name): String {
    val secureHash = SecureHash.sha256(x500Name.toString())
    // RFC 1035 specifies a limit 255 bytes for hostnames with each label being 63 bytes or less. Due to this, the string
    // representation of the SHA256 hash is truncated to 32 characters.
    return String.format(HOSTNAME_FORMAT, secureHash.toString().take(32).toLowerCase())
}
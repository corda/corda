package net.corda.nodeapi.internal.protonwrapper.netty

import io.netty.buffer.ByteBufAllocator
import io.netty.handler.ssl.*
import io.netty.util.DomainNameMappingBuilder
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.newSecureRandom
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.toHex
import net.corda.nodeapi.internal.ArtemisTcpTransport
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.crypto.toBc
import net.corda.nodeapi.internal.crypto.x509
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import sun.security.x509.X500Name
import java.net.Socket
import java.security.KeyStore
import java.security.cert.*
import java.util.*
import javax.net.ssl.*

private const val HOSTNAME_FORMAT = "%s.corda.net"
internal const val DEFAULT = "default"
internal const val SSL_HANDSHAKE_TIMEOUT_PROP_NAME = "corda.netty.sslHelper.handshakeTimeout"

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
    sslContext.init(keyManagers, trustManagers, newSecureRandom())
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

internal fun createClientOpenSslHandler(target: NetworkHostAndPort,
                                        expectedRemoteLegalNames: Set<CordaX500Name>,
                                        keyManagerFactory: KeyManagerFactory,
                                        trustManagerFactory: TrustManagerFactory,
                                        alloc: ByteBufAllocator): SslHandler {
    val sslContext = SslContextBuilder.forClient().sslProvider(SslProvider.OPENSSL).keyManager(keyManagerFactory).trustManager(LoggingTrustManagerFactoryWrapper(trustManagerFactory)).build()
    val sslEngine = sslContext.newEngine(alloc, target.host, target.port)
    sslEngine.enabledProtocols = ArtemisTcpTransport.TLS_VERSIONS.toTypedArray()
    sslEngine.enabledCipherSuites = ArtemisTcpTransport.CIPHER_SUITES.toTypedArray()
    if (expectedRemoteLegalNames.size == 1) {
        val sslParameters = sslEngine.sslParameters
        sslParameters.serverNames = listOf(SNIHostName(x500toHostName(expectedRemoteLegalNames.single())))
        sslEngine.sslParameters = sslParameters
    }
    return SslHandler(sslEngine)
}

internal fun createServerSslHandler(keyStore: CertificateStore,
                                    keyManagerFactory: KeyManagerFactory,
                                    trustManagerFactory: TrustManagerFactory): SslHandler {
    val sslContext = SSLContext.getInstance("TLS")
    val keyManagers = keyManagerFactory.keyManagers
    val trustManagers = trustManagerFactory.trustManagers.filterIsInstance(X509ExtendedTrustManager::class.java).map { LoggingTrustManagerWrapper(it) }.toTypedArray()
    sslContext.init(keyManagers, trustManagers, newSecureRandom())
    val sslEngine = sslContext.createSSLEngine()
    sslEngine.useClientMode = false
    sslEngine.needClientAuth = true
    sslEngine.enabledProtocols = ArtemisTcpTransport.TLS_VERSIONS.toTypedArray()
    sslEngine.enabledCipherSuites = ArtemisTcpTransport.CIPHER_SUITES.toTypedArray()
    sslEngine.enableSessionCreation = true
    val sslParameters = sslEngine.sslParameters
    sslParameters.sniMatchers = listOf(ServerSNIMatcher(keyStore))
    sslEngine.sslParameters = sslParameters
    return SslHandler(sslEngine)
}

internal fun initialiseTrustStoreAndEnableCrlChecking(trustStore: CertificateStore, revocationConfig: RevocationConfig): ManagerFactoryParameters {
    val pkixParams = PKIXBuilderParameters(trustStore.value.internal, X509CertSelector())
    val revocationChecker = if (revocationConfig.mode == RevocationConfig.Mode.OFF) {
        AllowAllRevocationChecker  // Custom PKIXRevocationChecker
    } else {
        val certPathBuilder = CertPathBuilder.getInstance("PKIX")
        val pkixRevocationChecker = certPathBuilder.revocationChecker as PKIXRevocationChecker
        pkixRevocationChecker.options = EnumSet.of(
                // Prefer CRL over OCSP
                PKIXRevocationChecker.Option.PREFER_CRLS,
                // Don't fall back to OCSP checking
                PKIXRevocationChecker.Option.NO_FALLBACK)
        if (revocationConfig.mode == RevocationConfig.Mode.SOFT_FAIL) {
            // Allow revocation check to succeed if the revocation status cannot be determined for one of
            // the following reasons: The CRL or OCSP response cannot be obtained because of a network error.
            pkixRevocationChecker.options = pkixRevocationChecker.options + PKIXRevocationChecker.Option.SOFT_FAIL
        }
        pkixRevocationChecker
    }
    pkixParams.addCertPathChecker(revocationChecker)
    return CertPathTrustManagerParameters(pkixParams)
}

internal fun createServerOpenSslHandler(keyManagerFactory: KeyManagerFactory,
                                        trustManagerFactory: TrustManagerFactory,
                                        alloc: ByteBufAllocator): SslHandler {

    val sslContext = getServerSslContextBuilder(keyManagerFactory, trustManagerFactory).build()
    val sslEngine = sslContext.newEngine(alloc)
    sslEngine.useClientMode = false
    return SslHandler(sslEngine)
}

/**
 * Creates a special SNI handler used only when openSSL is used for AMQPServer
 */
internal fun createServerSNIOpenSslHandler(keyManagerFactoriesMap: Map<String, KeyManagerFactory>,
                                           trustManagerFactory: TrustManagerFactory): SniHandler {

    // Default value can be any in the map.
    val sslCtxBuilder = getServerSslContextBuilder(keyManagerFactoriesMap.values.first(), trustManagerFactory)
    val mapping = DomainNameMappingBuilder(sslCtxBuilder.build())
    keyManagerFactoriesMap.forEach {
        mapping.add(it.key, sslCtxBuilder.keyManager(it.value).build())
    }
    return SniHandler(mapping.build())
}

private fun getServerSslContextBuilder(keyManagerFactory: KeyManagerFactory, trustManagerFactory: TrustManagerFactory): SslContextBuilder {
    return SslContextBuilder.forServer(keyManagerFactory)
            .sslProvider(SslProvider.OPENSSL)
            .trustManager(LoggingTrustManagerFactoryWrapper(trustManagerFactory))
            .clientAuth(ClientAuth.REQUIRE)
            .ciphers(ArtemisTcpTransport.CIPHER_SUITES)
            .protocols(*ArtemisTcpTransport.TLS_VERSIONS.toTypedArray())
}

internal fun splitKeystore(config: AMQPConfiguration): Map<String, CertHoldingKeyManagerFactoryWrapper> {
    val keyStore = config.keyStore.value.internal
    val password = config.keyStore.entryPassword.toCharArray()
    return keyStore.aliases().toList().map { alias ->
        val key = keyStore.getKey(alias, password)
        val certs = keyStore.getCertificateChain(alias)
        val x500Name = keyStore.getCertificate(alias).x509.subjectDN as X500Name
        val cordaX500Name = CordaX500Name.build(x500Name.asX500Principal())
        val newKeyStore = KeyStore.getInstance("JKS")
        newKeyStore.load(null)
        newKeyStore.setKeyEntry(alias, key, password, certs)
        val newKeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        newKeyManagerFactory.init(newKeyStore, password)
        x500toHostName(cordaX500Name) to CertHoldingKeyManagerFactoryWrapper(newKeyManagerFactory, config)
    }.toMap()
}

// As per Javadoc in: https://docs.oracle.com/javase/8/docs/api/javax/net/ssl/KeyManagerFactory.html `init` method
// 2nd parameter `password` - the password for recovering keys in the KeyStore
fun KeyManagerFactory.init(keyStore: CertificateStore) = init(keyStore.value.internal, keyStore.entryPassword.toCharArray())

fun TrustManagerFactory.init(trustStore: CertificateStore) = init(trustStore.value.internal)

/**
 * Method that converts a [CordaX500Name] to a a valid hostname (RFC-1035). It's used for SNI to indicate the target
 * when trying to communicate with nodes that reside behind the same firewall. This is a solution to TLS's extension not
 * yet supporting x500 names as server names
 */
internal fun x500toHostName(x500Name: CordaX500Name): String {
    val secureHash = SecureHash.sha256(x500Name.toString())
    // RFC 1035 specifies a limit 255 bytes for hostnames with each label being 63 bytes or less. Due to this, the string
    // representation of the SHA256 hash is truncated to 32 characters.
    return String.format(HOSTNAME_FORMAT, secureHash.toString().take(32).toLowerCase())
}

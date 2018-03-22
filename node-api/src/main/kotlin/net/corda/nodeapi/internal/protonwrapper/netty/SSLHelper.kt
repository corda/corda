package net.corda.nodeapi.internal.protonwrapper.netty

import io.netty.handler.ssl.SslHandler
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.ArtemisTcpTransport
import net.corda.nodeapi.internal.config.RevocationCheckConfig
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertPathBuilder
import java.security.cert.PKIXBuilderParameters
import java.security.cert.PKIXRevocationChecker
import java.security.cert.X509CertSelector
import java.util.*
import javax.net.ssl.*

internal fun createClientSslHelper(target: NetworkHostAndPort,
                                   keyManagerFactory: KeyManagerFactory,
                                   trustManagerFactory: TrustManagerFactory): SslHandler {
    val sslContext = SSLContext.getInstance("TLS")
    val keyManagers = keyManagerFactory.keyManagers
    val trustManagers = trustManagerFactory.trustManagers
    sslContext.init(keyManagers, trustManagers, SecureRandom())
    val sslEngine = sslContext.createSSLEngine(target.host, target.port)
    sslEngine.useClientMode = true
    sslEngine.enabledProtocols = ArtemisTcpTransport.TLS_VERSIONS.toTypedArray()
    sslEngine.enabledCipherSuites = ArtemisTcpTransport.CIPHER_SUITES.toTypedArray()
    sslEngine.enableSessionCreation = true
    return SslHandler(sslEngine)
}

internal fun createServerSslHelper(keyManagerFactory: KeyManagerFactory,
                                   trustManagerFactory: TrustManagerFactory): SslHandler {
    val sslContext = SSLContext.getInstance("TLS")
    val keyManagers = keyManagerFactory.keyManagers
    val trustManagers = trustManagerFactory.trustManagers
    sslContext.init(keyManagers, trustManagers, SecureRandom())
    val sslEngine = sslContext.createSSLEngine()
    sslEngine.useClientMode = false
    sslEngine.needClientAuth = true
    sslEngine.enabledProtocols = ArtemisTcpTransport.TLS_VERSIONS.toTypedArray()
    sslEngine.enabledCipherSuites = ArtemisTcpTransport.CIPHER_SUITES.toTypedArray()
    sslEngine.enableSessionCreation = true
    return SslHandler(sslEngine)
}

internal fun initialiseTrustStoreAndEnableCrlChecking(trustStore: KeyStore, revocationCheckConfig: RevocationCheckConfig): ManagerFactoryParameters {
    val certPathBuilder = CertPathBuilder.getInstance("PKIX")
    val revocationChecker = certPathBuilder.revocationChecker as PKIXRevocationChecker
    revocationChecker.options = EnumSet.of(
            PKIXRevocationChecker.Option.PREFER_CRLS,
            PKIXRevocationChecker.Option.NO_FALLBACK)
    if (revocationCheckConfig.preferCrl) {
        // Prefer CRL over OCSP
        revocationChecker.options = revocationChecker.options + PKIXRevocationChecker.Option.PREFER_CRLS
    }
    if (revocationCheckConfig.noFallback) {
        // Don't fall back to OCSP checking
        revocationChecker.options = revocationChecker.options + PKIXRevocationChecker.Option.NO_FALLBACK
    }
    if (revocationCheckConfig.softFail) {
        // Allow revocation check to succeed if the revocation status cannot be determined for one of
        // the following reasons: The CRL or OCSP response cannot be obtained because of a network error.
        revocationChecker.options = revocationChecker.options + PKIXRevocationChecker.Option.SOFT_FAIL
    }
    val pkixParams = PKIXBuilderParameters(trustStore, X509CertSelector())
    pkixParams.addCertPathChecker(revocationChecker)
    return CertPathTrustManagerParameters(pkixParams)
}

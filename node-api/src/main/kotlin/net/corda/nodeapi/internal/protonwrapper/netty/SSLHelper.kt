/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.protonwrapper.netty

import io.netty.handler.ssl.SslHandler
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.ArtemisTcpTransport
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

internal fun initialiseTrustStoreAndEnableCrlChecking(trustStore: KeyStore, crlCheckSoftFail: Boolean): ManagerFactoryParameters {
    val certPathBuilder = CertPathBuilder.getInstance("PKIX")
    val revocationChecker = certPathBuilder.revocationChecker as PKIXRevocationChecker
    revocationChecker.options = EnumSet.of(
            // Prefer CRL over OCSP
            PKIXRevocationChecker.Option.PREFER_CRLS,
            // Don't fall back to OCSP checking
            PKIXRevocationChecker.Option.NO_FALLBACK)
    if (crlCheckSoftFail) {
        // Allow revocation check to succeed if the revocation status cannot be determined for one of
        // the following reasons: The CRL or OCSP response cannot be obtained because of a network error.
        revocationChecker.options = revocationChecker.options + PKIXRevocationChecker.Option.SOFT_FAIL
    }
    val pkixParams = PKIXBuilderParameters(trustStore, X509CertSelector())
    pkixParams.addCertPathChecker(revocationChecker)
    return CertPathTrustManagerParameters(pkixParams)
}

package net.corda.node.utilities

import net.corda.core.crypto.Crypto
import net.corda.nodeapi.internal.crypto.*
import java.nio.file.Path
import java.security.KeyPair
import java.security.cert.X509Certificate
import java.time.Duration
import javax.security.auth.x500.X500Principal

fun createKeyPairAndSelfSignedTLSCertificate(x500Principal: X500Principal): Pair<KeyPair, X509Certificate> {
    val rpcKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    val selfSignCert = createSelfSignedTLSCertificate(x500Principal, rpcKeyPair)
    return Pair(rpcKeyPair, selfSignCert)
}

fun createSelfSignedTLSCertificate(subject: X500Principal,
                                  keyPair: KeyPair,
                                  validityWindow: Pair<Duration, Duration> = X509Utilities.DEFAULT_VALIDITY_WINDOW): X509Certificate {
    val window = X509Utilities.getCertificateValidityWindow(validityWindow.first, validityWindow.second)
    return X509Utilities.createCertificate(CertificateType.TLS, subject, keyPair, subject, keyPair.public, window)
}

fun saveToKeyStore(keyStorePath: Path, rpcKeyPair: KeyPair, selfSignCert: X509Certificate, password: String = "password", alias: String = "Key"): Path {
    val keyStore = loadOrCreateKeyStore(keyStorePath, password)
    keyStore.addOrReplaceKey(alias, rpcKeyPair.private, password.toCharArray(), arrayOf(selfSignCert))
    keyStore.save(keyStorePath, password)
    return keyStorePath
}

fun saveToTrustStore(trustStorePath: Path, selfSignCert: X509Certificate, password: String = "password", alias: String = "Key"): Path {
    val trustStore = loadOrCreateKeyStore(trustStorePath, password)
    trustStore.addOrReplaceCertificate(alias, selfSignCert)
    trustStore.save(trustStorePath, password)
    return trustStorePath
}
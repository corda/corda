package net.corda.node.utilities

import net.corda.core.crypto.Crypto
import net.corda.nodeapi.internal.crypto.*
import java.nio.file.Path
import java.security.KeyPair
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

val testName = X500Principal("CN=Test,O=R3 Ltd,L=London,C=GB")

fun createKeyPairAndSelfSignedCertificate(x500Principal: X500Principal= testName): Pair<KeyPair, X509Certificate> {
    val rpcKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    val selfSignCert = X509Utilities.createSelfSignedCACertificate(x500Principal, rpcKeyPair)
    return Pair(rpcKeyPair, selfSignCert)
}

fun saveToKeyStore(keyStorePath: Path, rpcKeyPair: KeyPair, selfSignCert: X509Certificate, password: String = "password"): Path {
    val keyStore = loadOrCreateKeyStore(keyStorePath, password)
    keyStore.addOrReplaceKey("Key", rpcKeyPair.private, password.toCharArray(), arrayOf(selfSignCert))
    keyStore.save(keyStorePath, password)
    return keyStorePath
}

fun saveToTrustStore(trustStorePath: Path, selfSignCert: X509Certificate, password: String = "password"): Path {
    val trustStore = loadOrCreateKeyStore(trustStorePath, password)
    trustStore.addOrReplaceCertificate("Key", selfSignCert)
    trustStore.save(trustStorePath, password)
    return trustStorePath
}
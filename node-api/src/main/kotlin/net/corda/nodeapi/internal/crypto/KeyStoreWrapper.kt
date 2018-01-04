package net.corda.nodeapi.internal.crypto

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.read
import java.nio.file.Path
import java.security.KeyPair
import java.security.cert.Certificate

class KeyStoreWrapper(private val storePath: Path, private val storePassword: String) {
    private val keyStore = storePath.read { loadKeyStore(it, storePassword) }

    // TODO This method seems misplaced in this class.
    fun storeLegalIdentity(legalName: CordaX500Name, alias: String, keyPair: KeyPair): PartyAndCertificate {
        val nodeCaCertChain = keyStore.getCertificateChain(X509Utilities.CORDA_CLIENT_CA)
        val nodeCa = getCertificateAndKeyPair(X509Utilities.CORDA_CLIENT_CA)
        val identityCert = X509Utilities.createCertificate(
                CertificateType.LEGAL_IDENTITY,
                nodeCa.certificate,
                nodeCa.keyPair,
                legalName.x500Principal,
                keyPair.public)
        val identityCertPath = X509CertificateFactory().generateCertPath(identityCert, *nodeCaCertChain)
        // Assume key password = store password.
        keyStore.addOrReplaceKey(alias, keyPair.private, storePassword.toCharArray(), identityCertPath.certificates.toTypedArray())
        keyStore.save(storePath, storePassword)
        return PartyAndCertificate(identityCertPath)
    }

    // Delegate methods to keystore. Sadly keystore doesn't have an interface.
    fun containsAlias(alias: String) = keyStore.containsAlias(alias)

    fun getX509Certificate(alias: String) = keyStore.getX509Certificate(alias)

    fun getCertificateChain(alias: String): Array<out Certificate> = keyStore.getCertificateChain(alias)

    fun getCertificate(alias: String): Certificate = keyStore.getCertificate(alias)

    fun getCertificateAndKeyPair(alias: String): CertificateAndKeyPair = keyStore.getCertificateAndKeyPair(alias, storePassword)
}
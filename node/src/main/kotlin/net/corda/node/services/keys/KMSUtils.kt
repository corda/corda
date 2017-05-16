package net.corda.node.services.keys

import net.corda.core.crypto.CertificateType
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.X509Utilities
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.KeyManagementService
import java.security.cert.CertPath
import java.security.cert.X509Certificate

/**
 * Generates a new random [KeyPair], adds it to the internal key storage, then generates a corresponding
 * [X509Certificate] and adds it to the identity service.
 *
 * @param keyManagementService key service to use when generating the new key.
 * @param identityService identity service to use when registering the certificate.
 * @param identity identity to generate a key and certificate for. Must be an identity this node has CA privileges for.
 * @param revocationEnabled whether to check revocation status of certificates in the certificate path.
 * @return X.509 certificate and path to the trust root.
 */
fun freshKeyAndCert(keyManagementService: KeyManagementService,
                    identityService: IdentityService,
                    identity: Party,
                    revocationEnabled: Boolean = false): Pair<X509Certificate, CertPath> {
    val ourPublicKey = keyManagementService.freshKey()
    // FIXME: Use the actual certificate for the identity the flow is presenting themselves as
    val issuerKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_IDENTITY_SIGNATURE_SCHEME)
    val issuerCertificate = X509Utilities.createSelfSignedCACertificate(identity.name, issuerKey)
    val ourCertificate = X509Utilities.createCertificate(CertificateType.IDENTITY, issuerCertificate, issuerKey, identity.name, ourPublicKey)
    val ourCertPath = X509Utilities.createCertificatePath(issuerCertificate, ourCertificate, revocationEnabled = revocationEnabled)
    identityService.registerPath(issuerCertificate,
            AnonymousParty(ourPublicKey),
            ourCertPath)
    return Pair(issuerCertificate, ourCertPath)
}
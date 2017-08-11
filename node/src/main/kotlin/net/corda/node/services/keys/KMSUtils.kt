package net.corda.node.services.keys

import net.corda.core.crypto.ContentSignerBuilder
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.cert
import net.corda.core.identity.AnonymousPartyAndPath
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.IdentityService
import net.corda.core.utilities.days
import net.corda.node.utilities.CertificateType
import net.corda.node.utilities.X509Utilities
import org.bouncycastle.operator.ContentSigner
import java.security.KeyPair
import java.security.PublicKey
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration

/**
 * Generates a new random [KeyPair], adds it to the internal key storage, then generates a corresponding
 * [X509Certificate] and adds it to the identity service.
 *
 * @param identityService issuer service to use when registering the certificate.
 * @param subjectPublicKey public key of new identity.
 * @param issuer issuer to generate a key and certificate for. Must be an identity this node has the private key for.
 * @param issuerSigner a content signer for the issuer.
 * @param revocationEnabled whether to check revocation status of certificates in the certificate path.
 * @return X.509 certificate and path to the trust root.
 */
fun freshCertificate(identityService: IdentityService,
                     subjectPublicKey: PublicKey,
                     issuer: PartyAndCertificate,
                     issuerSigner: ContentSigner,
                     revocationEnabled: Boolean = false): AnonymousPartyAndPath {
    val issuerCertificate = issuer.certificate
    val window = X509Utilities.getCertificateValidityWindow(Duration.ZERO, 3650.days, issuerCertificate)
    val ourCertificate = X509Utilities.createCertificate(CertificateType.IDENTITY, issuerCertificate.subject, issuerSigner, issuer.name, subjectPublicKey, window)
    val certFactory = CertificateFactory.getInstance("X509")
    val ourCertPath = certFactory.generateCertPath(listOf(ourCertificate.cert) + issuer.certPath.certificates)
    val anonymisedIdentity = AnonymousPartyAndPath(subjectPublicKey, ourCertPath)
    identityService.verifyAndRegisterAnonymousIdentity(anonymisedIdentity,
            issuer.party)
    return anonymisedIdentity
}

fun getSigner(issuerKeyPair: KeyPair): ContentSigner {
    val signatureScheme = Crypto.findSignatureScheme(issuerKeyPair.private)
    val provider = Security.getProvider(signatureScheme.providerName)
    return ContentSignerBuilder.build(signatureScheme, issuerKeyPair.private, provider)
}

package net.corda.node.services.keys

import net.corda.core.crypto.CertificateType
import net.corda.core.crypto.ContentSignerBuilder
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.X509Utilities
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.IdentityService
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.operator.ContentSigner
import java.security.KeyPair
import java.security.PublicKey
import java.security.Security
import java.security.cert.CertPath
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.*

/**
 * Generates a new random [KeyPair], adds it to the internal key storage, then generates a corresponding
 * [X509Certificate] and adds it to the identity service.
 *
 * @param subjectPublicKey public key of new identity.
 * @param issuerSigner a content signer for the issuer.
 * @param identityService issuer service to use when registering the certificate.
 * @param issuer issuer to generate a key and certificate for. Must be an identity this node has the private key for.
 * @param revocationEnabled whether to check revocation status of certificates in the certificate path.
 * @return X.509 certificate and path to the trust root.
 */
fun freshCertificate(identityService: IdentityService,
                     subjectPublicKey: PublicKey,
                     issuer: PartyAndCertificate,
                     issuerSigner: ContentSigner,
                     revocationEnabled: Boolean = false): Pair<X509CertificateHolder, CertPath> {
    val issuerCertificate = issuer.certificate
    val window = X509Utilities.getCertificateValidityWindow(Duration.ZERO, Duration.ofDays(10 * 365), issuerCertificate)
    val ourCertificate = Crypto.createCertificate(CertificateType.IDENTITY, issuerCertificate.subject, issuerSigner, issuer.name, subjectPublicKey, window)
    val actualPublicKey = Crypto.toSupportedPublicKey(ourCertificate.subjectPublicKeyInfo)
    require(subjectPublicKey == actualPublicKey)
    val ourCertPath = X509Utilities.createCertificatePath(issuerCertificate, ourCertificate, revocationEnabled = revocationEnabled)
    require(Arrays.equals(ourCertificate.subjectPublicKeyInfo.encoded, subjectPublicKey.encoded))
    identityService.registerAnonymousIdentity(AnonymousParty(subjectPublicKey),
            issuer.party,
            ourCertPath)
    return Pair(issuerCertificate, ourCertPath)
}

fun getSigner(issuerKeyPair: KeyPair): ContentSigner {
    val signatureScheme = Crypto.findSignatureScheme(issuerKeyPair.private)
    val provider = Security.getProvider(signatureScheme.providerName)
    return ContentSignerBuilder.build(signatureScheme, issuerKeyPair.private, provider)
}

/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.keys

import net.corda.core.crypto.Crypto
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.CertRole
import net.corda.core.node.services.IdentityService
import net.corda.core.utilities.days
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.ContentSignerBuilder
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.x509Certificates
import org.bouncycastle.operator.ContentSigner
import java.security.KeyPair
import java.security.PublicKey
import java.security.Security
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
                     revocationEnabled: Boolean = false): PartyAndCertificate {
    val issuerRole = CertRole.extract(issuer.certificate)
    require(issuerRole == CertRole.LEGAL_IDENTITY) { "Confidential identities can only be issued from well known identities, provided issuer ${issuer.name} has role $issuerRole" }
    val issuerCert = issuer.certificate
    val window = X509Utilities.getCertificateValidityWindow(Duration.ZERO, 3650.days, issuerCert)
    val ourCertificate = X509Utilities.createCertificate(
            CertificateType.CONFIDENTIAL_LEGAL_IDENTITY,
            issuerCert.subjectX500Principal,
            issuerCert.publicKey,
            issuerSigner,
            issuer.name.x500Principal,
            subjectPublicKey,
            window)
    val ourCertPath = X509Utilities.buildCertPath(ourCertificate, issuer.certPath.x509Certificates)
    val anonymisedIdentity = PartyAndCertificate(ourCertPath)
    if (identityService is IdentityServiceInternal) {
        identityService.justVerifyAndRegisterIdentity(anonymisedIdentity)
    } else {
        identityService.verifyAndRegisterIdentity(anonymisedIdentity)
    }
    return anonymisedIdentity
}

fun getSigner(issuerKeyPair: KeyPair): ContentSigner {
    val signatureScheme = Crypto.findSignatureScheme(issuerKeyPair.private)
    val provider = Security.getProvider(signatureScheme.providerName)
    return ContentSignerBuilder.build(signatureScheme, issuerKeyPair.private, provider)
}

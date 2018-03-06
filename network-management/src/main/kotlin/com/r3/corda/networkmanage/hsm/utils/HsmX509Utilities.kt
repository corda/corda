/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm.utils

import CryptoServerJCE.CryptoServerProvider
import net.corda.core.CordaOID
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.x500Name
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.getX509Certificate
import net.corda.nodeapi.internal.crypto.toJca
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.bc.BcX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

object HsmX509Utilities {

    const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    /**
     * Create a de novo root self-signed X509 v3 CA cert for the specified [KeyPair].
     * @param type type of the certificate to be created
     * @param subject X500 name of the certificate subject
     * @param keyPair public and private keys to be associated with the generated certificate
     * @param validDays number of days which this certificate is valid for
     * @param provider provider to be used during the certificate signing process
     * @param crlDistPoint url to the certificate revocation list of this certificate
     * @param crlIssuer issuer of the certificate revocation list of this certificate
     * @return the new root cert.
     * Note the generated certificate tree is capped at max depth of 2 to be in line with commercially available certificates
     */
    fun createSelfSignedCert(type: CertificateType,
                             subject: X500Name,
                             keyPair: KeyPair,
                             validDays: Int,
                             provider: Provider,
                             crlDistPoint: String? = null,
                             crlIssuer: X500Name? = null): X509Certificate {
        // TODO this needs to be changed
        val serial = BigInteger.valueOf(random63BitValue(provider))

        // Ten year certificate validity
        // TODO how do we manage certificate expiry, revocation and loss
        val window = getCertificateValidityWindow(0, validDays)
        val keyPurposes = DERSequence(ASN1EncodableVector().apply { type.purposes.forEach { add(it) } })

        val builder = JcaX509v3CertificateBuilder(subject, serial, window.first, window.second, subject, keyPair.public)
        builder.addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyIdentifier(keyPair.public))
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(type.isCA))
        builder.addExtension(Extension.keyUsage, false, type.keyUsage)
        builder.addExtension(Extension.extendedKeyUsage, false, keyPurposes)
        builder.addExtension(Extension.authorityKeyIdentifier, false, JcaX509ExtensionUtils().createAuthorityKeyIdentifier(keyPair.public))
        if (type.role != null) {
            builder.addExtension(ASN1ObjectIdentifier(CordaOID.X509_EXTENSION_CORDA_ROLE), false, type.role)
        }
        addCrlInfo(builder, crlDistPoint, crlIssuer)

        val cert = signCertificate(builder, keyPair.private, provider)

        cert.checkValidity()
        cert.verify(keyPair.public)

        return cert
    }

    /**
     * This is a helper function, which purpose is to workaround a bug in the bouncycastle library
     * that is associated with the incorrect encoded byte production when the EC algorithm is used with the passed keys.
     * @param publicKey public key
     * @return cleaned [PublicKey] instance
     */
    fun cleanEcdsaPublicKey(publicKey: PublicKey): PublicKey {
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKey.encoded))
    }

    /**
     * Retrieves key pair and certificate from the given key store. Also, the keys retrieved are cleaned in a sense of the
     * [cleanEcdsaPublicKey] method.
     * @param alias certificate and key name (alias) to be used when querying the key store.
     * @param keyStore key store that holds the certificate with its keys.
     * @return instance of [CertificateAndKeyPair] holding the key pair and the certificate.
     */
    fun retrieveCertAndKeyPair(alias: String, keyStore: KeyStore): CertificateAndKeyPair {
        val privateKey = keyStore.getKey(alias, null) as PrivateKey
        val certificate = keyStore.getX509Certificate(alias)
        return CertificateAndKeyPair(certificate, KeyPair(cleanEcdsaPublicKey(certificate.publicKey), privateKey))
    }

    /**
     * Create a de novo root intermediate X509 v3 CA cert and KeyPair.
     * @param type type of the certificate to be created
     * @param subject X500 name of the certificate subject
     * @param certificateAuthority The Public certificate and KeyPair of the root CA certificate above this used to sign it.
     * @param keyPair public and private keys to be associated with the generated certificate
     * @param validDays number of days which this certificate is valid for
     * @param provider provider to be used during the certificate signing process
     * @param crlDistPoint url to the certificate revocation list of this certificate
     * @param crlIssuer issuer of the certificate revocation list of this certificate
     * @return an instance of [CertificateAndKeyPair] class is returned containing the new intermediate CA Cert and its KeyPair for signing downstream certificates.
     * Note the generated certificate tree is capped at max depth of 1 below this to be in line with commercially available certificates
     */
    fun createIntermediateCert(type: CertificateType,
                               subject: X500Name,
                               certificateAuthority: CertificateAndKeyPair,
                               keyPair: KeyPair,
                               validDays: Int,
                               provider: Provider,
                               crlDistPoint: String?,
                               crlIssuer: X500Name?): CertificateAndKeyPair {

        val issuer = X509CertificateHolder(certificateAuthority.certificate.encoded).subject
        val serial = BigInteger.valueOf(random63BitValue(provider))
        val pubKey = keyPair.public

        // Ten year certificate validity
        // TODO how do we manage certificate expiry, revocation and loss
        val window = getCertificateValidityWindow(0, validDays, certificateAuthority.certificate.notBefore, certificateAuthority.certificate.notAfter)
        val keyPurposes = DERSequence(ASN1EncodableVector().apply { type.purposes.forEach { add(it) } })

        val builder = JcaX509v3CertificateBuilder(issuer, serial, window.first, window.second, subject, pubKey)
        builder.addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyIdentifier(pubKey))
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(type.isCA))
        builder.addExtension(Extension.keyUsage, false, type.keyUsage)
        builder.addExtension(Extension.extendedKeyUsage, false, keyPurposes)
        builder.addExtension(Extension.authorityKeyIdentifier, false, JcaX509ExtensionUtils().createAuthorityKeyIdentifier(certificateAuthority.keyPair.public))
        if (type.role != null) {
            builder.addExtension(ASN1ObjectIdentifier(CordaOID.X509_EXTENSION_CORDA_ROLE), false, type.role)
        }
        addCrlInfo(builder, crlDistPoint, crlIssuer)

        val cert = signCertificate(builder, certificateAuthority.keyPair.private, provider)

        cert.checkValidity(Date())
        cert.verify(certificateAuthority.keyPair.public)

        return CertificateAndKeyPair(cert, keyPair)
    }

    /**
     * Creates and signs a X509 v3 client certificate.
     * @param type type of the certificate to be created
     * @param caCertAndKey signing certificate authority certificate and its keys
     * @param request certificate signing request
     * @param validDays number of days which this certificate is valid for
     * @param provider provider to be used during the certificate signing process
     * @param crlDistPoint url to the certificate revocation list of this certificate
     * @param crlIssuer issuer of the certificate revocation list of this certificate
     * @return an instance of [CertificateAndKeyPair] class is returned containing the signed client certificate.
     */
    fun createClientCertificate(type: CertificateType,
                                caCertAndKey: CertificateAndKeyPair,
                                request: PKCS10CertificationRequest,
                                validDays: Int,
                                provider: Provider,
                                crlDistPoint: String?,
                                crlIssuer: X500Name?): X509Certificate {
        val jcaRequest = JcaPKCS10CertificationRequest(request)
        // This can be adjusted more to our future needs.
        val nameConstraints = NameConstraints(arrayOf(GeneralSubtree(GeneralName(GeneralName.directoryName, CordaX500Name.parse(jcaRequest.subject.toString()).copy(commonName = null).x500Name))), arrayOf())
        val issuerCertificate = caCertAndKey.certificate
        val issuerKeyPair = caCertAndKey.keyPair
        val validityWindow = getCertificateValidityWindow(0, validDays, issuerCertificate.notBefore, issuerCertificate.notAfter)
        val serial = BigInteger.valueOf(random63BitValue(provider))
        val subject = CordaX500Name.parse(jcaRequest.subject.toString()).x500Name
        val subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(jcaRequest.publicKey.encoded))
        val keyPurposes = DERSequence(ASN1EncodableVector().apply { type.purposes.forEach { add(it) } })
        val builder = JcaX509v3CertificateBuilder(issuerCertificate, serial, validityWindow.first, validityWindow.second, subject, jcaRequest.publicKey)
                .addExtension(Extension.subjectKeyIdentifier, false, BcX509ExtensionUtils().createSubjectKeyIdentifier(subjectPublicKeyInfo))
                .addExtension(Extension.basicConstraints, type.isCA, BasicConstraints(type.isCA))
                .addExtension(Extension.keyUsage, false, type.keyUsage)
                .addExtension(Extension.extendedKeyUsage, false, keyPurposes)
                .addExtension(Extension.nameConstraints, true, nameConstraints)
                .addExtension(Extension.authorityKeyIdentifier, false, JcaX509ExtensionUtils().createAuthorityKeyIdentifier(issuerKeyPair.public))
        if (type.role != null) {
            builder.addExtension(ASN1ObjectIdentifier(CordaOID.X509_EXTENSION_CORDA_ROLE), false, type.role)
        }
        addCrlInfo(builder, crlDistPoint, crlIssuer)
        val certificate = signCertificate(builder, issuerKeyPair.private, provider)
        certificate.checkValidity(Date())
        certificate.verify(issuerKeyPair.public)
        return certificate
    }

    /**
     * Helper method to get a notBefore and notAfter pair from current day bounded by parent certificate validity range
     * @param daysBefore number of days to roll back returned start date relative to current date
     * @param daysAfter number of days to roll forward returned end date relative to current date
     * @param parentNotBefore if provided is used to lower bound the date interval returned
     * @param parentNotAfter if provided is used to upper bound the date interval returned
     * Note we use Date rather than LocalDate as the consuming java.security and BouncyCastle certificate apis all use Date
     * Thus we avoid too many round trip conversions.
     */
    fun getCertificateValidityWindow(daysBefore: Int, daysAfter: Int, parentNotBefore: Date? = null, parentNotAfter: Date? = null): Pair<Date, Date> {
        val startOfDayUTC = Instant.now().truncatedTo(ChronoUnit.DAYS)

        var notBefore = Date.from(startOfDayUTC.minus(daysBefore.toLong(), ChronoUnit.DAYS))
        if (parentNotBefore != null) {
            if (parentNotBefore.after(notBefore)) {
                notBefore = parentNotBefore
            }
        }

        var notAfter = Date.from(startOfDayUTC.plus(daysAfter.toLong(), ChronoUnit.DAYS))
        if (parentNotAfter != null) {
            if (parentNotAfter.after(notAfter)) {
                notAfter = parentNotAfter
            }
        }

        return Pair(notBefore, notAfter)
    }

    /**
     * Creates and initializes a key store from the given crypto server provider.
     * @param provider crypto server provider to be used for the key store creation
     * @return created key store instance
     */
    fun getAndInitializeKeyStore(provider: CryptoServerProvider): KeyStore {
        val keyStore = KeyStore.getInstance("CryptoServer", provider)
        keyStore.load(null, null)
        return keyStore
    }

    /**
     * Encode provided public key in correct format for inclusion in certificate issuer/subject fields
     */
    private fun createSubjectKeyIdentifier(key: Key): SubjectKeyIdentifier {
        val info = SubjectPublicKeyInfo.getInstance(key.encoded)
        return BcX509ExtensionUtils().createSubjectKeyIdentifier(info)
    }

    /**
     * Generate a random value using the provider.
     */
    private fun random63BitValue(provider: Provider): Long = Math.abs(newSecureRandom(provider).nextLong())

    private fun newSecureRandom(provider: Provider? = null): SecureRandom {
        if (provider != null && provider.name == "CryptoServer") {
            return SecureRandom.getInstance("CryptoServer", provider)
        }
        if (System.getProperty("os.name") == "Linux") {
            return SecureRandom.getInstance("NativePRNGNonBlocking")
        } else {
            return SecureRandom.getInstanceStrong()
        }
    }

    fun verify(data: ByteArray,
               signature: ByteArray,
               publicKey: PublicKey,
               signatureAlgorithm: String = SIGNATURE_ALGORITHM) {
        val verify = Signature.getInstance(signatureAlgorithm)
        verify.initVerify(publicKey)
        verify.update(data)
        require(verify.verify(signature)) { "Signature didn't independently verify" }
    }

    /**
     * Use bouncy castle utilities to sign completed X509 certificate with CA cert private key
     */
    private fun signCertificate(certificateBuilder: X509v3CertificateBuilder,
                                signedWithPrivateKey: PrivateKey,
                                provider: Provider,
                                signatureAlgorithm: String = SIGNATURE_ALGORITHM): X509Certificate {
        val signer = JcaContentSignerBuilder(signatureAlgorithm).setProvider(provider).build(signedWithPrivateKey)
        return certificateBuilder.build(signer).toJca()
    }

    private fun addCrlInfo(builder: X509v3CertificateBuilder, crlDistPoint: String?, crlIssuer: X500Name?) {
        if (crlDistPoint != null) {
            val distPointName = DistributionPointName(GeneralNames(GeneralName(GeneralName.uniformResourceIdentifier, crlDistPoint)))
            val crlIssuerGeneralNames = crlIssuer?.let {
                GeneralNames(GeneralName(crlIssuer))
            }
            // The second argument is flag that allows you to define what reason of certificate revocation is served by this distribution point see [ReasonFlags].
            // The idea is that you have different revocation per revocation reason. Since we won't go into such a granularity, we can skip that parameter.
            // The third argument allows you to specify the name of the CRL issuer, it needs to be consistent with the crl (IssuingDistributionPoint) extension and the idp argument.
            // If idp == true, set it, if idp == false, leave it null as done here.
            val distPoint = DistributionPoint(distPointName, null, crlIssuerGeneralNames)
            builder.addExtension(Extension.cRLDistributionPoints, false, CRLDistPoint(arrayOf(distPoint)))
        }
    }
}

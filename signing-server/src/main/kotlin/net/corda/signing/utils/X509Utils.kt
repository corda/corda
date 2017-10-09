package net.corda.signing.utils

import CryptoServerJCE.CryptoServerProvider
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.toX509CertHolder
import net.corda.node.utilities.CertificateAndKeyPair
import net.corda.node.utilities.CertificateType
import net.corda.node.utilities.X509Utilities
import net.corda.node.utilities.getX509Certificate
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.bc.BcX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import java.math.BigInteger
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

object X509Utilities {

    val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    /**
     * Create a de novo root self-signed X509 v3 CA cert for the specified [KeyPair].
     * @param legalName The Common (CN) field of the cert Subject will be populated with the domain string
     * @param keyPair public and private keys to be associated with the generated certificate
     * @param validDays number of days which this certificate is valid for
     * @param provider provider to be used during the certificate signing process
     * @return an instance of [CertificateAndKeyPair] class is returned containing the new root CA Cert and its [KeyPair] for signing downstream certificates.
     * Note the generated certificate tree is capped at max depth of 2 to be in line with commercially available certificates
     */
    fun createSelfSignedCACert(legalName: String, keyPair: KeyPair, validDays: Int, provider: Provider): CertificateAndKeyPair {
        // TODO this needs to be chaneged
        val issuer = getDevX509Name(legalName)
        val serial = BigInteger.valueOf(random63BitValue(provider))
        val subject = issuer
        val pubKey = keyPair.public

        // Ten year certificate validity
        // TODO how do we manage certificate expiry, revocation and loss
        val window = getCertificateValidityWindow(0, validDays)

        val builder = JcaX509v3CertificateBuilder(
                issuer, serial, window.first, window.second, subject, pubKey)

        builder.addExtension(Extension.subjectKeyIdentifier, false,
                createSubjectKeyIdentifier(pubKey))
        // TODO to remove once we allow for longer certificate chains
        builder.addExtension(Extension.basicConstraints, true,
                BasicConstraints(2))

        val usage = KeyUsage(KeyUsage.keyCertSign or KeyUsage.digitalSignature or KeyUsage.keyEncipherment or KeyUsage.dataEncipherment or KeyUsage.cRLSign)
        builder.addExtension(Extension.keyUsage, false, usage)

        val purposes = ASN1EncodableVector()
        purposes.add(KeyPurposeId.id_kp_serverAuth)
        purposes.add(KeyPurposeId.id_kp_clientAuth)
        purposes.add(KeyPurposeId.anyExtendedKeyUsage)
        builder.addExtension(Extension.extendedKeyUsage, false, DERSequence(purposes))

        val cert = signCertificate(builder, keyPair.private, provider)

        cert.checkValidity(Date())
        cert.verify(pubKey)

        return CertificateAndKeyPair(cert.toX509CertHolder(), KeyPair(pubKey, keyPair.private))
    }

    /**
     * This is a helper function, which purpose is to workaround a bug in the bouncycastle library
     * that is associated with the incorrect encoded byte production when the EC algorithm is used with the passed keys.
     * @param publicKey public key
     * @param privateKey private key
     * @return cleaned [KeyPair] instance
     */
    fun getCleanEcdsaKeyPair(publicKey: PublicKey, privateKey: PrivateKey): KeyPair {
        val rawPublicKeyBytes = publicKey.encoded
        val kf = KeyFactory.getInstance("EC")
        val cleanPublicKey = kf.generatePublic(X509EncodedKeySpec(rawPublicKeyBytes))
        return KeyPair(cleanPublicKey, privateKey)
    }

    /**
     * Retrieves a certificate and keys from the given key store. Also, the keys retrieved are cleaned in a sense of the
     * [getCleanEcdsaKeyPair] method.
     * @param certificateKeyName certificate and key name (alias) to be used when querying the key store.
     * @param privateKeyPassword password for the private key.
     * @param keyStore key store that holds the certificate with its keys.
     * @return instance of [CertificateAndKeyPair] holding the retrieved certificate with its keys.
     */
    fun retrieveCertificateAndKeys(certificateKeyName: String, privateKeyPassword: String, keyStore: KeyStore): CertificateAndKeyPair {
        val privateKey = keyStore.getKey(certificateKeyName, privateKeyPassword.toCharArray()) as PrivateKey
        val publicKey = keyStore.getCertificate(certificateKeyName).publicKey
        val certificate = keyStore.getX509Certificate(certificateKeyName)
        return CertificateAndKeyPair(certificate, getCleanEcdsaKeyPair(publicKey, privateKey))
    }

    /**
     * Create a de novo root intermediate X509 v3 CA cert and KeyPair.
     * @param commonName The Common (CN) field of the cert Subject will be populated with the domain string.
     * @param certificateAuthority The Public certificate and KeyPair of the root CA certificate above this used to sign it.
     * @param keyPair public and private keys to be associated with the generated certificate
     * @param validDays number of days which this certificate is valid for
     * @param provider provider to be used during the certificate signing process
     * @return an instance of [CertificateAndKeyPair] class is returned containing the new intermediate CA Cert and its KeyPair for signing downstream certificates.
     * Note the generated certificate tree is capped at max depth of 1 below this to be in line with commercially available certificates
     */
    fun createIntermediateCert(commonName: String,
                               certificateAuthority: CertificateAndKeyPair,
                               keyPair: KeyPair, validDays: Int, provider: Provider): CertificateAndKeyPair {

        val issuer = X509CertificateHolder(certificateAuthority.certificate.encoded).subject
        val serial = BigInteger.valueOf(random63BitValue(provider))
        val subject = getDevX509Name(commonName)
        val pubKey = keyPair.public

        // Ten year certificate validity
        // TODO how do we manage certificate expiry, revocation and loss
        val window = getCertificateValidityWindow(0, validDays, certificateAuthority.certificate.notBefore, certificateAuthority.certificate.notAfter)

        val builder = JcaX509v3CertificateBuilder(
                issuer, serial, window.first, window.second, subject, pubKey)

        builder.addExtension(Extension.subjectKeyIdentifier, false,
                createSubjectKeyIdentifier(pubKey))
        // TODO to remove onece we allow for longer certificate chains
        builder.addExtension(Extension.basicConstraints, true,
                BasicConstraints(1))

        val usage = KeyUsage(KeyUsage.keyCertSign or KeyUsage.digitalSignature or KeyUsage.keyEncipherment or KeyUsage.dataEncipherment or KeyUsage.cRLSign)
        builder.addExtension(Extension.keyUsage, false, usage)

        val purposes = ASN1EncodableVector()
        purposes.add(KeyPurposeId.id_kp_serverAuth)
        purposes.add(KeyPurposeId.id_kp_clientAuth)
        purposes.add(KeyPurposeId.anyExtendedKeyUsage)
        builder.addExtension(Extension.extendedKeyUsage, false,
                DERSequence(purposes))

        val cert = signCertificate(builder, certificateAuthority.keyPair.private, provider)

        cert.checkValidity(Date())
        cert.verify(certificateAuthority.keyPair.public)

        return CertificateAndKeyPair(cert.toX509CertHolder(), KeyPair(pubKey, keyPair.private))
    }

    /**
     * Creates and signs a X509 v3 client certificate.
     * @param caCertAndKey signing certificate authority certificate and its keys
     * @param request certficate signing request
     * @param validDays number of days which this certificate is valid for
     * @param provider provider to be used during the certificate signing process
     * @return an instance of [CertificateAndKeyPair] class is returned containing the signed client certificate.
     */
    fun createClientCertificate(caCertAndKey: CertificateAndKeyPair,
                                request: PKCS10CertificationRequest,
                                validDays: Int,
                                provider: Provider): Certificate {
        val jcaRequest = JcaPKCS10CertificationRequest(request)
        // This can be adjusted more to our future needs.
        val nameConstraints = NameConstraints(arrayOf(GeneralSubtree(GeneralName(GeneralName.directoryName, CordaX500Name.build(jcaRequest.subject).copy(commonName = null).x500Name))), arrayOf())
        val issuerCertificate = caCertAndKey.certificate
        val issuerKeyPair = caCertAndKey.keyPair
        val certificateType = CertificateType.CLIENT_CA
        val validityWindow = getCertificateValidityWindow(0, validDays, issuerCertificate.notBefore, issuerCertificate.notAfter)
        val serial = BigInteger.valueOf(random63BitValue(provider))
        val subject = CordaX500Name.build(jcaRequest.subject).copy(commonName = X509Utilities.CORDA_CLIENT_CA_CN).x500Name
        val subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(jcaRequest.publicKey.encoded))
        val keyPurposes = DERSequence(ASN1EncodableVector().apply { certificateType.purposes.forEach { add(it) } })
        val builder = JcaX509v3CertificateBuilder(issuerCertificate.subject, serial, validityWindow.first, validityWindow.second, subject, jcaRequest.publicKey)
                .addExtension(Extension.subjectKeyIdentifier, false, BcX509ExtensionUtils().createSubjectKeyIdentifier(subjectPublicKeyInfo))
                .addExtension(Extension.basicConstraints, certificateType.isCA, BasicConstraints(certificateType.isCA))
                .addExtension(Extension.keyUsage, false, certificateType.keyUsage)
                .addExtension(Extension.extendedKeyUsage, false, keyPurposes)
                .addExtension(Extension.nameConstraints, true, nameConstraints)
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
     * A utility method for transforming number of certificates into the [CertPath] instance.
     * The certificates passed should be ordered starting with the leaf certificate and ending with the root one.
     * @param certificates ordered certficates
     */
    fun buildCertPath(vararg certificates: Certificate) = CertificateFactory.getInstance("X509").generateCertPath(certificates.asList())

    /**
     * Creates and initializes a key store from the given crypto server provider.
     * It uses the provided key store password to enable key store access.
     * @param provider crypto server provider to be used for the key store creation
     * @param keyStorePassword key store password to be used for key store access authentication
     * @return created key store instance
     */
    fun getAndInitializeKeyStore(provider: CryptoServerProvider, keyStorePassword: String?): KeyStore {
        val keyStore = KeyStore.getInstance("CryptoServer", provider)
        keyStore.load(null, keyStorePassword?.toCharArray())
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

    /**
     * Use bouncy castle utilities to sign completed X509 certificate with CA cert private key
     */
    private fun signCertificate(certificateBuilder: X509v3CertificateBuilder,
                                signedWithPrivateKey: PrivateKey,
                                provider: Provider,
                                signatureAlgorithm: String = SIGNATURE_ALGORITHM): X509Certificate {
        val signer = JcaContentSignerBuilder(signatureAlgorithm).setProvider(provider).build(signedWithPrivateKey)
        return JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(certificateBuilder.build(signer))
    }

    /**
     * Return a bogus X509 for dev purposes. Use [getX509Name] for something more real.
     */
    private fun getDevX509Name(commonName: String): X500Name {
        val nameBuilder = X500NameBuilder(BCStyle.INSTANCE)
        nameBuilder.addRDN(BCStyle.CN, commonName)
        nameBuilder.addRDN(BCStyle.O, "R3")
        nameBuilder.addRDN(BCStyle.OU, "corda")
        nameBuilder.addRDN(BCStyle.L, "London")
        nameBuilder.addRDN(BCStyle.C, "UK")
        return nameBuilder.build()
    }

    private fun getX509Name(myLegalName: String, nearestCity: String, email: String): X500Name {
        return X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.CN, myLegalName)
                .addRDN(BCStyle.L, nearestCity)
                .addRDN(BCStyle.E, email).build()
    }
}
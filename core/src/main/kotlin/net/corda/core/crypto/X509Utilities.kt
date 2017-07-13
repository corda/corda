package net.corda.core.crypto

import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.NameConstraints
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.util.io.pem.PemReader
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStream
import java.nio.file.Path
import java.security.KeyPair
import java.security.PublicKey
import java.security.cert.*
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

object X509Utilities {
    val DEFAULT_IDENTITY_SIGNATURE_SCHEME = Crypto.EDDSA_ED25519_SHA512
    val DEFAULT_TLS_SIGNATURE_SCHEME = Crypto.ECDSA_SECP256R1_SHA256

    // Aliases for private keys and certificates.
    val CORDA_ROOT_CA = "cordarootca"
    val CORDA_INTERMEDIATE_CA = "cordaintermediateca"
    val CORDA_CLIENT_TLS = "cordaclienttls"
    val CORDA_CLIENT_CA = "cordaclientca"

    private val DEFAULT_VALIDITY_WINDOW = Pair(Duration.ofMillis(0), Duration.ofDays(365 * 10))
    /**
     * Helper function to return the latest out of an instant and an optional date.
     */
    private fun max(first: Instant, second: Date?): Date {
        return if (second != null && second.time > first.toEpochMilli())
            second
        else
            Date(first.toEpochMilli())
    }

    /**
     * Helper function to return the earliest out of an instant and an optional date.
     */
    private fun min(first: Instant, second: Date?): Date {
        return if (second != null && second.time < first.toEpochMilli())
            second
        else
            Date(first.toEpochMilli())
    }

    /**
     * Helper method to get a notBefore and notAfter pair from current day bounded by parent certificate validity range.
     * @param before duration to roll back returned start date relative to current date.
     * @param after duration to roll forward returned end date relative to current date.
     * @param parent if provided certificate whose validity should bound the date interval returned.
     */
    fun getCertificateValidityWindow(before: Duration, after: Duration, parent: X509CertificateHolder? = null): Pair<Date, Date> {
        val startOfDayUTC = Instant.now().truncatedTo(ChronoUnit.DAYS)
        val notBefore = max(startOfDayUTC - before, parent?.notBefore)
        val notAfter = min(startOfDayUTC + after, parent?.notAfter)
        return Pair(notBefore, notAfter)
    }

    /**
     * Return a bogus X509 for dev purposes. Use [getX509Name] for something more real.
     */
    @Deprecated("Full legal names should be specified in all configurations")
    fun getDevX509Name(commonName: String): X500Name {
        val nameBuilder = X500NameBuilder(BCStyle.INSTANCE)
        nameBuilder.addRDN(BCStyle.CN, commonName)
        nameBuilder.addRDN(BCStyle.O, "R3")
        nameBuilder.addRDN(BCStyle.OU, "corda")
        nameBuilder.addRDN(BCStyle.L, "London")
        nameBuilder.addRDN(BCStyle.C, "GB")
        return nameBuilder.build()
    }

    /**
     * Generate a distinguished name from the provided values.
     *
     * @see [CoreTestUtils.getTestX509Name] for generating distinguished names for test cases.
     */
    @JvmOverloads
    @JvmStatic
    fun getX509Name(myLegalName: String, nearestCity: String, email: String, country: String? = null): X500Name {
        return X500NameBuilder(BCStyle.INSTANCE).let { builder ->
            builder.addRDN(BCStyle.CN, myLegalName)
            builder.addRDN(BCStyle.L, nearestCity)
            country?.let { builder.addRDN(BCStyle.C, it) }
            builder.addRDN(BCStyle.E, email)
            builder.build()
        }
    }

    /*
     * Create a de novo root self-signed X509 v3 CA cert.
     */
    @JvmStatic
    fun createSelfSignedCACertificate(subject: X500Name, keyPair: KeyPair, validityWindow: Pair<Duration, Duration> = DEFAULT_VALIDITY_WINDOW): X509CertificateHolder {
        val window = getCertificateValidityWindow(validityWindow.first, validityWindow.second)
        return Crypto.createCertificate(CertificateType.ROOT_CA, subject, keyPair, subject, keyPair.public, window)
    }

    /**
     * Create a X509 v3 cert.
     * @param issuerCertificate The Public certificate of the root CA above this used to sign it.
     * @param issuerKeyPair The KeyPair of the root CA above this used to sign it.
     * @param subject subject of the generated certificate.
     * @param subjectPublicKey subject 's public key.
     * @param validityWindow The certificate's validity window. Default to [DEFAULT_VALIDITY_WINDOW] if not provided.
     * @return A data class is returned containing the new intermediate CA Cert and its KeyPair for signing downstream certificates.
     * Note the generated certificate tree is capped at max depth of 1 below this to be in line with commercially available certificates.
     */
    @JvmStatic
    fun createCertificate(certificateType: CertificateType,
                          issuerCertificate: X509CertificateHolder, issuerKeyPair: KeyPair,
                          subject: X500Name, subjectPublicKey: PublicKey,
                          validityWindow: Pair<Duration, Duration> = DEFAULT_VALIDITY_WINDOW,
                          nameConstraints: NameConstraints? = null): X509CertificateHolder {
        val window = getCertificateValidityWindow(validityWindow.first, validityWindow.second, issuerCertificate)
        return Crypto.createCertificate(certificateType, issuerCertificate.subject, issuerKeyPair, subject, subjectPublicKey, window, nameConstraints)
    }

    fun validateCertificateChain(trustedRoot: X509CertificateHolder, vararg certificates: Certificate) {
        require(certificates.isNotEmpty()) { "Certificate path must contain at least one certificate" }
        val certFactory = CertificateFactory.getInstance("X509")
        val params = PKIXParameters(setOf(TrustAnchor(trustedRoot.cert, null)))
        params.isRevocationEnabled = false
        val certPath = certFactory.generateCertPath(certificates.toList())
        val pathValidator = CertPathValidator.getInstance("PKIX")
        pathValidator.validate(certPath, params)
    }

    /**
     * Helper method to store a .pem/.cer format file copy of a certificate if required for import into a PC/Mac, or for inspection.
     * @param x509Certificate certificate to save.
     * @param filename Target filename.
     */
    @JvmStatic
    fun saveCertificateAsPEMFile(x509Certificate: X509CertificateHolder, filename: Path) {
        FileWriter(filename.toFile()).use {
            JcaPEMWriter(it).use {
                it.writeObject(x509Certificate)
            }
        }
    }

    /**
     * Helper method to load back a .pem/.cer format file copy of a certificate.
     * @param filename Source filename.
     * @return The X509Certificate that was encoded in the file.
     */
    @JvmStatic
    fun loadCertificateFromPEMFile(filename: Path): X509CertificateHolder {
        val reader = PemReader(FileReader(filename.toFile()))
        val pemObject = reader.readPemObject()
        val cert = X509CertificateHolder(pemObject.content)
        return cert.apply {
            isValidOn(Date())
        }
    }

    fun createCertificateSigningRequest(subject: X500Name, keyPair: KeyPair, signatureScheme: SignatureScheme = DEFAULT_TLS_SIGNATURE_SCHEME) = Crypto.createCertificateSigningRequest(subject, keyPair, signatureScheme)
}

/**
 * Rebuild the distinguished name, adding a postfix to the common name. If no common name is present.
 * @throws IllegalArgumentException if the distinguished name does not contain a common name element.
 */
fun X500Name.appendToCommonName(commonName: String): X500Name = mutateCommonName { attr -> attr.toString() + commonName }

/**
 * Rebuild the distinguished name, replacing the common name with the given value. If no common name is present, this
 * adds one.
 * @throws IllegalArgumentException if the distinguished name does not contain a common name element.
 */
fun X500Name.replaceCommonName(commonName: String): X500Name = mutateCommonName { _ -> commonName }

/**
 * Rebuild the distinguished name, replacing the common name with a value generated from the provided function.
 *
 * @param mutator a function to generate the new value from the previous one.
 * @throws IllegalArgumentException if the distinguished name does not contain a common name element.
 */
private fun X500Name.mutateCommonName(mutator: (ASN1Encodable) -> String): X500Name {
    val builder = X500NameBuilder(BCStyle.INSTANCE)
    var matched = false
    this.rdNs.forEach { rdn ->
        rdn.typesAndValues.forEach { typeAndValue ->
            when (typeAndValue.type) {
                BCStyle.CN -> {
                    matched = true
                    builder.addRDN(typeAndValue.type, mutator(typeAndValue.value))
                }
                else -> {
                    builder.addRDN(typeAndValue)
                }
            }
        }
    }
    require(matched) { "Input X.500 name must include a common name (CN) attribute: ${this}" }
    return builder.build()
}

val X500Name.commonName: String get() = getRDNs(BCStyle.CN).first().first.value.toString()
val X500Name.orgName: String? get() = getRDNs(BCStyle.O).firstOrNull()?.first?.value?.toString()
val X500Name.location: String get() = getRDNs(BCStyle.L).first().first.value.toString()
val X500Name.locationOrNull: String? get() = try { location } catch (e: Exception) { null }
val X509Certificate.subject: X500Name get() = X509CertificateHolder(encoded).subject
val X509CertificateHolder.cert: X509Certificate get() = JcaX509CertificateConverter().getCertificate(this)

class CertificateStream(val input: InputStream) {
    private val certificateFactory = CertificateFactory.getInstance("X.509")

    fun nextCertificate(): X509Certificate = certificateFactory.generateCertificate(input) as X509Certificate
}

data class CertificateAndKeyPair(val certificate: X509CertificateHolder, val keyPair: KeyPair)

enum class CertificateType(val keyUsage: KeyUsage, vararg val purposes: KeyPurposeId, val isCA: Boolean) {
    ROOT_CA(KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyCertSign or KeyUsage.cRLSign), KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth, KeyPurposeId.anyExtendedKeyUsage, isCA = true),
    INTERMEDIATE_CA(KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyCertSign or KeyUsage.cRLSign), KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth, KeyPurposeId.anyExtendedKeyUsage, isCA = true),
    CLIENT_CA(KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyCertSign or KeyUsage.cRLSign), KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth, KeyPurposeId.anyExtendedKeyUsage, isCA = true),
    TLS(KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment or KeyUsage.keyAgreement), KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth, KeyPurposeId.anyExtendedKeyUsage, isCA = false),
    // TODO: Identity certs should have only limited depth (i.e. 1) CA signing capability, with tight name constraints
    IDENTITY(KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyCertSign), KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth, KeyPurposeId.anyExtendedKeyUsage, isCA = true)
}

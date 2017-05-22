package net.corda.core.crypto

import net.corda.core.crypto.Crypto.generateKeyPair
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.util.IPAddress
import org.bouncycastle.util.io.pem.PemReader
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStream
import java.net.InetAddress
import java.nio.file.Path
import java.security.InvalidAlgorithmParameterException
import java.security.KeyPair
import java.security.KeyStore
import java.security.PublicKey
import java.security.cert.*
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

object X509Utilities {
    val DEFAULT_TLS_SIGNATURE_SCHEME = Crypto.ECDSA_SECP256R1_SHA256

    // Aliases for private keys and certificates.
    val CORDA_ROOT_CA_PRIVATE_KEY = "cordarootcaprivatekey"
    val CORDA_ROOT_CA = "cordarootca"
    val CORDA_INTERMEDIATE_CA_PRIVATE_KEY = "cordaintermediatecaprivatekey"
    val CORDA_INTERMEDIATE_CA = "cordaintermediateca"
    val CORDA_CLIENT_CA_PRIVATE_KEY = "cordaclientcaprivatekey"
    val CORDA_CLIENT_CA = "cordaclientca"

    private val CA_KEY_USAGE = KeyUsage(KeyUsage.keyCertSign or KeyUsage.digitalSignature or KeyUsage.keyEncipherment or KeyUsage.dataEncipherment or KeyUsage.cRLSign)
    private val CLIENT_KEY_USAGE = KeyUsage(KeyUsage.digitalSignature)
    private val CA_KEY_PURPOSES = listOf(KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth, KeyPurposeId.anyExtendedKeyUsage)
    private val CLIENT_KEY_PURPOSES = listOf(KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth)

    private val DEFAULT_VALIDITY_WINDOW = Pair(Duration.ofMillis(0), Duration.ofDays(365 * 10))

    /**
     * Helper method to get a notBefore and notAfter pair from current day bounded by parent certificate validity range.
     * @param daysBefore number of days to roll back returned start date relative to current date.
     * @param daysAfter number of days to roll forward returned end date relative to current date.
     * @param parent certificate whose validity should bound the date interval returned.
     */
    private fun getCertificateValidityWindow(before: Duration, after: Duration, parent: X509Certificate): Pair<Instant, Instant> {
        return getCertificateValidityWindow(before, after, parent.notBefore. toInstant(), parent.notAfter.toInstant())
    }

    /**
     * Helper method to get a notBefore and notAfter pair from current day bounded by parent certificate validity range.
     * @param daysBefore number of days to roll back returned start date relative to current date.
     * @param daysAfter number of days to roll forward returned end date relative to current date.
     * @param parentNotBefore if provided is used to lower bound the date interval returned.
     * @param parentNotAfter if provided is used to upper bound the date interval returned.
     */
    private fun getCertificateValidityWindow(before: Duration,
                                             after: Duration,
                                             parentNotBefore: Instant? = null,
                                             parentNotAfter: Instant? = null): Pair<Instant, Instant> {
        val startOfDayUTC = Instant.now().truncatedTo(ChronoUnit.DAYS)
        val notBefore = (startOfDayUTC - before).let { notBefore ->
            if (parentNotBefore != null && parentNotBefore > notBefore) parentNotBefore else notBefore
        }
        val notAfter = (startOfDayUTC + after).let { notAfter ->
            if (parentNotAfter != null && parentNotAfter < notAfter) parentNotAfter else notAfter
        }
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
        nameBuilder.addRDN(BCStyle.C, "UK")
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
     * Create a de novo root self-signed X509 v3 CA cert and [KeyPair].
     * @param subject the cert Subject will be populated with the domain string.
     * @param signatureScheme The signature scheme which will be used to generate keys and certificate. Default to [DEFAULT_TLS_SIGNATURE_SCHEME] if not provided.
     * @param validityWindow The certificate's validity window. Default to [DEFAULT_VALIDITY_WINDOW] if not provided.
     * @return A data class is returned containing the new root CA Cert and its [KeyPair] for signing downstream certificates.
     * Note the generated certificate tree is capped at max depth of 2 to be in line with commercially available certificates.
     */
    @JvmStatic
    fun createSelfSignedCACert(subject: X500Name,
                               keyPair: KeyPair,
                               validityWindow: Pair<Duration, Duration> = DEFAULT_VALIDITY_WINDOW): CertificateAndKeyPair {
        val window = getCertificateValidityWindow(validityWindow.first, validityWindow.second)
        val cert = Crypto.createCertificate(subject, keyPair, subject, keyPair.public, CA_KEY_USAGE, CA_KEY_PURPOSES, window, pathLength = 2)
        return CertificateAndKeyPair(cert, keyPair)
    }

    @JvmStatic
    fun createSelfSignedCACert(subject: X500Name, signatureScheme: SignatureScheme = DEFAULT_TLS_SIGNATURE_SCHEME,
                               validityWindow: Pair<Duration, Duration> = DEFAULT_VALIDITY_WINDOW): CertificateAndKeyPair
            = createSelfSignedCACert(subject, generateKeyPair(signatureScheme), validityWindow)

    /**
     * Create a de novo root intermediate X509 v3 CA cert and KeyPair.
     * @param subject subject of the generated certificate.
     * @param ca The Public certificate and KeyPair of the root CA certificate above this used to sign it.
     * @param signatureScheme The signature scheme which will be used to generate keys and certificate. Default to [DEFAULT_TLS_SIGNATURE_SCHEME] if not provided.
     * @param validityWindow The certificate's validity window. Default to [DEFAULT_VALIDITY_WINDOW] if not provided.
     * @return A data class is returned containing the new intermediate CA Cert and its KeyPair for signing downstream certificates.
     * Note the generated certificate tree is capped at max depth of 1 below this to be in line with commercially available certificates.
     */
    @JvmStatic
    fun createIntermediateCert(subject: X500Name,
                               ca: CertificateAndKeyPair,
                               signatureScheme: SignatureScheme = DEFAULT_TLS_SIGNATURE_SCHEME,
                               validityWindow: Pair<Duration, Duration> = DEFAULT_VALIDITY_WINDOW): CertificateAndKeyPair {
        val keyPair = generateKeyPair(signatureScheme)
        val issuer = X509CertificateHolder(ca.certificate.encoded).subject
        val window = getCertificateValidityWindow(validityWindow.first, validityWindow.second, ca.certificate)
        val cert = Crypto.createCertificate(issuer, ca.keyPair, subject, keyPair.public, CA_KEY_USAGE, CA_KEY_PURPOSES, window, pathLength = 1)
        return CertificateAndKeyPair(cert, keyPair)
    }

    /**
     * Create an X509v3 certificate suitable for use in TLS roles.
     * @param subject The contents to put in the subject field of the certificate.
     * @param publicKey The PublicKey to be wrapped in the certificate.
     * @param ca The Public certificate and KeyPair of the parent CA that will sign this certificate.
     * @param subjectAlternativeNameDomains A set of alternate DNS names to be supported by the certificate during validation of the TLS handshakes.
     * @param subjectAlternativeNameIps A set of alternate IP addresses to be supported by the certificate during validation of the TLS handshakes.
     * @param validityWindow The certificate's validity window. Default to [DEFAULT_VALIDITY_WINDOW] if not provided.
     * @return The generated X509Certificate suitable for use as a Server/Client certificate in TLS.
     * This certificate is not marked as a CA cert to be similar in nature to commercial certificates.
     */
    @JvmStatic
    fun createTlsServerCert(subject: X500Name, publicKey: PublicKey,
                            ca: CertificateAndKeyPair,
                            subjectAlternativeNameDomains: List<String>,
                            subjectAlternativeNameIps: List<String>,
                            validityWindow: Pair<Duration, Duration> = DEFAULT_VALIDITY_WINDOW): X509Certificate {

        val issuer = X509CertificateHolder(ca.certificate.encoded).subject
        val window = getCertificateValidityWindow(validityWindow.first, validityWindow.second, ca.certificate)
        val dnsNames = subjectAlternativeNameDomains.map { GeneralName(GeneralName.dNSName, it) }
        val ipAddresses = subjectAlternativeNameIps.filter {
            IPAddress.isValidIPv6WithNetmask(it) || IPAddress.isValidIPv6(it) || IPAddress.isValidIPv4WithNetmask(it) || IPAddress.isValidIPv4(it)
        }.map { GeneralName(GeneralName.iPAddress, it) }
        return Crypto.createCertificate(issuer, ca.keyPair, subject, publicKey, CLIENT_KEY_USAGE, CLIENT_KEY_PURPOSES, window, subjectAlternativeName = dnsNames + ipAddresses)
    }

    /**
     * Build a certificate path from a trusted root certificate to a target certificate. This will always return a path
     * directly from the root to the target, with no intermediate certificates (presuming that path is valid).
     *
     * @param rootCertAndKey trusted root certificate that will be the start of the path.
     * @param targetCertAndKey certificate the path ends at.
     * @param revocationEnabled whether revocation of certificates in the path should be checked.
     */
    fun createCertificatePath(rootCertAndKey: CertificateAndKeyPair,
                              targetCertAndKey: CertificateAndKeyPair,
                              revocationEnabled: Boolean): CertPathBuilderResult {
        val intermediateCertificates = setOf(targetCertAndKey.certificate)
        val certStore = CertStore.getInstance("Collection", CollectionCertStoreParameters(intermediateCertificates))
        val certPathFactory = CertPathBuilder.getInstance("PKIX")
        val trustAnchor = TrustAnchor(rootCertAndKey.certificate, null)
        val certPathParameters = try {
            PKIXBuilderParameters(setOf(trustAnchor), X509CertSelector().apply {
                certificate = targetCertAndKey.certificate
            })
        } catch (ex: InvalidAlgorithmParameterException) {
            throw RuntimeException(ex)
        }.apply {
            addCertStore(certStore)
            isRevocationEnabled = revocationEnabled
        }
        return certPathFactory.build(certPathParameters)
    }

    /**
     * Helper method to store a .pem/.cer format file copy of a certificate if required for import into a PC/Mac, or for inspection.
     * @param x509Certificate certificate to save.
     * @param filename Target filename.
     */
    @JvmStatic
    fun saveCertificateAsPEMFile(x509Certificate: X509Certificate, filename: Path) {
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
    fun loadCertificateFromPEMFile(filename: Path): X509Certificate {
        val reader = PemReader(FileReader(filename.toFile()))
        val pemObject = reader.readPemObject()
        return CertificateStream(pemObject.content.inputStream()).nextCertificate().apply {
            checkValidity()
        }
    }

    /**
     * An all in wrapper to manufacture a server certificate and keys all stored in a KeyStore suitable for running TLS on the local machine.
     * @param keyStoreFilePath KeyStore path to save output to.
     * @param storePassword access password for KeyStore.
     * @param keyPassword PrivateKey access password for the generated keys.
     * It is recommended that this is the same as the storePassword as most TLS libraries assume they are the same.
     * @param caKeyStore KeyStore containing CA keys generated by createCAKeyStoreAndTrustStore.
     * @param caKeyPassword password to unlock private keys in the CA KeyStore.
     * @return The KeyStore created containing a private key, certificate chain and root CA public cert for use in TLS applications.
     */
    fun createKeystoreForSSL(keyStoreFilePath: Path,
                             storePassword: String,
                             keyPassword: String,
                             caKeyStore: KeyStore,
                             caKeyPassword: String,
                             commonName: X500Name,
                             signatureScheme: SignatureScheme = DEFAULT_TLS_SIGNATURE_SCHEME): KeyStore {

        val rootCA = caKeyStore.getCertificateAndKeyPair(CORDA_ROOT_CA_PRIVATE_KEY, caKeyPassword)
        val intermediateCA = caKeyStore.getCertificateAndKeyPair(CORDA_INTERMEDIATE_CA_PRIVATE_KEY, caKeyPassword)

        val serverKey = generateKeyPair(signatureScheme)
        val host = InetAddress.getLocalHost()
        val serverCert = createTlsServerCert(commonName, serverKey.public, intermediateCA, listOf(host.hostName), listOf(host.hostAddress))

        val keyPass = keyPassword.toCharArray()
        val keyStore = KeyStoreUtilities.loadOrCreateKeyStore(keyStoreFilePath, storePassword)

        keyStore.addOrReplaceKey(
                CORDA_CLIENT_CA_PRIVATE_KEY,
                serverKey.private,
                keyPass,
                arrayOf(serverCert, intermediateCA.certificate, rootCA.certificate))
        keyStore.addOrReplaceCertificate(CORDA_CLIENT_CA, serverCert)
        keyStore.save(keyStoreFilePath, storePassword)
        return keyStore
    }

    fun createCertificateSigningRequest(subject: X500Name, keyPair: KeyPair, signatureScheme: SignatureScheme = DEFAULT_TLS_SIGNATURE_SCHEME) = Crypto.createCertificateSigningRequest(subject, keyPair, signatureScheme)
}

/**
 * Rebuild the distinguished name, adding a postfix to the common name. If no common name is present, this throws an
 * exception.
 */
@Throws(IllegalArgumentException::class)
fun X500Name.appendToCommonName(commonName: String): X500Name = mutateCommonName { attr -> attr.toString() + commonName }

/**
 * Rebuild the distinguished name, replacing the common name with the given value. If no common name is present, this
 * adds one.
 */
@Throws(IllegalArgumentException::class)
fun X500Name.replaceCommonName(commonName: String): X500Name = mutateCommonName { _ -> commonName }

/**
 * Rebuild the distinguished name, replacing the common name with a value generated from the provided function.
 *
 * @param mutator a function to generate the new value from the previous one.
 */
@Throws(IllegalArgumentException::class)
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

class CertificateStream(val input: InputStream) {
    private val certificateFactory = CertificateFactory.getInstance("X.509")

    fun nextCertificate(): X509Certificate = certificateFactory.generateCertificate(input) as X509Certificate
}

data class CertificateAndKeyPair(val certificate: X509Certificate, val keyPair: KeyPair)

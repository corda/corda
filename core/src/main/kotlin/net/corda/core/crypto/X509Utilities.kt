package net.corda.core.crypto

import net.corda.core.crypto.Crypto.generateKeyPair
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.util.IPAddress
import org.bouncycastle.util.io.pem.PemReader
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStream
import java.net.InetAddress
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyStore
import java.security.PublicKey
import java.security.cert.*
import java.security.cert.Certificate
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

object X509Utilities {
    val DEFAULT_TLS_SIGNATURE_SCHEME = Crypto.ECDSA_SECP256R1_SHA256

    // Aliases for private keys and certificates.
    val CORDA_ROOT_CA = "cordarootca"
    val CORDA_INTERMEDIATE_CA = "cordaintermediateca"
    val CORDA_CLIENT_TLS = "cordaclienttls"
    val CORDA_CLIENT_CA = "cordaclientca"

    private val CA_KEY_USAGE = KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyCertSign or KeyUsage.cRLSign)
    private val IDENTITY_KEY_USAGE = KeyUsage(KeyUsage.digitalSignature)
    private val TLS_KEY_USAGE = KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment or KeyUsage.keyAgreement)

    private val CA_KEY_PURPOSES = listOf(KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth, KeyPurposeId.anyExtendedKeyUsage)
    private val CLIENT_KEY_PURPOSES = listOf(KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth)

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
    private fun getCertificateValidityWindow(before: Duration, after: Duration, parent: X509Certificate? = null): Pair<Date, Date> {
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
    fun createSelfSignedCACert(subject: X500Name, keyPair: KeyPair, validityWindow: Pair<Duration, Duration> = DEFAULT_VALIDITY_WINDOW): CertificateAndKeyPair {
        val window = getCertificateValidityWindow(validityWindow.first, validityWindow.second)
        val cert = Crypto.createCertificate(subject, keyPair, subject, keyPair.public, CA_KEY_USAGE, CA_KEY_PURPOSES, window)
        return CertificateAndKeyPair(cert, keyPair)
    }

    /**
     * Create a de novo root intermediate X509 v3 CA cert and KeyPair.
     * @param subject subject of the generated certificate.
     * @param publicKey subject 's public key.
     * @param ca The Public certificate and KeyPair of the root CA certificate above this used to sign it.
     * @param validityWindow The certificate's validity window. Default to [DEFAULT_VALIDITY_WINDOW] if not provided.
     * @return A data class is returned containing the new intermediate CA Cert and its KeyPair for signing downstream certificates.
     * Note the generated certificate tree is capped at max depth of 1 below this to be in line with commercially available certificates.
     */
    @JvmStatic
    fun createIntermediateCACert(subject: X500Name, publicKey: PublicKey, ca: CertificateAndKeyPair, validityWindow: Pair<Duration, Duration> = DEFAULT_VALIDITY_WINDOW, nameConstraints: NameConstraints? = null): X509Certificate {
        val issuer = ca.certificate.subject
        val window = getCertificateValidityWindow(validityWindow.first, validityWindow.second, ca.certificate)
        return Crypto.createCertificate(issuer, ca.keyPair, subject, publicKey, CA_KEY_USAGE, CA_KEY_PURPOSES, window, nameConstraints = nameConstraints)
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
    fun createTLSCert(subject: X500Name, publicKey: PublicKey,
                      ca: CertificateAndKeyPair,
                      subjectAlternativeNameDomains: List<String>,
                      subjectAlternativeNameIps: List<String>,
                      validityWindow: Pair<Duration, Duration> = DEFAULT_VALIDITY_WINDOW): X509Certificate {

        val dnsNames = subjectAlternativeNameDomains.map { GeneralName(GeneralName.dNSName, it) }
        val ipAddresses = subjectAlternativeNameIps.filter {
            IPAddress.isValidIPv6WithNetmask(it) || IPAddress.isValidIPv6(it) || IPAddress.isValidIPv4WithNetmask(it) || IPAddress.isValidIPv4(it)
        }.map { GeneralName(GeneralName.iPAddress, it) }
        val issuer = X509CertificateHolder(ca.certificate.encoded).subject
        val window = getCertificateValidityWindow(validityWindow.first, validityWindow.second, ca.certificate)
        return Crypto.createCertificate(issuer, ca.keyPair, subject, publicKey, TLS_KEY_USAGE, CLIENT_KEY_PURPOSES, window, subjectAlternativeName = dnsNames + ipAddresses, isCA = false)
    }

    @JvmStatic
    fun createIdentityCert(subject: X500Name, publicKey: PublicKey,
                      ca: CertificateAndKeyPair,
                      subjectAlternativeNameDomains: List<String>,
                      subjectAlternativeNameIps: List<String>,
                      validityWindow: Pair<Duration, Duration> = DEFAULT_VALIDITY_WINDOW): X509Certificate {

        val dnsNames = subjectAlternativeNameDomains.map { GeneralName(GeneralName.dNSName, it) }
        val ipAddresses = subjectAlternativeNameIps.filter {
            IPAddress.isValidIPv6WithNetmask(it) || IPAddress.isValidIPv6(it) || IPAddress.isValidIPv4WithNetmask(it) || IPAddress.isValidIPv4(it)
        }.map { GeneralName(GeneralName.iPAddress, it) }
        val issuer = X509CertificateHolder(ca.certificate.encoded).subject
        val window = getCertificateValidityWindow(validityWindow.first, validityWindow.second, ca.certificate)
        return Crypto.createCertificate(issuer, ca.keyPair, subject, publicKey, IDENTITY_KEY_USAGE, CLIENT_KEY_PURPOSES, window, subjectAlternativeName = dnsNames + ipAddresses, isCA = false)
    }

    /**
     * Build a certificate path from a trusted root certificate to a target certificate. This will always return a path
     * directly from the target to the root.
     *
     * @param trustedRoot trusted root certificate that will be the start of the path.
     * @param certificates certificates in the path.
     * @param revocationEnabled whether revocation of certificates in the path should be checked.
     */
    fun createCertificatePath(trustedRoot: X509Certificate, vararg certificates: X509Certificate, revocationEnabled: Boolean): CertPath {
        val certFactory = CertificateFactory.getInstance("X509")
        val params = PKIXParameters(setOf(TrustAnchor(trustedRoot, null)))
        params.isRevocationEnabled = revocationEnabled
        return certFactory.generateCertPath(certificates.toList())
    }

    fun validateCertificateChain(trustedRoot: X509Certificate, vararg certificates: Certificate) {
        require(certificates.isNotEmpty()) { "Certificate path must contain at least one certificate" }
        val certFactory = CertificateFactory.getInstance("X509")
        val params = PKIXParameters(setOf(TrustAnchor(trustedRoot, null)))
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
     * @param sslKeyStorePath KeyStore path to save ssl key and cert to.
     * @param sslKeyStorePath KeyStore path to save client CA key and cert to.
     * @param storePassword access password for KeyStore.
     * @param keyPassword PrivateKey access password for the generated keys.
     * It is recommended that this is the same as the storePassword as most TLS libraries assume they are the same.
     * @param caKeyStore KeyStore containing CA keys generated by createCAKeyStoreAndTrustStore.
     * @param caKeyPassword password to unlock private keys in the CA KeyStore.
     * @return The KeyStore created containing a private key, certificate chain and root CA public cert for use in TLS applications.
     */
    fun createKeystoreForCordaNode(sslKeyStorePath: Path,
                                   clientCAKeystorePath: Path,
                                   storePassword: String,
                                   keyPassword: String,
                                   caKeyStore: KeyStore,
                                   caKeyPassword: String,
                                   legalName: X500Name,
                                   signatureScheme: SignatureScheme = DEFAULT_TLS_SIGNATURE_SCHEME) {

        val rootCACert = caKeyStore.getX509Certificate(CORDA_ROOT_CA)
        val intermediateCA = caKeyStore.getCertificateAndKeyPair(CORDA_INTERMEDIATE_CA, caKeyPassword)

        val clientKey = generateKeyPair(signatureScheme)
        val nameConstraints = NameConstraints(arrayOf(GeneralSubtree(GeneralName(GeneralName.directoryName, legalName))), arrayOf())
        val clientCACert = createIntermediateCACert(legalName, clientKey.public, intermediateCA, nameConstraints = nameConstraints)
        val clientCA = CertificateAndKeyPair(clientCACert, clientKey)

        val host = InetAddress.getLocalHost()
        val tlsKey = generateKeyPair(signatureScheme)
        val clientTLSCert = createTLSCert(legalName, tlsKey.public, clientCA, listOf(host.hostName, legalName.commonName), listOf(host.hostAddress))

        val keyPass = keyPassword.toCharArray()

        val clientCAKeystore = KeyStoreUtilities.loadOrCreateKeyStore(clientCAKeystorePath, storePassword)
        clientCAKeystore.addOrReplaceKey(
                CORDA_CLIENT_CA,
                clientKey.private,
                keyPass,
                arrayOf(clientCACert, intermediateCA.certificate, rootCACert))
        clientCAKeystore.save(clientCAKeystorePath, storePassword)

        val tlsKeystore = KeyStoreUtilities.loadOrCreateKeyStore(sslKeyStorePath, storePassword)
        tlsKeystore.addOrReplaceKey(
                CORDA_CLIENT_TLS,
                tlsKey.private,
                keyPass,
                arrayOf(clientTLSCert, clientCACert, intermediateCA.certificate, rootCACert))
        tlsKeystore.save(sslKeyStorePath, storePassword)
    }

    fun createCertificateSigningRequest(subject: X500Name, keyPair: KeyPair, signatureScheme: SignatureScheme = DEFAULT_TLS_SIGNATURE_SCHEME) = Crypto.createCertificateSigningRequest(subject, keyPair, signatureScheme)
}

/**
 * Rebuild the distinguished name, adding a postfix to the common name. If no common name is present, this throws an
 * exception.
 */
fun X500Name.appendToCommonName(commonName: String): X500Name = mutateCommonName { attr -> attr.toString() + commonName }

/**
 * Rebuild the distinguished name, replacing the common name with the given value. If no common name is present, this
 * adds one.
 */
fun X500Name.replaceCommonName(commonName: String): X500Name = mutateCommonName { _ -> commonName }

/**
 * Rebuild the distinguished name, replacing the common name with a value generated from the provided function.
 *
 * @param mutator a function to generate the new value from the previous one.
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
val X509Certificate.subject: X500Name get() = X509CertificateHolder(encoded).subject

class CertificateStream(val input: InputStream) {
    private val certificateFactory = CertificateFactory.getInstance("X.509")

    fun nextCertificate(): X509Certificate = certificateFactory.generateCertificate(input) as X509Certificate
}

data class CertificateAndKeyPair(val certificate: X509Certificate, val keyPair: KeyPair)

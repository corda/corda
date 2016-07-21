package com.r3corda.core.crypto

import com.r3corda.core.random63BitValue
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1EncodableVector
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
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.util.IPAddress
import org.bouncycastle.util.io.pem.PemReader
import java.io.*
import java.math.BigInteger
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

object X509Utilities {

    val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    val KEY_GENERATION_ALGORITHM = "ECDSA"
    val ECDSA_CURVE = "secp256k1" // TLS implementations only support standard SEC2 curves, although internally Corda uses newer EDDSA keys

    val KEYSTORE_TYPE = "JKS"
    val CA_CERT_ALIAS = "CA Cert"
    val CERT_PRIVATE_KEY_ALIAS = "Server Private Key"
    val ROOT_CA_CERT_PRIVATE_KEY_ALIAS = "Root CA Private Key"
    val INTERMEDIATE_CA_PRIVATE_KEY_ALIAS = "Intermediate CA Private Key"

    init {
        Security.addProvider(BouncyCastleProvider()) // register Bouncy Castle Crypto Provider required to sign certificates
    }

    /**
     * Helper method to get a notBefore and notAfter pair from current day bounded by parent certificate validity range
     * @param daysBefore number of days to roll back returned start date relative to current date
     * @param daysAfter number of days to roll forward returned end date relative to current date
     * @param parentNotBefore if provided is used to lower bound the date interval returned
     * @param parentNotAfter if provided is used to upper bound the date interval returned
     */
    private fun GetCertificateValidityWindow(daysBefore: Int, daysAfter: Int, parentNotBefore: Date? = null, parentNotAfter: Date? = null): Pair<Date, Date> {
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
     * Encode provided public key in correct format for inclusion in certificate issuer/subject fields
     */
    private fun createSubjectKeyIdentifier(key: Key): SubjectKeyIdentifier {
        val info = SubjectPublicKeyInfo.getInstance(key.encoded)
        return BcX509ExtensionUtils().createSubjectKeyIdentifier(info)
    }

    /**
     * Use bouncy castle utilities to sign completed X509 certificate with CA cert private key
     */
    private fun signCertificate(certificateBuilder: X509v3CertificateBuilder, signedWithPrivateKey: PrivateKey): X509Certificate {
        val signer = JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider(BouncyCastleProvider.PROVIDER_NAME).build(signedWithPrivateKey)
        return JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(certificateBuilder.build(signer))
    }

    /**
     * Helper method to create Subject field contents
     */
    fun GetX509Name(domain: String): X500Name {
        val nameBuilder = X500NameBuilder(BCStyle.INSTANCE)
        nameBuilder.addRDN(BCStyle.CN, domain)
        nameBuilder.addRDN(BCStyle.O, "R3")
        nameBuilder.addRDN(BCStyle.OU, "corda")
        nameBuilder.addRDN(BCStyle.L, "London")
        nameBuilder.addRDN(BCStyle.C, "UK")
        return nameBuilder.build()
    }

    /**
     * Helper method to either open an existing keystore for modification, or create a new blank keystore
     * @param keyStoreFilePath location of KeyStore file
     * @param storePassword password to open the store. This does not have to be the same password as any keys stored,
     * but for SSL purposes this is recommended.
     * @return returns the KeyStore opened/created
     */
    fun loadOrCreateKeyStore(keyStoreFilePath: Path, storePassword: String): KeyStore {
        val pass = storePassword.toCharArray()
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        if (Files.exists(keyStoreFilePath)) {
            val input = FileInputStream(keyStoreFilePath.toFile())
            input.use {
                keyStore.load(input, pass)
            }
        } else {
            keyStore.load(null, pass)
        }
        return keyStore
    }

    /**
     * Helper method to open an existing keystore for modification/read
     * @param keyStoreFilePath location of KeyStore file which must exist, or this will throw FileNotFoundException
     * @param storePassword password to open the store. This does not have to be the same password as any keys stored,
     * but for SSL purposes this is recommended.
     * @return returns the KeyStore opened
     */
    fun loadKeyStore(keyStoreFilePath: Path, storePassword: String): KeyStore {
        val pass = storePassword.toCharArray()
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        val input = FileInputStream(keyStoreFilePath.toFile())
        input.use {
            keyStore.load(input, pass)
        }
        return keyStore
    }

    /**
     * Helper method to open an existing keystore for modification/read
     * @param input stream containing a KeyStore e.g. loaded from a resource file
     * @param storePassword password to open the store. This does not have to be the same password as any keys stored,
     * but for SSL purposes this is recommended.
     * @return returns the KeyStore opened
     */
    fun loadKeyStore(input: InputStream, storePassword: String): KeyStore {
        val pass = storePassword.toCharArray()
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        input.use {
            keyStore.load(input, pass)
        }
        return keyStore
    }

    /**
     * Helper method save KeyStore to storage
     * @param keyStore the KeyStore to persist
     * @param keyStoreFilePath the file location to save to
     * @param storePassword password to access the store in future. This does not have to be the same password as any keys stored,
     * but for SSL purposes this is recommended.
     */
    fun saveKeyStore(keyStore: KeyStore, keyStoreFilePath: Path, storePassword: String) {
        val pass = storePassword.toCharArray()
        val output = FileOutputStream(keyStoreFilePath.toFile())
        output.use {
            keyStore.store(output, pass)
        }
    }

    /**
     * Helper extension method to add, or overwrite any key data in store
     * @param alias name to record the private key and certificate chain under
     * @param key cryptographic key to store
     * @param password password for unlocking the key entry in the future. This does not have to be the same password as any keys stored,
     * but for SSL purposes this is recommended.
     * @param chain the sequence of certificates starting with the public key certificate for this key and extending to the root CA cert
     */
    private fun KeyStore.addOrReplaceKey(alias: String, key: Key, password: CharArray, chain: Array<Certificate>) {
        try {
            this.deleteEntry(alias)
        } catch (kse: KeyStoreException) {
            // ignore as may not exist in keystore yet
        }
        this.setKeyEntry(alias, key, password, chain)
    }

    /**
     * Helper extension method to add, or overwrite any public certificate data in store
     * @param alias name to record the public certificate under
     * @param cert certificate to store
     */
    private fun KeyStore.addOrReplaceCertificate(alias: String, cert: Certificate) {
        try {
            this.deleteEntry(alias)
        } catch (kse: KeyStoreException) {
            // ignore as may not exist in keystore yet
        }
        this.setCertificateEntry(alias, cert)
    }


    /**
     * Generate a standard curve ECDSA KeyPair suitable for TLS, although the rest of Corda uses newer curves.
     * @return The generated Public/Private KeyPair
     */
    fun generateECDSAKeyPairForSSL(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance(KEY_GENERATION_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME)
        val ecSpec = ECGenParameterSpec(ECDSA_CURVE) // Force named curve, because TLS implementations don't support many curves
        keyGen.initialize(ecSpec, SecureRandom())
        return keyGen.generateKeyPair()
    }

    /**
     * Helper data class to pass around public certificate and KeyPair entities when using CA certs
     */
    data class CACertAndKey(val certificate: X509Certificate, val keypair: KeyPair)


    /**
     * Create a de novo root self-signed X509 v3 CA cert and KeyPair.
     * @param domain The Common (CN) field of the cert Subject will be populated with the domain string
     * @return A data class is returned containing the new root CA Cert and its KeyPair for signing downstream certificates.
     * Note the generated certificate tree is capped at max depth of 2 to be in line with commercially available certificates
     */
    fun createSelfSignedCACert(domain: String): CACertAndKey {
        val keyPair = generateECDSAKeyPairForSSL()

        val issuer = GetX509Name(domain)
        val serial = BigInteger.valueOf(random63BitValue())
        val subject = issuer
        val pubKey = keyPair.public
        val window = GetCertificateValidityWindow(0, 365 * 10)

        val builder = JcaX509v3CertificateBuilder(
                issuer, serial, window.first, window.second, subject, pubKey)

        builder.addExtension(Extension.subjectKeyIdentifier, false,
                createSubjectKeyIdentifier(pubKey))
        builder.addExtension(Extension.basicConstraints, true,
                BasicConstraints(2))

        val usage = KeyUsage(KeyUsage.keyCertSign or KeyUsage.digitalSignature or KeyUsage.keyEncipherment or KeyUsage.dataEncipherment or KeyUsage.cRLSign)
        builder.addExtension(Extension.keyUsage, false, usage)

        val purposes = ASN1EncodableVector()
        purposes.add(KeyPurposeId.id_kp_serverAuth)
        purposes.add(KeyPurposeId.id_kp_clientAuth)
        purposes.add(KeyPurposeId.anyExtendedKeyUsage)
        builder.addExtension(Extension.extendedKeyUsage, false,
                DERSequence(purposes))

        val cert = signCertificate(builder, keyPair.private)

        cert.checkValidity(Date())
        cert.verify(pubKey)

        return CACertAndKey(cert, keyPair)
    }

    /**
     * Create a de novo root intermediate X509 v3 CA cert and KeyPair.
     * @param domain The Common (CN) field of the cert Subject will be populated with the domain string
     * @param certificateAuthority The Public certificate and KeyPair of the root CA certificate above this used to sign it
     * @return A data class is returned containing the new intermediate CA Cert and its KeyPair for signing downstream certificates.
     * Note the generated certificate tree is capped at max depth of 1 below this to be in line with commercially available certificates
     */
    fun createIntermediateCert(domain: String,
                               certificateAuthority: CACertAndKey): CACertAndKey {
        val keyPair = generateECDSAKeyPairForSSL()

        val issuer = X509CertificateHolder(certificateAuthority.certificate.encoded).subject
        val serial = BigInteger.valueOf(random63BitValue())
        val subject = GetX509Name(domain)
        val pubKey = keyPair.public
        // One year certificate validity
        val window = GetCertificateValidityWindow(0, 365, certificateAuthority.certificate.notBefore, certificateAuthority.certificate.notAfter)

        val builder = JcaX509v3CertificateBuilder(
                issuer, serial, window.first, window.second, subject, pubKey)

        builder.addExtension(Extension.subjectKeyIdentifier, false,
                createSubjectKeyIdentifier(pubKey))
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

        val cert = signCertificate(builder, certificateAuthority.keypair.private)

        cert.checkValidity(Date())
        cert.verify(certificateAuthority.keypair.public)

        return CACertAndKey(cert, keyPair)
    }

    /**
     * Create an X509v3 certificate suitable for use in TLS roles.
     * @param subject The contents to put in the subject field of the certificate
     * @param publicKey The PublicKey to be wrapped in the certificate
     * @param certificateAuthority The Public certificate and KeyPair of the parent CA that will sign this certificate
     * @param subjectAlternativeNameDomains A set of alternate DNS names to be supported by the certificate during validation of the TLS handshakes
     * @param subjectAlternativeNameIps A set of alternate IP addresses to be supported by the certificate during validation of the TLS handshakes
     * @return The generated X509Certificate suitable for use as a Server/Client certificate in TLS.
     * This certificate is not marked as a CA cert to be similar in nature to commercial certificates.
     */
    fun createServerCert(subject: X500Name,
                         publicKey: PublicKey,
                         certificateAuthority: CACertAndKey,
                         subjectAlternativeNameDomains: List<String>,
                         subjectAlternativeNameIps: List<String>): X509Certificate {

        val issuer = X509CertificateHolder(certificateAuthority.certificate.encoded).subject
        val serial = BigInteger.valueOf(random63BitValue())
        val window = GetCertificateValidityWindow(0, 365, certificateAuthority.certificate.notBefore, certificateAuthority.certificate.notAfter)

        val builder = JcaX509v3CertificateBuilder(issuer, serial, window.first, window.second, subject, publicKey)
        builder.addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyIdentifier(publicKey))
        builder.addExtension(Extension.basicConstraints, false, BasicConstraints(false))

        val usage = KeyUsage(KeyUsage.digitalSignature)
        builder.addExtension(Extension.keyUsage, false, usage)

        val purposes = ASN1EncodableVector()
        purposes.add(KeyPurposeId.id_kp_serverAuth)
        purposes.add(KeyPurposeId.id_kp_clientAuth)
        builder.addExtension(Extension.extendedKeyUsage, false,
                DERSequence(purposes))

        val subjectAlternativeNames = ArrayList<ASN1Encodable>()
        subjectAlternativeNames.add(GeneralName(GeneralName.dNSName, subject.getRDNs(BCStyle.CN).first().first.value))

        for (subjectAlternativeNameDomain in subjectAlternativeNameDomains) {
            subjectAlternativeNames.add(GeneralName(GeneralName.dNSName, subjectAlternativeNameDomain))
        }

        for (subjectAlternativeNameIp in subjectAlternativeNameIps) {
            if (IPAddress.isValidIPv6WithNetmask(subjectAlternativeNameIp)
                    || IPAddress.isValidIPv6(subjectAlternativeNameIp)
                    || IPAddress.isValidIPv4WithNetmask(subjectAlternativeNameIp)
                    || IPAddress.isValidIPv4(subjectAlternativeNameIp)) {
                subjectAlternativeNames.add(GeneralName(GeneralName.iPAddress, subjectAlternativeNameIp))
            }
        }

        val subjectAlternativeNamesExtension = DERSequence(subjectAlternativeNames.toTypedArray())
        builder.addExtension(Extension.subjectAlternativeName, false, subjectAlternativeNamesExtension)

        val cert = signCertificate(builder, certificateAuthority.keypair.private)

        cert.checkValidity(Date())
        cert.verify(certificateAuthority.keypair.public)

        return cert
    }

    /**
     * Helper method to store a .pem/.cer format file copy of a certificate if required for import into a PC/Mac, or for inspection
     * @param x509Certificate certificate to save
     * @param filename Target filename
     */
    fun saveCertificateAsPEMFile(x509Certificate: X509Certificate, filename: Path) {
        val fileWriter = FileWriter(filename.toFile())
        var jcaPEMWriter: JcaPEMWriter? = null
        try {
            jcaPEMWriter = JcaPEMWriter(fileWriter)
            jcaPEMWriter.writeObject(x509Certificate)
        } finally {
            jcaPEMWriter?.close()
            fileWriter.close()
        }
    }

    /**
     * Helper method to load back a .pem/.cer format file copy of a certificate
     * @param filename Source filename
     * @return The X509Certificate that was encoded in the file
     */
    fun loadCertificateFromPEMFile(filename: Path): X509Certificate {
        val reader = PemReader(FileReader(filename.toFile()))
        val pemObject = reader.readPemObject()
        val certFact = CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME)
        val inputStream = ByteArrayInputStream(pemObject.content)
        try {
            val cert = certFact.generateCertificate(inputStream) as X509Certificate
            cert.checkValidity()
            return cert
        } finally {
            inputStream.close()
        }
    }

    /**
     * Extract public and private keys from a KeyStore file assuming storage alias is know
     * @param keyStoreFilePath Path to load KeyStore from
     * @param storePassword Password to unlock the KeyStore
     * @param keyPassword Password to unlock the private key entries
     * @param alias The name to lookup the Key and Certificate chain from
     * @return The KeyPair found in the KeyStore under the specified alias
     */
    fun loadKeyPairFromKeyStore(keyStoreFilePath: Path,
                                storePassword: String,
                                keyPassword: String,
                                alias: String): KeyPair {
        val keyStore = loadKeyStore(keyStoreFilePath, storePassword)
        val keyEntry = keyStore.getKey(alias, keyPassword.toCharArray()) as PrivateKey
        val certificate = keyStore.getCertificate(alias) as X509Certificate
        return KeyPair(certificate.publicKey, keyEntry)
    }

    /**
     * Extract public X509 certificate from a KeyStore file assuming storage alias is know
     * @param keyStoreFilePath Path to load KeyStore from
     * @param storePassword Password to unlock the KeyStore
     * @param alias The name to lookup the Key and Certificate chain from
     * @return The X509Certificate found in the KeyStore under the specified alias
     */
    fun loadCertificateFromKeyStore(keyStoreFilePath: Path,
                                    storePassword: String,
                                    alias: String): X509Certificate {
        val keyStore = loadKeyStore(keyStoreFilePath, storePassword)
        return keyStore.getCertificate(alias) as X509Certificate
    }

    /**
     * All in one wrapper to manufacture a root CA cert and an Intermediate CA cert.
     * Normally this would be run once and then the outputs would be re-used repeatedly to manufacture the server certs
     * @param keyStoreFilePath The output KeyStore path to publish the private keys of the CA root and intermediate certs into.
     * @param storePassword The storage password to protect access to the generated KeyStore and public certificates
     * @param keyPassword The password that protects the CA private keys.
     * Unlike the SSL libraries that tend to assume the password is the same as the keystore password.
     * These CA private keys should be protected more effectively with a distinct password.
     * @param trustStoreFilePath The output KeyStore to place the Root CA public certificate, which can be used as an SSL truststore
     * @param trustStorePassword The password to protect the truststore
     * @return The KeyStore object that was saved to file
     */
    fun createCAKeyStoreAndTrustStore(keyStoreFilePath: Path,
                                      storePassword: String,
                                      keyPassword: String,
                                      trustStoreFilePath: Path,
                                      trustStorePassword: String
    ): KeyStore {
        val rootCA = X509Utilities.createSelfSignedCACert("Corda Node Root CA")
        val intermediateCA = X509Utilities.createIntermediateCert("Corda Node Intermediate CA", rootCA)

        val keypass = keyPassword.toCharArray()
        val keyStore = loadOrCreateKeyStore(keyStoreFilePath, storePassword)

        keyStore.addOrReplaceKey(ROOT_CA_CERT_PRIVATE_KEY_ALIAS, rootCA.keypair.private, keypass, arrayOf(rootCA.certificate))

        keyStore.addOrReplaceKey(INTERMEDIATE_CA_PRIVATE_KEY_ALIAS,
                intermediateCA.keypair.private,
                keypass,
                arrayOf(intermediateCA.certificate, rootCA.certificate))

        saveKeyStore(keyStore, keyStoreFilePath, storePassword)

        val trustStore = loadOrCreateKeyStore(trustStoreFilePath, trustStorePassword)

        trustStore.addOrReplaceCertificate(CA_CERT_ALIAS, rootCA.certificate)

        saveKeyStore(trustStore, trustStoreFilePath, trustStorePassword)

        return keyStore
    }

    /**
     * Helper method to load a Certificate and KeyPair from their KeyStore.
     * The access details should match those of the createCAKeyStoreAndTrustStore call used to manufacture the keys.
     * @param keyStore Source KeyStore to look in for the data
     * @param keyPassword The password for the PrivateKey (not the store access password)
     * @param alias The name to search for the data. Typically if generated with the methods here this will be one of
     * CERT_PRIVATE_KEY_ALIAS, ROOT_CA_CERT_PRIVATE_KEY_ALIAS, INTERMEDIATE_CA_PRIVATE_KEY_ALIAS defined above
     */
    fun loadCertificateAndKey(keyStore: KeyStore,
                              keyPassword: String,
                              alias: String): CACertAndKey {
        val keypass = keyPassword.toCharArray()
        val key = keyStore.getKey(alias, keypass) as PrivateKey
        val cert = keyStore.getCertificate(alias) as X509Certificate
        return CACertAndKey(cert, KeyPair(cert.publicKey, key))
    }

    /**
     * An all in wrapper to manufacture a server certificate and keys all stored in a KeyStore suitable for running TLS on the local machine
     * @param keyStoreFilePath KeyStore path to save output to
     * @param storePassword access password for KeyStore
     * @param keyPassword PrivateKey access password for the generated keys.
     * It is recommended that this is the same as the storePassword as most TLS libraries assume they are the same.
     * @param caKeyStore KeyStore containing CA keys generated by createCAKeyStoreAndTrustStore
     * @param caKeyPassword password to unlock private keys in the CA KeyStore
     * @return The KeyStore created containing a private key, certificate chain and root CA public cert for use in TLS applications
     */
    fun createKeystoreForSSL(keyStoreFilePath: Path,
                             storePassword: String,
                             keyPassword: String,
                             caKeyStore: KeyStore,
                             caKeyPassword: String): KeyStore {
        val rootCA = X509Utilities.loadCertificateAndKey(caKeyStore,
                caKeyPassword,
                X509Utilities.ROOT_CA_CERT_PRIVATE_KEY_ALIAS)
        val intermediateCA = X509Utilities.loadCertificateAndKey(caKeyStore,
                caKeyPassword,
                X509Utilities.INTERMEDIATE_CA_PRIVATE_KEY_ALIAS)

        val serverKey = X509Utilities.generateECDSAKeyPairForSSL()
        val host = InetAddress.getLocalHost()
        val subject = GetX509Name(host.canonicalHostName)
        val serverCert = X509Utilities.createServerCert(subject,
                serverKey.public,
                intermediateCA,
                listOf(),
                listOf(host.hostAddress))

        val keypass = keyPassword.toCharArray()
        val keyStore = loadOrCreateKeyStore(keyStoreFilePath, storePassword)

        keyStore.addOrReplaceKey(CERT_PRIVATE_KEY_ALIAS,
                serverKey.private,
                keypass,
                arrayOf(serverCert, intermediateCA.certificate, rootCA.certificate))

        keyStore.addOrReplaceCertificate(CA_CERT_ALIAS, rootCA.certificate)

        saveKeyStore(keyStore, keyStoreFilePath, storePassword)

        return keyStore
    }
}
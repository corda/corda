package net.corda.testing.common.internal

import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.createFile
import net.corda.core.internal.deleteIfExists
import net.corda.core.internal.div
import net.corda.nodeapi.internal.config.FileBasedCertificateStoreSupplier
import net.corda.nodeapi.internal.config.TwoWaySslConfiguration
import net.corda.nodeapi.internal.config.TwoWaySslOptions
import net.corda.nodeapi.internal.crypto.*
import org.apache.commons.io.FileUtils
import sun.security.tools.keytool.CertAndKeyGen
import sun.security.x509.X500Name
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.time.Instant.now
import java.time.temporal.ChronoUnit
import java.util.*
import javax.security.auth.x500.X500Principal

class UnsafeCertificatesFactory(
        defaults: Defaults = defaults(),
        private val keyType: String = defaults.keyType,
        private val signatureAlgorithm: String = defaults.signatureAlgorithm,
        private val keySize: Int = defaults.keySize,
        private val certificatesValidityWindow: CertificateValidityWindow = defaults.certificatesValidityWindow,
        private val keyStoreType: String = defaults.keyStoreType) {

    companion object {
        private const val KEY_TYPE_RSA = "RSA"
        private const val SIG_ALG_SHA_RSA = "SHA1WithRSA"
        private const val KEY_SIZE = 1024
        private val DEFAULT_DURATION = Duration.of(365, ChronoUnit.DAYS)
        private const val DEFAULT_KEYSTORE_TYPE = "JKS"

        fun defaults() = Defaults(KEY_TYPE_RSA, SIG_ALG_SHA_RSA, KEY_SIZE, CertificateValidityWindow(now(), DEFAULT_DURATION), DEFAULT_KEYSTORE_TYPE)
    }

    data class Defaults(
            val keyType: String,
            val signatureAlgorithm: String,
            val keySize: Int,
            val certificatesValidityWindow: CertificateValidityWindow,
            val keyStoreType: String)

    fun createSelfSigned(name: X500Name): UnsafeCertificate = createSelfSigned(name, keyType, signatureAlgorithm, keySize, certificatesValidityWindow)

    fun createSelfSigned(name: CordaX500Name) = createSelfSigned(name.asX500Name())

    fun createSignedBy(subject: X500Principal, issuer: UnsafeCertificate): UnsafeCertificate = issuer.createSigned(subject, keyType, signatureAlgorithm, keySize, certificatesValidityWindow)

    fun createSignedBy(name: CordaX500Name, issuer: UnsafeCertificate): UnsafeCertificate = issuer.createSigned(name, keyType, signatureAlgorithm, keySize, certificatesValidityWindow)

    fun newKeyStore(password: String) = UnsafeKeyStore.create(keyStoreType, password)

    fun newKeyStores(keyStorePassword: String, trustStorePassword: String): KeyStores = KeyStores(newKeyStore(keyStorePassword), newKeyStore(trustStorePassword))
}

class KeyStores(val keyStore: UnsafeKeyStore, val trustStore: UnsafeKeyStore) {
    fun save(directory: Path = Files.createTempDirectory(null)): AutoClosableSSLConfiguration {
        val keyStoreFile = keyStore.toTemporaryFile("sslkeystore", directory = directory)
        val trustStoreFile = trustStore.toTemporaryFile("truststore", directory = directory)

        val sslConfiguration = sslConfiguration(keyStoreFile, trustStoreFile)

        return object : AutoClosableSSLConfiguration {
            override val value = sslConfiguration

            override fun close() {
                keyStoreFile.close()
                trustStoreFile.close()
            }
        }
    }

    private fun sslConfiguration(keyStoreFile: TemporaryFile, trustStoreFile: TemporaryFile): TwoWaySslConfiguration {

        val keyStore = FileBasedCertificateStoreSupplier(keyStoreFile.file, keyStore.password)
        val trustStore = FileBasedCertificateStoreSupplier(trustStoreFile.file, trustStore.password)
        return TwoWaySslOptions(keyStore, trustStore)
    }
}

interface AutoClosableSSLConfiguration : AutoCloseable {
    val value: TwoWaySslConfiguration
}

typealias KeyStoreEntry = Pair<String, UnsafeCertificate>

data class UnsafeKeyStore(private val delegate: KeyStore, val password: String) : Iterable<KeyStoreEntry> {
    companion object {
        private const val JKS_TYPE = "JKS"

        fun create(type: String, password: String) = UnsafeKeyStore(newKeyStore(type, password), password)

        fun createJKS(password: String) = create(JKS_TYPE, password)
    }

    operator fun plus(entry: KeyStoreEntry) = set(entry.first, entry.second)

    override fun iterator(): Iterator<Pair<String, UnsafeCertificate>> = delegate.aliases().toList().map { alias -> alias to get(alias) }.iterator()

    operator fun get(alias: String): UnsafeCertificate {
        return when {
            delegate.isKeyEntry(alias) -> delegate.getCertificateAndKeyPair(alias, password).unsafe()
            else -> UnsafeCertificate(delegate.getX509Certificate(alias), null)
        }
    }

    operator fun set(alias: String, certificate: UnsafeCertificate) {
        delegate.setCertificateEntry(alias, certificate.value)
        delegate.setKeyEntry(alias, certificate.privateKey, password.toCharArray(), arrayOf(certificate.value))
    }

    fun save(path: Path) = delegate.save(path, password)

    fun toTemporaryFile(fileName: String, fileExtension: String? = delegate.type.toLowerCase(), directory: Path): TemporaryFile {
        return TemporaryFile("$fileName.$fileExtension", directory).also { save(it.file) }
    }
}

class TemporaryFile(fileName: String, val directory: Path) : AutoCloseable {
    val file: Path = (directory / fileName).createFile().toAbsolutePath()

    init {
        file.toFile().deleteOnExit()
    }

    override fun close() {
        file.deleteIfExists()
    }
}

data class UnsafeCertificate(val value: X509Certificate, val privateKey: PrivateKey?) {
    val keyPair = KeyPair(value.publicKey, privateKey)

    val principal: X500Principal get() = value.subjectX500Principal

    val issuer: X500Principal get() = value.issuerX500Principal

    fun createSigned(subject: X500Principal, keyType: String, signatureAlgorithm: String, keySize: Int, certificatesValidityWindow: CertificateValidityWindow): UnsafeCertificate {
        val keyGen = keyGen(keyType, signatureAlgorithm, keySize)

        return UnsafeCertificate(X509Utilities.createCertificate(
                certificateType = CertificateType.TLS,
                issuer = value.subjectX500Principal,
                issuerKeyPair = keyPair,
                validityWindow = certificatesValidityWindow.datePair,
                subject = subject,
                subjectPublicKey = keyGen.publicKey
        ), keyGen.privateKey)
    }

    fun createSigned(name: CordaX500Name, keyType: String, signatureAlgorithm: String, keySize: Int, certificatesValidityWindow: CertificateValidityWindow) = createSigned(name.x500Principal, keyType, signatureAlgorithm, keySize, certificatesValidityWindow)
}

data class CertificateValidityWindow(val from: Instant, val to: Instant) {
    constructor(from: Instant, duration: Duration) : this(from, from.plus(duration))

    val duration = Duration.between(from, to)!!

    val datePair = Date.from(from) to Date.from(to)
}

private fun createSelfSigned(name: X500Name, keyType: String, signatureAlgorithm: String, keySize: Int, certificatesValidityWindow: CertificateValidityWindow): UnsafeCertificate {
    val keyGen = keyGen(keyType, signatureAlgorithm, keySize)
    return UnsafeCertificate(keyGen.getSelfCertificate(name, certificatesValidityWindow.duration.toMillis()), keyGen.privateKey)
}

private fun CordaX500Name.asX500Name(): X500Name = X500Name.asX500Name(x500Principal)

private fun CertificateAndKeyPair.unsafe() = UnsafeCertificate(certificate, keyPair.private)

private fun keyGen(keyType: String, signatureAlgorithm: String, keySize: Int): CertAndKeyGen {
    val keyGen = CertAndKeyGen(keyType, signatureAlgorithm)
    keyGen.generate(keySize)
    return keyGen
}

private fun newKeyStore(type: String, password: String): KeyStore {
    val keyStore = KeyStore.getInstance(type)
    // Loading creates the store, can't do anything with it until it's loaded
    keyStore.load(null, password.toCharArray())

    return keyStore
}

fun withKeyStores(server: KeyStores, client: KeyStores, action: (brokerSslOptions: TwoWaySslConfiguration, clientSslOptions: TwoWaySslConfiguration) -> Unit) {
    val serverDir = Files.createTempDirectory(null)
    FileUtils.forceDeleteOnExit(serverDir.toFile())

    val clientDir = Files.createTempDirectory(null)
    FileUtils.forceDeleteOnExit(clientDir.toFile())

    server.save(serverDir).use { serverSslConfiguration ->
        client.save(clientDir).use { clientSslConfiguration ->
            action(serverSslConfiguration.value, clientSslConfiguration.value)
        }
    }
    clientDir.deleteIfExists()
    serverDir.deleteIfExists()
}

fun withCertificates(factoryDefaults: UnsafeCertificatesFactory.Defaults = UnsafeCertificatesFactory.defaults(), action: (server: KeyStores, client: KeyStores, createSelfSigned: (name: CordaX500Name) -> UnsafeCertificate, createSignedBy: (name: CordaX500Name, issuer: UnsafeCertificate) -> UnsafeCertificate) -> Unit) {
    val factory = UnsafeCertificatesFactory(factoryDefaults)
    val server = factory.newKeyStores("serverKeyStorePass", "serverTrustKeyStorePass")
    val client = factory.newKeyStores("clientKeyStorePass", "clientTrustKeyStorePass")
    action(server, client, factory::createSelfSigned, factory::createSignedBy)
}
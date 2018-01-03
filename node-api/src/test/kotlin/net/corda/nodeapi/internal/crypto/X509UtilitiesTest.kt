package net.corda.nodeapi.internal.crypto

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.Crypto.EDDSA_ED25519_SHA512
import net.corda.core.crypto.Crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.cert
import net.corda.core.internal.div
import net.corda.core.internal.toTypedArray
import net.corda.core.internal.x500Name
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.node.serialization.KryoServerSerializationScheme
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.createDevKeyStores
import net.corda.nodeapi.internal.serialization.AllWhitelist
import net.corda.nodeapi.internal.serialization.SerializationContextImpl
import net.corda.nodeapi.internal.serialization.SerializationFactoryImpl
import net.corda.nodeapi.internal.serialization.kryo.KryoHeaderV0_1
import net.corda.testing.ALICE_NAME
import net.corda.testing.BOB_NAME
import net.corda.testing.TestIdentity
import net.corda.testing.internal.createDevIntermediateCaCertPath
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Path
import java.security.SecureRandom
import java.security.cert.CertPath
import java.security.cert.X509Certificate
import java.util.*
import java.util.stream.Stream
import javax.net.ssl.*
import kotlin.concurrent.thread
import kotlin.test.*

class X509UtilitiesTest {
    private companion object {
        val ALICE = TestIdentity(ALICE_NAME, 70).party
        val bob = TestIdentity(BOB_NAME, 80)
        val MEGA_CORP = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).party
        val BOB get() = bob.party
        val BOB_PUBKEY get() = bob.publicKey
        val CIPHER_SUITES = arrayOf(
                "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"
        )
    }

    @Rule
    @JvmField
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `create valid self-signed CA certificate`() {
        val caKey = generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val caCert = X509Utilities.createSelfSignedCACertificate(CordaX500Name(commonName = "Test Cert", organisation = "R3 Ltd", locality = "London", country = "GB"), caKey)
        assertEquals(X500Name("CN=Test Cert,O=R3 Ltd,L=London,C=GB"), caCert.subject) // using our subject common name
        assertEquals(caCert.issuer, caCert.subject) //self-signed
        caCert.isValidOn(Date()) // throws on verification problems
        caCert.isSignatureValid(JcaContentVerifierProviderBuilder().build(caKey.public)) // throws on verification problems
        val basicConstraints = BasicConstraints.getInstance(caCert.getExtension(Extension.basicConstraints).parsedValue)
        val keyUsage = KeyUsage.getInstance(caCert.getExtension(Extension.keyUsage).parsedValue)
        assertFalse { keyUsage.hasUsages(5) } // Bit 5 == keyCertSign according to ASN.1 spec (see full comment on KeyUsage property)
        assertNull(basicConstraints.pathLenConstraint) // No length constraint specified on this CA certificate
    }

    @Test
    fun `load and save a PEM file certificate`() {
        val tmpCertificateFile = tempFile("cacert.pem")
        val caKey = generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val caCert = X509Utilities.createSelfSignedCACertificate(CordaX500Name(commonName = "Test Cert", organisation = "R3 Ltd", locality = "London", country = "GB"), caKey).cert
        X509Utilities.saveCertificateAsPEMFile(caCert, tmpCertificateFile)
        val readCertificate = X509Utilities.loadCertificateFromPEMFile(tmpCertificateFile)
        assertEquals(caCert, readCertificate)
    }

    @Test
    fun `create valid server certificate chain`() {
        val caKey = generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val caCert = X509Utilities.createSelfSignedCACertificate(CordaX500Name(commonName = "Test CA Cert", organisation = "R3 Ltd", locality = "London", country = "GB"), caKey)
        val subject = CordaX500Name(commonName = "Server Cert", organisation = "R3 Ltd", locality = "London", country = "GB")
        val keyPair = generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val serverCert = X509Utilities.createCertificate(CertificateType.TLS, caCert, caKey, subject, keyPair.public)
        assertEquals(X500Name("C=GB,L=London,O=R3 Ltd,CN=Server Cert"), serverCert.subject) // using our subject common name
        assertEquals(caCert.issuer, serverCert.issuer) // Issued by our CA cert
        serverCert.isValidOn(Date()) // throws on verification problems
        serverCert.isSignatureValid(JcaContentVerifierProviderBuilder().build(caKey.public)) // throws on verification problems
        val basicConstraints = BasicConstraints.getInstance(serverCert.getExtension(Extension.basicConstraints).parsedValue)
        val keyUsage = KeyUsage.getInstance(serverCert.getExtension(Extension.keyUsage).parsedValue)
        assertFalse { keyUsage.hasUsages(5) } // Bit 5 == keyCertSign according to ASN.1 spec (see full comment on KeyUsage property)
        assertNull(basicConstraints.pathLenConstraint) // Non-CA certificate
    }

    @Test
    fun `storing EdDSA key in java keystore`() {
        val tmpKeyStore = tempFile("keystore.jks")

        val keyPair = generateKeyPair(EDDSA_ED25519_SHA512)
        val testName = CordaX500Name(commonName = "Test", organisation = "R3 Ltd", locality = "London", country = "GB")
        val selfSignCert = X509Utilities.createSelfSignedCACertificate(testName, keyPair)

        assertTrue(Arrays.equals(selfSignCert.subjectPublicKeyInfo.encoded, keyPair.public.encoded))

        // Save the EdDSA private key with self sign cert in the keystore.
        val keyStore = loadOrCreateKeyStore(tmpKeyStore, "keystorepass")
        keyStore.setKeyEntry("Key", keyPair.private, "password".toCharArray(),
                Stream.of(selfSignCert).map { it.cert }.toTypedArray())
        keyStore.save(tmpKeyStore, "keystorepass")

        // Load the keystore from file and make sure keys are intact.
        val keyStore2 = loadOrCreateKeyStore(tmpKeyStore, "keystorepass")
        val privateKey = keyStore2.getKey("Key", "password".toCharArray())
        val pubKey = keyStore2.getCertificate("Key").publicKey

        assertNotNull(pubKey)
        assertNotNull(privateKey)
        assertEquals(keyPair.public, pubKey)
        assertEquals(keyPair.private, privateKey)
    }

    @Test
    fun `signing EdDSA key with EcDSA certificate`() {
        val tmpKeyStore = tempFile("keystore.jks")
        val ecDSAKey = generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val testName = CordaX500Name(commonName = "Test", organisation = "R3 Ltd", locality = "London", country = "GB")
        val ecDSACert = X509Utilities.createSelfSignedCACertificate(testName, ecDSAKey)
        val edDSAKeypair = generateKeyPair(EDDSA_ED25519_SHA512)
        val edDSACert = X509Utilities.createCertificate(CertificateType.TLS, ecDSACert, ecDSAKey, X500Name("CN=TestEdDSA"), edDSAKeypair.public)

        // Save the EdDSA private key with cert chains.
        val keyStore = loadOrCreateKeyStore(tmpKeyStore, "keystorepass")
        keyStore.setKeyEntry("Key", edDSAKeypair.private, "password".toCharArray(),
                Stream.of(ecDSACert, edDSACert).map { it.cert }.toTypedArray())
        keyStore.save(tmpKeyStore, "keystorepass")

        // Load the keystore from file and make sure keys are intact.
        val keyStore2 = loadOrCreateKeyStore(tmpKeyStore, "keystorepass")
        val privateKey = keyStore2.getKey("Key", "password".toCharArray())
        val certs = keyStore2.getCertificateChain("Key")

        val pubKey = certs.last().publicKey

        assertEquals(2, certs.size)
        assertNotNull(pubKey)
        assertNotNull(privateKey)
        assertEquals(edDSAKeypair.public, pubKey)
        assertEquals(edDSAKeypair.private, privateKey)
    }

    @Test
    fun `create server certificate in keystore for SSL`() {
        val sslConfig = object : SSLConfiguration {
            override val certificatesDirectory = tempFolder.root.toPath()
            override val keyStorePassword = "serverstorepass"
            override val trustStorePassword = "trustpass"
        }

        val (rootCa, intermediateCa) = createDevIntermediateCaCertPath()

        // Generate server cert and private key and populate another keystore suitable for SSL
        sslConfig.createDevKeyStores(rootCa.certificate, intermediateCa, MEGA_CORP.name)

        // Load back server certificate
        val serverKeyStore = loadKeyStore(sslConfig.nodeKeystore, sslConfig.keyStorePassword)
        val (serverCert, serverKeyPair) = serverKeyStore.getCertificateAndKeyPair(X509Utilities.CORDA_CLIENT_CA, sslConfig.keyStorePassword)

        serverCert.cert.checkValidity()
        serverCert.cert.verify(intermediateCa.certificate.cert.publicKey)
        assertThat(CordaX500Name.parse(serverCert.subject.toString())).isEqualTo(MEGA_CORP.name)

        // Load back SSL certificate
        val sslKeyStore = loadKeyStore(sslConfig.sslKeystore, sslConfig.keyStorePassword)
        val (sslCert) = sslKeyStore.getCertificateAndKeyPair(X509Utilities.CORDA_CLIENT_TLS, sslConfig.keyStorePassword)

        sslCert.cert.checkValidity()
        sslCert.cert.verify(serverCert.cert.publicKey)
        assertThat(CordaX500Name.parse(sslCert.subject.toString())).isEqualTo(MEGA_CORP.name)

        // Now sign something with private key and verify against certificate public key
        val testData = "123456".toByteArray()
        val signature = Crypto.doSign(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME, serverKeyPair.private, testData)
        val publicKey = Crypto.toSupportedPublicKey(serverCert.subjectPublicKeyInfo)
        assertTrue { Crypto.isValid(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME, publicKey, signature, testData) }
    }

    @Test
    fun `create server cert and use in SSL socket`() {
        val sslConfig = object : SSLConfiguration {
            override val certificatesDirectory = tempFolder.root.toPath()
            override val keyStorePassword = "serverstorepass"
            override val trustStorePassword = "trustpass"
        }

        val (rootCa, intermediateCa) = createDevIntermediateCaCertPath()

        // Generate server cert and private key and populate another keystore suitable for SSL
        sslConfig.createDevKeyStores(rootCa.certificate, intermediateCa, MEGA_CORP.name)
        sslConfig.createTrustStore(rootCa.certificate.cert)

        val keyStore = loadKeyStore(sslConfig.sslKeystore, sslConfig.keyStorePassword)
        val trustStore = loadKeyStore(sslConfig.trustStoreFile, sslConfig.trustStorePassword)

        val context = SSLContext.getInstance("TLS")
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, sslConfig.keyStorePassword.toCharArray())
        val keyManagers = keyManagerFactory.keyManagers
        val trustMgrFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustMgrFactory.init(trustStore)
        val trustManagers = trustMgrFactory.trustManagers
        context.init(keyManagers, trustManagers, SecureRandom())

        val serverSocketFactory = context.serverSocketFactory
        val clientSocketFactory = context.socketFactory

        val serverSocket = serverSocketFactory.createServerSocket(0) as SSLServerSocket // use 0 to get first free socket
        val serverParams = SSLParameters(CIPHER_SUITES, arrayOf("TLSv1.2"))
        serverParams.wantClientAuth = true
        serverParams.needClientAuth = true
        serverParams.endpointIdentificationAlgorithm = null // Reconfirm default no server name indication, use our own validator.
        serverSocket.sslParameters = serverParams
        serverSocket.useClientMode = false

        val clientSocket = clientSocketFactory.createSocket() as SSLSocket
        val clientParams = SSLParameters(CIPHER_SUITES, arrayOf("TLSv1.2"))
        clientParams.endpointIdentificationAlgorithm = null // Reconfirm default no server name indication, use our own validator.
        clientSocket.sslParameters = clientParams
        clientSocket.useClientMode = true
        // We need to specify this explicitly because by default the client binds to 'localhost' and we want it to bind
        // to whatever <hostname> resolves to(as that's what the server binds to). In particular on Debian <hostname>
        // resolves to 127.0.1.1 instead of the external address of the interface, so the ssl handshake fails.
        clientSocket.bind(InetSocketAddress(InetAddress.getLocalHost(), 0))

        val lock = Object()
        var done = false
        var serverError = false

        val serverThread = thread {
            try {
                val sslServerSocket = serverSocket.accept()
                assertTrue(sslServerSocket.isConnected)
                val serverInput = DataInputStream(sslServerSocket.inputStream)
                val receivedString = serverInput.readUTF()
                assertEquals("Hello World", receivedString)
                synchronized(lock) {
                    done = true
                    lock.notifyAll()
                }
                sslServerSocket.close()
            } catch (ex: Throwable) {
                serverError = true
            }
        }

        clientSocket.connect(InetSocketAddress(InetAddress.getLocalHost(), serverSocket.localPort))
        assertTrue(clientSocket.isConnected)

        // Double check hostname manually
        val peerChain = clientSocket.session.peerCertificates
        val peerX500Principal = (peerChain[0] as X509Certificate).subjectX500Principal
        assertEquals(MEGA_CORP.name.x500Principal, peerX500Principal)
        X509Utilities.validateCertificateChain(trustStore.getX509Certificate(X509Utilities.CORDA_ROOT_CA), *peerChain)
        val output = DataOutputStream(clientSocket.outputStream)
        output.writeUTF("Hello World")
        var timeout = 0
        synchronized(lock) {
            while (!done) {
                timeout++
                if (timeout > 10) throw IOException("Timed out waiting for server to complete")
                lock.wait(1000)
            }
        }

        clientSocket.close()
        serverThread.join(1000)
        assertFalse { serverError }
        serverSocket.close()
        assertTrue(done)
    }

    private fun tempFile(name: String): Path = tempFolder.root.toPath() / name

    private fun SSLConfiguration.createTrustStore(rootCert: X509Certificate) {
        val trustStore = loadOrCreateKeyStore(trustStoreFile, trustStorePassword)
        trustStore.addOrReplaceCertificate(X509Utilities.CORDA_ROOT_CA, rootCert)
        trustStore.save(trustStoreFile, trustStorePassword)
    }

    @Test
    fun `get correct private key type from Keystore`() {
        val keyPair = generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val testName = CordaX500Name(commonName = "Test", organisation = "R3 Ltd", locality = "London", country = "GB")
        val selfSignCert = X509Utilities.createSelfSignedCACertificate(testName, keyPair)
        val keyStore = loadOrCreateKeyStore(tempFile("testKeystore.jks"), "keystorepassword")
        keyStore.setKeyEntry("Key", keyPair.private, "keypassword".toCharArray(), arrayOf(selfSignCert.cert))

        val keyFromKeystore = keyStore.getKey("Key", "keypassword".toCharArray())
        val keyFromKeystoreCasted = keyStore.getSupportedKey("Key", "keypassword")

        assertTrue(keyFromKeystore is java.security.interfaces.ECPrivateKey) // by default JKS returns SUN EC key
        assertTrue(keyFromKeystoreCasted is org.bouncycastle.jce.interfaces.ECPrivateKey)
    }

    @Test
    fun `serialize - deserialize X509CertififcateHolder`() {
        val factory = SerializationFactoryImpl().apply { registerScheme(KryoServerSerializationScheme()) }
        val context = SerializationContextImpl(KryoHeaderV0_1,
                javaClass.classLoader,
                AllWhitelist,
                emptyMap(),
                true,
                SerializationContext.UseCase.P2P)
        val expected: X509CertificateHolder = X509Utilities.createSelfSignedCACertificate(ALICE.name, Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME))
        val serialized = expected.serialize(factory, context).bytes
        val actual: X509CertificateHolder = serialized.deserialize(factory, context)
        assertEquals(expected, actual)
    }

    @Test
    fun `serialize - deserialize X509CertPath`() {
        val factory = SerializationFactoryImpl().apply { registerScheme(KryoServerSerializationScheme()) }
        val context = SerializationContextImpl(KryoHeaderV0_1,
                javaClass.classLoader,
                AllWhitelist,
                emptyMap(),
                true,
                SerializationContext.UseCase.P2P)
        val rootCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val rootCACert = X509Utilities.createSelfSignedCACertificate(ALICE.name, rootCAKey)
        val certificate = X509Utilities.createCertificate(CertificateType.TLS, rootCACert, rootCAKey, BOB.name.x500Name, BOB_PUBKEY)
        val expected = X509CertificateFactory().generateCertPath(certificate.cert, rootCACert.cert)
        val serialized = expected.serialize(factory, context).bytes
        val actual: CertPath = serialized.deserialize(factory, context)
        assertEquals(expected, actual)
    }
}

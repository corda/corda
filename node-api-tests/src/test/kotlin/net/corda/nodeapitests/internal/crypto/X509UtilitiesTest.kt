package net.corda.nodeapitests.internal.crypto


import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslProvider
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.Crypto.COMPOSITE_KEY
import net.corda.core.crypto.Crypto.ECDSA_SECP256K1_SHA256
import net.corda.core.crypto.Crypto.ECDSA_SECP256R1_SHA256
import net.corda.core.crypto.Crypto.EDDSA_ED25519_SHA512
import net.corda.core.crypto.Crypto.RSA_SHA256
import net.corda.core.crypto.Crypto.SPHINCS256_SHA256
import net.corda.core.crypto.Crypto.generateKeyPair
import net.corda.core.crypto.SignatureScheme
import net.corda.core.crypto.newSecureRandom
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.days
import net.corda.core.utilities.hours
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.createDevNodeCa
import net.corda.nodeapi.internal.crypto.X509Utilities.DEFAULT_IDENTITY_SIGNATURE_SCHEME
import net.corda.nodeapi.internal.crypto.X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME
import net.corda.nodeapi.internal.installDevNodeCaCertPath
import net.corda.nodeapi.internal.protonwrapper.netty.init
import net.corda.nodeapi.internal.registerDevP2pCertificates
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.SerializationContextImpl
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.amqpMagic
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.coretesting.internal.NettyTestClient
import net.corda.coretesting.internal.NettyTestHandler
import net.corda.coretesting.internal.NettyTestServer
import net.corda.testing.internal.createDevIntermediateCaCertPath
import net.corda.coretesting.internal.stubs.CertificateStoreStubs
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.checkValidity
import net.corda.nodeapi.internal.crypto.getSupportedKey
import net.corda.nodeapi.internal.crypto.loadOrCreateKeyStore
import net.corda.nodeapi.internal.crypto.save
import net.corda.nodeapi.internal.crypto.toBc
import net.corda.nodeapi.internal.crypto.x509
import net.corda.testing.internal.IS_OPENJ9
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPrivateKey
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PrivateKey
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Path
import java.security.Key
import java.security.KeyPair
import java.security.PrivateKey
import java.security.cert.CertPath
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.*
import javax.security.auth.x500.X500Principal
import kotlin.concurrent.thread
import kotlin.test.*

class X509UtilitiesTest {
    private companion object {
        val ALICE = TestIdentity(ALICE_NAME, 70).party
        val BOB = TestIdentity(BOB_NAME, 80)
        val MEGA_CORP = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).party
        val CIPHER_SUITES = arrayOf(
                "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
        )

        val portAllocation = incrementalPortAllocation()

        // We ensure that all of the algorithms are both used (at least once) as first and second in the following [Pair]s.
        // We also add [DEFAULT_TLS_SIGNATURE_SCHEME] and [DEFAULT_IDENTITY_SIGNATURE_SCHEME] combinations for consistency.
        val certChainSchemeCombinations = listOf(
                Pair(DEFAULT_TLS_SIGNATURE_SCHEME, DEFAULT_TLS_SIGNATURE_SCHEME),
                Pair(DEFAULT_IDENTITY_SIGNATURE_SCHEME, DEFAULT_IDENTITY_SIGNATURE_SCHEME),
                Pair(DEFAULT_TLS_SIGNATURE_SCHEME, DEFAULT_IDENTITY_SIGNATURE_SCHEME),
                Pair(ECDSA_SECP256R1_SHA256, SPHINCS256_SHA256),
                Pair(ECDSA_SECP256K1_SHA256, RSA_SHA256),
                Pair(EDDSA_ED25519_SHA512, ECDSA_SECP256K1_SHA256),
                Pair(RSA_SHA256, EDDSA_ED25519_SHA512),
                Pair(EDDSA_ED25519_SHA512, ECDSA_SECP256R1_SHA256),
                Pair(SPHINCS256_SHA256, ECDSA_SECP256R1_SHA256)
        )

        val schemeToKeyTypes = listOf(
                // By default, JKS returns SUN EC key.
                Triple(ECDSA_SECP256R1_SHA256,java.security.interfaces.ECPrivateKey::class.java, org.bouncycastle.jce.interfaces.ECPrivateKey::class.java),
                Triple(ECDSA_SECP256K1_SHA256,java.security.interfaces.ECPrivateKey::class.java, org.bouncycastle.jce.interfaces.ECPrivateKey::class.java),
                Triple(EDDSA_ED25519_SHA512, EdDSAPrivateKey::class.java, EdDSAPrivateKey::class.java),
                // By default, JKS returns SUN RSA key.
                Triple(SPHINCS256_SHA256, BCSphincs256PrivateKey::class.java, BCSphincs256PrivateKey::class.java)
        )
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test(timeout=300_000)
	fun `create valid self-signed CA certificate`() {
        Crypto.supportedSignatureSchemes().filter { it != COMPOSITE_KEY
                && ( it != SPHINCS256_SHA256)}.forEach { validSelfSignedCertificate(it) }
    }

    private fun validSelfSignedCertificate(signatureScheme: SignatureScheme) {
        val caKey = generateKeyPair(signatureScheme)
        val subject = X500Principal("CN=Test Cert,O=R3 Ltd,L=London,C=GB")
        val caCert = X509Utilities.createSelfSignedCACertificate(subject, caKey)
        assertEquals(subject, caCert.subjectX500Principal) // using our subject common name
        assertEquals(caCert.issuerX500Principal, caCert.subjectX500Principal) //self-signed
        caCert.checkValidity(Date()) // throws on verification problems
        caCert.verify(caKey.public) // throws on verification problems
        caCert.toBc().run {
            val basicConstraints = BasicConstraints.getInstance(getExtension(Extension.basicConstraints).parsedValue)
            val keyUsage = KeyUsage.getInstance(getExtension(Extension.keyUsage).parsedValue)
            assertFalse { keyUsage.hasUsages(5) } // Bit 5 == keyCertSign according to ASN.1 spec (see full comment on KeyUsage property)
            assertNull(basicConstraints.pathLenConstraint) // No length constraint specified on this CA certificate
        }
    }

    @Test(timeout=300_000)
	fun `load and save a PEM file certificate`() {
        Crypto.supportedSignatureSchemes().filter { it != COMPOSITE_KEY }.forEach { loadSavePEMCert(it) }
    }

    private fun loadSavePEMCert(signatureScheme: SignatureScheme) {
        val tmpCertificateFile = tempFile("cacert.pem")
        val caKey = generateKeyPair(signatureScheme)
        val caCert = X509Utilities.createSelfSignedCACertificate(X500Principal("CN=Test Cert,O=R3 Ltd,L=London,C=GB"), caKey)
        X509Utilities.saveCertificateAsPEMFile(caCert, tmpCertificateFile)
        val readCertificate = X509Utilities.loadCertificateFromPEMFile(tmpCertificateFile)
        assertEquals(caCert, readCertificate)
    }

    @Test(timeout=300_000)
	fun `create valid server certificate chain`() {
        certChainSchemeCombinations.filter{ it.first != SPHINCS256_SHA256 }
                                   .forEach { createValidServerCertChain(it.first, it.second) }
    }

    private fun createValidServerCertChain(signatureSchemeRoot: SignatureScheme, signatureSchemeChild: SignatureScheme) {
        val (caKeyPair, caCert, _, childCert, _, childSubject)
                = genCaAndChildKeysCertsAndSubjects(signatureSchemeRoot, signatureSchemeChild)
        assertEquals(childSubject, childCert.subjectX500Principal) // Using our subject common name.
        assertEquals(caCert.issuerX500Principal, childCert.issuerX500Principal) // Issued by our CA cert.
        childCert.checkValidity(Date()) // Throws on verification problems.
        childCert.verify(caKeyPair.public) // Throws on verification problems.
        childCert.toBc().run {
            val basicConstraints = BasicConstraints.getInstance(getExtension(Extension.basicConstraints).parsedValue)
            val keyUsage = KeyUsage.getInstance(getExtension(Extension.keyUsage).parsedValue)
            assertFalse { keyUsage.hasUsages(5) } // Bit 5 == keyCertSign according to ASN.1 spec (see full comment on KeyUsage property).
            assertNull(basicConstraints.pathLenConstraint) // Non-CA certificate.
        }
    }

    private data class CaAndChildKeysCertsAndSubjects(val caKeyPair: KeyPair,
                                                      val caCert: X509Certificate,
                                                      val childKeyPair: KeyPair,
                                                      val childCert: X509Certificate,
                                                      val caSubject: X500Principal,
                                                      val childSubject: X500Principal)

    private fun genCaAndChildKeysCertsAndSubjects(signatureSchemeRoot: SignatureScheme,
                                   signatureSchemeChild: SignatureScheme,
                                   rootSubject: X500Principal = X500Principal("CN=Test CA Cert,O=R3 Ltd,L=London,C=GB"),
                                   childSubject: X500Principal = X500Principal("CN=Test Child Cert,O=R3 Ltd,L=London,C=GB")): CaAndChildKeysCertsAndSubjects {
        val caKeyPair = generateKeyPair(signatureSchemeRoot)
        val caCert = X509Utilities.createSelfSignedCACertificate(rootSubject, caKeyPair)
        val childKeyPair = generateKeyPair(signatureSchemeChild)
        val childCert = X509Utilities.createCertificate(CertificateType.TLS, caCert, caKeyPair, childSubject, childKeyPair.public)
        return CaAndChildKeysCertsAndSubjects(caKeyPair, caCert, childKeyPair, childCert, rootSubject, childSubject)
    }

    @Test(timeout=300_000)
	fun `create valid server certificate chain includes CRL info`() {
        certChainSchemeCombinations.forEach { createValidServerCertIncludeCRL(it.first, it.second) }
    }

    private fun createValidServerCertIncludeCRL(signatureSchemeRoot: SignatureScheme, signatureSchemeChild: SignatureScheme) {
        val caKey = generateKeyPair(signatureSchemeRoot)
        val caCert = X509Utilities.createSelfSignedCACertificate(X500Principal("CN=Test CA Cert,O=R3 Ltd,L=London,C=GB"), caKey)
        val caSubjectKeyIdentifier = SubjectKeyIdentifier.getInstance(caCert.toBc().getExtension(Extension.subjectKeyIdentifier).parsedValue)
        val keyPair = generateKeyPair(signatureSchemeChild)
        val crlDistPoint = "http://test.com"
        val serverCert = X509Utilities.createCertificate(
                CertificateType.TLS,
                caCert,
                caKey,
                X500Principal("CN=Server Cert,O=R3 Ltd,L=London,C=GB"),
                keyPair.public,
                crlDistPoint = crlDistPoint)
        serverCert.toBc().run {
            val certCrlDistPoint = CRLDistPoint.getInstance(getExtension(Extension.cRLDistributionPoints).parsedValue)
            assertTrue(certCrlDistPoint.distributionPoints.first().distributionPoint.toString().contains(crlDistPoint))
            val certCaAuthorityKeyIdentifier = AuthorityKeyIdentifier.getInstance(getExtension(Extension.authorityKeyIdentifier).parsedValue)
            assertTrue(Arrays.equals(caSubjectKeyIdentifier.keyIdentifier, certCaAuthorityKeyIdentifier.keyIdentifier))
        }
    }

    @Test(timeout=300_000)
	fun `storing all supported key types in java keystore`() {
        Crypto.supportedSignatureSchemes().filter { it != COMPOSITE_KEY }.forEach { storeKeyToKeystore(it) }
    }

    private fun storeKeyToKeystore(signatureScheme: SignatureScheme) {
        val tmpKeyStore = tempFile("keystore.jks")

        val keyPair = generateKeyPair(signatureScheme)
        val testName = X500Principal("CN=Test,O=R3 Ltd,L=London,C=GB")
        val selfSignCert = X509Utilities.createSelfSignedCACertificate(testName, keyPair)

        assertTrue(Arrays.equals(selfSignCert.publicKey.encoded, keyPair.public.encoded))

        // Save the private key with self sign cert in the keystore.
        val keyStore = loadOrCreateKeyStore(tmpKeyStore, "keystorepass")
        keyStore.setKeyEntry("Key", keyPair.private, "password".toCharArray(), arrayOf(selfSignCert))
        keyStore.save(tmpKeyStore, "keystorepass")

        // Load the keystore from file and make sure keys are intact.
        val reloadedKeystore = loadOrCreateKeyStore(tmpKeyStore, "keystorepass")
        val reloadedPrivateKey = reloadedKeystore.getKey("Key", "password".toCharArray())
        val reloadedPublicKey = reloadedKeystore.getCertificate("Key").publicKey

        assertNotNull(reloadedPublicKey)
        assertNotNull(reloadedPrivateKey)
        assertEquals(keyPair.public, reloadedPublicKey)
        assertEquals(keyPair.private, reloadedPrivateKey)
    }

    @Test(timeout=300_000)
	fun `create server certificate in keystore for SSL`() {
        val certificatesDirectory = tempFolder.root.toPath()
        val signingCertStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory, "serverstorepass")
        val p2pSslConfig = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory, keyStorePassword = "serverstorepass")

        val (rootCa, intermediateCa) = createDevIntermediateCaCertPath()

        // Generate server cert and private key and populate another keystore suitable for SSL
        val nodeCa = createDevNodeCa(intermediateCa, MEGA_CORP.name)
        signingCertStore.get(createNew = true).also { it.installDevNodeCaCertPath(MEGA_CORP.name, rootCa.certificate, intermediateCa, nodeCa) }
        p2pSslConfig.keyStore.get(createNew = true).also { it.registerDevP2pCertificates(MEGA_CORP.name, rootCa.certificate, intermediateCa, nodeCa) }
        // Load back server certificate
        val certStore = signingCertStore.get()
        val serverKeyStore = certStore.value
        val (serverCert, serverKeyPair) = serverKeyStore.getCertificateAndKeyPair(X509Utilities.CORDA_CLIENT_CA, certStore.entryPassword)

        serverCert.checkValidity()
        serverCert.verify(intermediateCa.certificate.publicKey)
        assertThat(CordaX500Name.build(serverCert.subjectX500Principal)).isEqualTo(MEGA_CORP.name)

        // Load back SSL certificate
        val sslKeyStoreReloaded = p2pSslConfig.keyStore.get()
        val (sslCert) = sslKeyStoreReloaded.query { getCertificateAndKeyPair(X509Utilities.CORDA_CLIENT_TLS, sslKeyStoreReloaded.entryPassword) }

        sslCert.checkValidity()
        sslCert.verify(serverCert.publicKey)
        assertThat(CordaX500Name.build(sslCert.subjectX500Principal)).isEqualTo(MEGA_CORP.name)

        // Now sign something with private key and verify against certificate public key
        val testData = "123456".toByteArray()
        val signature = Crypto.doSign(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME, serverKeyPair.private, testData)
        assertTrue { Crypto.isValid(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME, serverCert.publicKey, signature, testData) }
    }

    @Test(timeout=300_000)
	fun `create server cert and use in SSL socket`() {
        val sslConfig = CertificateStoreStubs.P2P.withCertificatesDirectory(tempFolder.root.toPath(), keyStorePassword = "serverstorepass")

        val (rootCa, intermediateCa) = createDevIntermediateCaCertPath()

        // Generate server cert and private key and populate another keystore suitable for SSL
        sslConfig.keyStore.get(true).registerDevP2pCertificates(MEGA_CORP.name, rootCa.certificate, intermediateCa)
        sslConfig.createTrustStore(rootCa.certificate)

        val keyStore = sslConfig.keyStore.get()
        val trustStore = sslConfig.trustStore.get()

        val context = SSLContext.getInstance("TLS")
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore)
        val keyManagers = keyManagerFactory.keyManagers
        val trustMgrFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustMgrFactory.init(trustStore)
        val trustManagers = trustMgrFactory.trustManagers
        context.init(keyManagers, trustManagers, newSecureRandom())

        val serverSocketFactory = context.serverSocketFactory
        val clientSocketFactory = context.socketFactory

        val serverSocket = serverSocketFactory.createServerSocket(0) as SSLServerSocket // use 0 to get first free socket
        val serverParams = SSLParameters(CIPHER_SUITES,
                arrayOf("TLSv1.2"))
        serverParams.wantClientAuth = true
        serverParams.needClientAuth = true
        serverParams.endpointIdentificationAlgorithm = null // Reconfirm default no server name indication, use our own validator.
        serverSocket.sslParameters = serverParams
        serverSocket.useClientMode = false

        val clientSocket = clientSocketFactory.createSocket() as SSLSocket
        val clientParams = SSLParameters(CIPHER_SUITES,
                arrayOf("TLSv1.2"))
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
            } catch (ex: Exception) {
                serverError = true
            }
        }

        clientSocket.connect(InetSocketAddress(InetAddress.getLocalHost(), serverSocket.localPort))
        assertTrue(clientSocket.isConnected)

        // Double check hostname manually
        val peerChain = clientSocket.session.peerCertificates.x509
        val peerX500Principal = peerChain[0].subjectX500Principal
        assertEquals(MEGA_CORP.name.x500Principal, peerX500Principal)
        X509Utilities.validateCertificateChain(listOf(rootCa.certificate), peerChain)
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

    @Test(timeout=300_000)
	fun `create server cert and use in OpenSSL channel`() {
        Assume.assumeTrue(!IS_OPENJ9)
        val sslConfig = CertificateStoreStubs.P2P.withCertificatesDirectory(tempFolder.root.toPath(), keyStorePassword = "serverstorepass")

        val (rootCa, intermediateCa) = createDevIntermediateCaCertPath()

        // Generate server cert and private key and populate another keystore suitable for SSL
        sslConfig.keyStore.get(true).registerDevP2pCertificates(MEGA_CORP.name, rootCa.certificate, intermediateCa)
        sslConfig.createTrustStore(rootCa.certificate)

        val keyStore = sslConfig.keyStore.get()
        val trustStore = sslConfig.trustStore.get()

        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore)

        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(trustStore)


        val sslServerContext = SslContextBuilder
                .forServer(keyManagerFactory)
                .trustManager(trustManagerFactory)
                .clientAuth(ClientAuth.REQUIRE)
                .ciphers(CIPHER_SUITES.toMutableList())
                .sslProvider(SslProvider.OPENSSL)
                .protocols("TLSv1.2")
                .build()
        val sslClientContext = SslContextBuilder
                .forClient()
                .keyManager(keyManagerFactory)
                .trustManager(trustManagerFactory)
                .ciphers(CIPHER_SUITES.toMutableList())
                .sslProvider(SslProvider.OPENSSL)
                .protocols("TLSv1.2")
                .build()
        val serverHandler = NettyTestHandler { ctx, msg -> ctx?.writeAndFlush(msg) }
        val clientHandler = NettyTestHandler { _, msg -> assertEquals("Hello", NettyTestHandler.readString(msg)) }
        NettyTestServer(sslServerContext, serverHandler, portAllocation.nextPort()).use { server ->
            server.start()
            NettyTestClient(sslClientContext, InetAddress.getLocalHost().canonicalHostName, server.port, clientHandler).use { client ->
                client.start()

                clientHandler.writeString("Hello")
                val readCalled = clientHandler.waitForReadCalled()
                clientHandler.rethrowIfFailed()
                serverHandler.rethrowIfFailed()
                assertTrue(readCalled)
                assertEquals(1, serverHandler.readCalledCounter)
                assertEquals(1, clientHandler.readCalledCounter)

                val peerChain = client.engine!!.session.peerCertificates.x509
                val peerX500Principal = peerChain[0].subjectX500Principal
                assertEquals(MEGA_CORP.name.x500Principal, peerX500Principal)
                X509Utilities.validateCertificateChain(listOf(rootCa.certificate), peerChain)
            }
        }
    }

    private fun tempFile(name: String): Path = tempFolder.root.toPath() / name

    private fun MutualSslConfiguration.createTrustStore(rootCert: X509Certificate) {
        val trustStore = this.trustStore.get(true)
        trustStore[X509Utilities.CORDA_ROOT_CA] = rootCert
    }

    @Test(timeout=300_000)
	fun `get correct private key type from Keystore`() {
        schemeToKeyTypes.forEach { getCorrectKeyFromKeystore(it.first, it.second, it.third) }
    }

    private fun <U, C> getCorrectKeyFromKeystore(signatureScheme: SignatureScheme, uncastedClass: Class<U>, castedClass: Class<C>) {
        val keyPair = generateKeyPair(signatureScheme)
        val (keyFromKeystore, keyFromKeystoreCasted) = storeAndGetKeysFromKeystore(keyPair)
        if (uncastedClass == EdDSAPrivateKey::class.java && keyFromKeystore !is BCEdDSAPrivateKey) {
            assertThat(keyFromKeystore).isInstanceOf(uncastedClass)
        }
        assertThat(keyFromKeystoreCasted).isInstanceOf(castedClass)
    }

    private fun storeAndGetKeysFromKeystore(keyPair: KeyPair): Pair<Key, PrivateKey> {
        val testName = X500Principal("CN=Test,O=R3 Ltd,L=London,C=GB")
        val selfSignCert = X509Utilities.createSelfSignedCACertificate(testName, keyPair)
        val keyStore = loadOrCreateKeyStore(tempFile("testKeystore.jks"), "keystorepassword")
        keyStore.setKeyEntry("Key", keyPair.private, "keypassword".toCharArray(), arrayOf(selfSignCert))

        val keyFromKeystore = keyStore.getKey("Key", "keypassword".toCharArray())
        val keyFromKeystoreCasted = keyStore.getSupportedKey("Key", "keypassword")
        return Pair(keyFromKeystore, keyFromKeystoreCasted)
    }

    @Test(timeout=300_000)
	fun `serialize - deserialize X509Certificate`() {
        Crypto.supportedSignatureSchemes().filter { it != COMPOSITE_KEY }.forEach { serializeDeserializeX509Cert(it) }
    }

    private fun serializeDeserializeX509Cert(signatureScheme: SignatureScheme) {
        val factory = SerializationFactoryImpl().apply { registerScheme(AMQPServerSerializationScheme()) }
        val context = SerializationContextImpl(amqpMagic,
                javaClass.classLoader,
                AllWhitelist,
                emptyMap(),
                true,
                SerializationContext.UseCase.P2P,
                null)
        val expected = X509Utilities.createSelfSignedCACertificate(ALICE.name.x500Principal, generateKeyPair(signatureScheme))
        val serialized = expected.serialize(factory, context).bytes
        val actual = serialized.deserialize<X509Certificate>(factory, context)
        assertEquals(expected, actual)
    }

    @Test(timeout=300_000)
	fun `serialize - deserialize X509CertPath`() {
        Crypto.supportedSignatureSchemes().filter { it != COMPOSITE_KEY }.forEach { serializeDeserializeX509CertPath(it) }
    }

    private fun serializeDeserializeX509CertPath(signatureScheme: SignatureScheme) {
        val factory = SerializationFactoryImpl().apply { registerScheme(AMQPServerSerializationScheme()) }
        val context = SerializationContextImpl(
                amqpMagic,
                javaClass.classLoader,
                AllWhitelist,
                emptyMap(),
                true,
                SerializationContext.UseCase.P2P,
                null
        )
        val rootCAKey = generateKeyPair(signatureScheme)
        val rootCACert = X509Utilities.createSelfSignedCACertificate(ALICE_NAME.x500Principal, rootCAKey)
        val certificate = X509Utilities.createCertificate(CertificateType.TLS, rootCACert, rootCAKey, BOB_NAME.x500Principal, BOB.publicKey)
        val expected = X509Utilities.buildCertPath(certificate, rootCACert)
        val serialized = expected.serialize(factory, context).bytes
        val actual: CertPath = serialized.deserialize(factory, context)
        assertEquals(expected, actual)
    }

    @Test(timeout=300_000)
	fun `signing a key type with another key type certificate then store and reload correctly from keystore`() {
        certChainSchemeCombinations.forEach { signCertWithOtherKeyTypeAndTestKeystoreReload(it.first, it.second) }
    }

    private fun signCertWithOtherKeyTypeAndTestKeystoreReload(signatureSchemeRoot: SignatureScheme, signatureSchemeChild: SignatureScheme) {
        val tmpKeyStore = tempFile("keystore.jks")

        val (_, caCert, childKeyPair, childCert) = genCaAndChildKeysCertsAndSubjects(signatureSchemeRoot, signatureSchemeChild)

        // Save the child private key with cert chains.
        val keyStore = loadOrCreateKeyStore(tmpKeyStore, "keystorepass")
        keyStore.setKeyEntry("Key", childKeyPair.private, "password".toCharArray(), arrayOf(caCert, childCert))
        keyStore.save(tmpKeyStore, "keystorepass")

        // Load the keystore from file and make sure keys are intact.
        val reloadedKeystore = loadOrCreateKeyStore(tmpKeyStore, "keystorepass")
        val reloadedPrivateKey = reloadedKeystore.getKey("Key", "password".toCharArray())
        val reloadedCerts = reloadedKeystore.getCertificateChain("Key")

        val reloadedPublicKey = reloadedCerts.last().publicKey

        assertEquals(2, reloadedCerts.size)
        assertNotNull(reloadedPublicKey)
        assertNotNull(reloadedPrivateKey)
        assertEquals(childKeyPair.public, reloadedPublicKey)
        assertEquals(childKeyPair.private, reloadedPrivateKey)
    }

    @Test(timeout=300_000)
	fun `check certificate validity or print warning if expiry is within 30 days`() {
        val keyPair = generateKeyPair(DEFAULT_TLS_SIGNATURE_SCHEME)
        val testName = X500Principal("CN=Test,O=R3 Ltd,L=London,C=GB")
        val cert = X509Utilities.createSelfSignedCACertificate(testName, keyPair, 0.days to 50.days)
        val today = cert.notBefore
        var warnings = 0
        cert.checkValidity({ "No error expected" }, { fail("No warning expected") }, today)
        cert.checkValidity({ "No error expected" }, { fail("No warning expected") }, Date.from(today.toInstant() + 20.days))
        cert.checkValidity({ "No error expected" }, { daysToExpiry ->
            assertEquals(30, daysToExpiry)
            warnings++
        }, Date.from(today.toInstant() + 20.days + 3.hours))
        cert.checkValidity({ "No error expected" }, { daysToExpiry ->
            assertEquals(11, daysToExpiry)
            warnings++
        }, Date.from(today.toInstant() + 40.days))
        cert.checkValidity({ "No error expected" }, { daysToExpiry ->
            assertEquals(1, daysToExpiry)
            warnings++
        }, Date.from(today.toInstant() + 49.days + 20.hours))
        assertEquals(3, warnings)
        assertFailsWith(IllegalArgumentException::class, "Error text") {
            cert.checkValidity({ "Error text" }, { }, Date.from(today.toInstant() + 50.days + 1.hours))
        }
        assertFailsWith(IllegalArgumentException::class, "Error text") {
            cert.checkValidity({ "Error text" }, { }, Date.from(today.toInstant() + 51.days))
        }
    }
}

package net.corda.core.crypto

import net.corda.core.div
import net.corda.testing.MEGA_CORP
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.GeneralName
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Path
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.*
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class X509UtilitiesTest {
    @Rule
    @JvmField
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `create valid self-signed CA certificate`() {
        val caCertAndKey = X509Utilities.createSelfSignedCACert(X500Name("CN=Test Cert,OU=Corda QA Department,O=R3 CEV,L=New York,C=US"))
        assertTrue { caCertAndKey.certificate.subjectDN.name.contains("CN=Test Cert") } // using our subject common name
        assertEquals(caCertAndKey.certificate.issuerDN, caCertAndKey.certificate.subjectDN) //self-signed
        caCertAndKey.certificate.checkValidity(Date()) // throws on verification problems
        caCertAndKey.certificate.verify(caCertAndKey.keyPair.public) // throws on verification problems
        assertTrue { caCertAndKey.certificate.keyUsage[5] } // Bit 5 == keyCertSign according to ASN.1 spec (see full comment on KeyUsage property)
        assertTrue { caCertAndKey.certificate.basicConstraints > 0 } // This returns the signing path length Would be -1 for non-CA certificate
    }

    @Test
    fun `load and save a PEM file certificate`() {
        val tmpCertificateFile = tempFile("cacert.pem")
        val caCertAndKey = X509Utilities.createSelfSignedCACert(X500Name("CN=Test Cert,OU=Corda QA Department,O=R3 CEV,L=New York,C=US"))
        X509Utilities.saveCertificateAsPEMFile(caCertAndKey.certificate, tmpCertificateFile)
        val readCertificate = X509Utilities.loadCertificateFromPEMFile(tmpCertificateFile)
        assertEquals(caCertAndKey.certificate, readCertificate)
    }

    @Test
    fun `create valid server certificate chain`() {
        val caCertAndKey = X509Utilities.createSelfSignedCACert(X500Name("CN=Test CA Cert,OU=Corda QA Department,O=R3 CEV,L=New York,C=US"))
        val subjectDN = X500Name("CN=Server Cert,OU=Corda QA Department,O=R3 CEV,L=New York,C=US")
        val keyPair = X509Utilities.generateECDSAKeyPairForSSL()
        val serverCert = X509Utilities.createServerCert(subjectDN, keyPair.public, caCertAndKey, listOf("alias name"), listOf("10.0.0.54"))
        assertTrue { serverCert.subjectDN.name.contains("CN=Server Cert") } // using our subject common name
        assertEquals(caCertAndKey.certificate.issuerDN, serverCert.issuerDN) // Issued by our CA cert
        serverCert.checkValidity(Date()) // throws on verification problems
        serverCert.verify(caCertAndKey.keyPair.public) // throws on verification problems
        assertFalse { serverCert.keyUsage[5] } // Bit 5 == keyCertSign according to ASN.1 spec (see full comment on KeyUsage property)
        assertTrue { serverCert.basicConstraints == -1 } // This returns the signing path length should be -1 for non-CA certificate
        assertEquals(2, serverCert.subjectAlternativeNames.size)
        var foundAliasDnsName = false
        for (entry in serverCert.subjectAlternativeNames) {
            val typeId = entry[0] as Int
            val value = entry[1] as String
            if (typeId == GeneralName.iPAddress) {
                assertEquals("10.0.0.54", value)
            } else if (value == "alias name") {
                foundAliasDnsName = true
            }
        }
        assertTrue(foundAliasDnsName)
    }

    @Test
    fun `create full CA keystore`() {
        val tmpKeyStore = tempFile("keystore.jks")
        val tmpTrustStore = tempFile("truststore.jks")

        // Generate Root and Intermediate CA cert and put both into key store and root ca cert into trust store
        X509Utilities.createCAKeyStoreAndTrustStore(tmpKeyStore, "keystorepass", "keypass", tmpTrustStore, "trustpass")

        // Load back generated root CA Cert and private key from keystore and check against copy in truststore
        val keyStore = X509Utilities.loadKeyStore(tmpKeyStore, "keystorepass")
        val trustStore = X509Utilities.loadKeyStore(tmpTrustStore, "trustpass")
        val rootCaCert = keyStore.getCertificate(X509Utilities.CORDA_ROOT_CA_PRIVATE_KEY) as X509Certificate
        val rootCaPrivateKey = keyStore.getKey(X509Utilities.CORDA_ROOT_CA_PRIVATE_KEY, "keypass".toCharArray()) as PrivateKey
        val rootCaFromTrustStore = trustStore.getCertificate(X509Utilities.CORDA_ROOT_CA) as X509Certificate
        assertEquals(rootCaCert, rootCaFromTrustStore)
        rootCaCert.checkValidity(Date())
        rootCaCert.verify(rootCaCert.publicKey)

        // Now sign something with private key and verify against certificate public key
        val testData = "12345".toByteArray()
        val caSigner = Signature.getInstance(X509Utilities.SIGNATURE_ALGORITHM)
        caSigner.initSign(rootCaPrivateKey)
        caSigner.update(testData)
        val caSignature = caSigner.sign()
        val caVerifier = Signature.getInstance(X509Utilities.SIGNATURE_ALGORITHM)
        caVerifier.initVerify(rootCaCert.publicKey)
        caVerifier.update(testData)
        assertTrue { caVerifier.verify(caSignature) }

        // Load back generated intermediate CA Cert and private key
        val intermediateCaCert = keyStore.getCertificate(X509Utilities.CORDA_INTERMEDIATE_CA_PRIVATE_KEY) as X509Certificate
        val intermediateCaCertPrivateKey = keyStore.getKey(X509Utilities.CORDA_INTERMEDIATE_CA_PRIVATE_KEY, "keypass".toCharArray()) as PrivateKey
        intermediateCaCert.checkValidity(Date())
        intermediateCaCert.verify(rootCaCert.publicKey)

        // Now sign something with private key and verify against certificate public key
        val intermediateSigner = Signature.getInstance(X509Utilities.SIGNATURE_ALGORITHM)
        intermediateSigner.initSign(intermediateCaCertPrivateKey)
        intermediateSigner.update(testData)
        val intermediateSignature = intermediateSigner.sign()
        val intermediateVerifier = Signature.getInstance(X509Utilities.SIGNATURE_ALGORITHM)
        intermediateVerifier.initVerify(intermediateCaCert.publicKey)
        intermediateVerifier.update(testData)
        assertTrue { intermediateVerifier.verify(intermediateSignature) }
    }

    @Test
    fun `create server certificate in keystore for SSL`() {
        val tmpCAKeyStore = tempFile("keystore.jks")
        val tmpTrustStore = tempFile("truststore.jks")
        val tmpServerKeyStore = tempFile("serverkeystore.jks")

        // Generate Root and Intermediate CA cert and put both into key store and root ca cert into trust store
        X509Utilities.createCAKeyStoreAndTrustStore(tmpCAKeyStore,
                "cakeystorepass",
                "cakeypass",
                tmpTrustStore,
                "trustpass")

        // Load signing intermediate CA cert
        val caKeyStore = X509Utilities.loadKeyStore(tmpCAKeyStore, "cakeystorepass")
        val caCertAndKey = X509Utilities.loadCertificateAndKey(caKeyStore, "cakeypass", X509Utilities.CORDA_INTERMEDIATE_CA_PRIVATE_KEY)

        // Generate server cert and private key and populate another keystore suitable for SSL
        X509Utilities.createKeystoreForSSL(tmpServerKeyStore, "serverstorepass", "serverkeypass", caKeyStore, "cakeypass", X500Name(MEGA_CORP.name))

        // Load back server certificate
        val serverKeyStore = X509Utilities.loadKeyStore(tmpServerKeyStore, "serverstorepass")
        val serverCertAndKey = X509Utilities.loadCertificateAndKey(serverKeyStore, "serverkeypass", X509Utilities.CORDA_CLIENT_CA_PRIVATE_KEY)

        serverCertAndKey.certificate.checkValidity(Date())
        serverCertAndKey.certificate.verify(caCertAndKey.certificate.publicKey)

        assertTrue { serverCertAndKey.certificate.subjectDN.name.contains(X500Name(MEGA_CORP.name).commonName) }

        // Now sign something with private key and verify against certificate public key
        val testData = "123456".toByteArray()
        val signer = Signature.getInstance(X509Utilities.SIGNATURE_ALGORITHM)
        signer.initSign(serverCertAndKey.keyPair.private)
        signer.update(testData)
        val signature = signer.sign()
        val verifier = Signature.getInstance(X509Utilities.SIGNATURE_ALGORITHM)
        verifier.initVerify(serverCertAndKey.certificate.publicKey)
        verifier.update(testData)
        assertTrue { verifier.verify(signature) }
    }

    @Test
    fun `create server cert and use in SSL socket`() {
        val tmpCAKeyStore = tempFile("keystore.jks")
        val tmpTrustStore = tempFile("truststore.jks")
        val tmpServerKeyStore = tempFile("serverkeystore.jks")

        // Generate Root and Intermediate CA cert and put both into key store and root ca cert into trust store
        val caKeyStore = X509Utilities.createCAKeyStoreAndTrustStore(tmpCAKeyStore,
                "cakeystorepass",
                "cakeypass",
                tmpTrustStore,
                "trustpass")

        // Generate server cert and private key and populate another keystore suitable for SSL
        val keyStore = X509Utilities.createKeystoreForSSL(tmpServerKeyStore, "serverstorepass", "serverstorepass", caKeyStore, "cakeypass", X500Name(MEGA_CORP.name))
        val trustStore = X509Utilities.loadKeyStore(tmpTrustStore, "trustpass")

        val context = SSLContext.getInstance("TLS")
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, "serverstorepass".toCharArray())
        val keyManagers = keyManagerFactory.keyManagers
        val trustMgrFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustMgrFactory.init(trustStore)
        val trustManagers = trustMgrFactory.trustManagers
        context.init(keyManagers, trustManagers, SecureRandom())

        val serverSocketFactory = context.serverSocketFactory
        val clientSocketFactory = context.socketFactory

        val serverSocket = serverSocketFactory.createServerSocket(0) as SSLServerSocket // use 0 to get first free socket
        val serverParams = SSLParameters(arrayOf("TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
                "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",
                "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
                "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
                "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256"),
                arrayOf("TLSv1.2"))
        serverParams.wantClientAuth = true
        serverParams.needClientAuth = true
        serverParams.endpointIdentificationAlgorithm = "HTTPS" // enable hostname checking
        serverSocket.sslParameters = serverParams
        serverSocket.useClientMode = false

        val clientSocket = clientSocketFactory.createSocket() as SSLSocket
        val clientParams = SSLParameters(arrayOf("TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
                "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",
                "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
                "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
                "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256"),
                arrayOf("TLSv1.2"))
        clientParams.endpointIdentificationAlgorithm = "HTTPS" // enable hostname checking
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
                assert(sslServerSocket.isConnected)
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
        val x500name = X500Name(peerX500Principal.name)
        assertEquals(X500Name(MEGA_CORP.name), x500name)


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
}

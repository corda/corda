package net.corda.services.messaging

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.toX500Name
import net.corda.coretesting.internal.configureTestSSL
import net.corda.coretesting.internal.stubs.CertificateStoreStubs
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NODE_P2P_USER
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.PEER_USER
import net.corda.nodeapi.internal.DEV_INTERMEDIATE_CA
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.nodeapi.internal.loadDevCaTrustStore
import net.corda.nodeapi.internal.registerDevP2pCertificates
import net.corda.services.messaging.SimpleAMQPClient.Companion.sendAndVerify
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.internal.createDevIntermediateCaCertPath
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration
import org.apache.activemq.artemis.api.core.ActiveMQClusterSecurityException
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import org.junit.Test
import java.nio.file.Files
import javax.jms.JMSSecurityException
import javax.security.auth.x500.X500Principal
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.test.assertEquals

/**
 * Runs the security tests with the attacker pretending to be a node on the network.
 */
class MQSecurityAsNodeTest : P2PMQSecurityTest() {
    override fun createAttacker(): SimpleMQClient {
        return clientTo(alice.node.configuration.p2pAddress)
    }

    override fun startAttacker(attacker: SimpleMQClient) {
        attacker.start(PEER_USER, PEER_USER)  // Login as a peer
    }

    @Test(timeout=300_000)
    fun `send message to RPC requests address`() {
        assertProducerQueueCreationAttackFails(RPCApi.RPC_SERVER_QUEUE_NAME)
    }

    @Test(timeout=300_000)
	fun `only the node running the broker can login using the special P2P node user`() {
        val attacker = clientTo(alice.node.configuration.p2pAddress)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start(NODE_P2P_USER, NODE_P2P_USER)
        }
    }

    @Test(timeout=300_000)
	fun `login as the default cluster user`() {
        val attacker = clientTo(alice.node.configuration.p2pAddress)
        assertThatExceptionOfType(ActiveMQClusterSecurityException::class.java).isThrownBy {
            attacker.start(ActiveMQDefaultConfiguration.getDefaultClusterUser(), ActiveMQDefaultConfiguration.getDefaultClusterPassword())
        }
    }

    @Test(timeout=300_000)
	fun `login without a username and password`() {
        val attacker = clientTo(alice.node.configuration.p2pAddress)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start()
        }
    }

    @Test(timeout=300_000)
	fun `login to a non ssl port as a node user`() {
        val attacker = clientTo(alice.node.configuration.rpcOptions.address, sslConfiguration = null)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start(NODE_P2P_USER, NODE_P2P_USER, enableSSL = false)
        }
    }

    @Test(timeout=300_000)
	fun `login to a non ssl port as a peer user`() {
        val attacker = clientTo(alice.node.configuration.rpcOptions.address, sslConfiguration = null)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start(PEER_USER, PEER_USER, enableSSL = false)  // Login as a peer
        }
    }

    @Test(timeout=300_000)
	fun `login with invalid certificate chain`() {
        val certsDir = Files.createTempDirectory("certs")
        certsDir.createDirectories()
        val signingCertStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certsDir)
        val p2pSslConfig = CertificateStoreStubs.P2P.withCertificatesDirectory(certsDir)

        val legalName = CordaX500Name("MegaCorp", "London", "GB")
        if (!p2pSslConfig.trustStore.path.exists()) {
            val trustStore = p2pSslConfig.trustStore.get(true)
            loadDevCaTrustStore().copyTo(trustStore)
        }

        val clientKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        // Set name constrain to the legal name.
        val nameConstraints = NameConstraints(arrayOf(GeneralSubtree(GeneralName(GeneralName.directoryName, legalName.toX500Name()))), arrayOf())
        val clientCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, DEV_INTERMEDIATE_CA.certificate, DEV_INTERMEDIATE_CA.keyPair, legalName.x500Principal, clientKeyPair.public, nameConstraints = nameConstraints)

        val tlsKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        // Using different x500 name in the TLS cert which is not allowed in the name constraints.
        val clientTLSCert = X509Utilities.createCertificate(CertificateType.TLS, clientCACert, clientKeyPair, CordaX500Name("MiniCorp", "London", "GB").x500Principal, tlsKeyPair.public)

        signingCertStore.get(createNew = true).update {
            setPrivateKey(X509Utilities.CORDA_CLIENT_CA, clientKeyPair.private, listOf(clientCACert, DEV_INTERMEDIATE_CA.certificate, DEV_ROOT_CA.certificate), signingCertStore.entryPassword)
        }

        p2pSslConfig.keyStore.get(createNew = true).update {
            setPrivateKey(X509Utilities.CORDA_CLIENT_TLS, tlsKeyPair.private, listOf(clientTLSCert, clientCACert, DEV_INTERMEDIATE_CA.certificate, DEV_ROOT_CA.certificate), p2pSslConfig.keyStore.entryPassword)
        }

        val attacker = clientTo(alice.node.configuration.p2pAddress, p2pSslConfig)
        assertThatExceptionOfType(ActiveMQNotConnectedException::class.java).isThrownBy {
            attacker.start(PEER_USER, PEER_USER)
        }
    }

    @Test(timeout = 300_000)
    fun `login with invalid root`() {
        val legalName = CordaX500Name("MegaCorp", "London", "GB")
        val sslConfig = configureTestSSL(legalName)
        val (rootCa, intermediateCa) = createDevIntermediateCaCertPath(X500Principal("CN=Root2"))
        sslConfig.trustStore.get()[CORDA_ROOT_CA] = rootCa.certificate
        sslConfig.keyStore.get().registerDevP2pCertificates(legalName, rootCa.certificate, intermediateCa)

        val attacker = clientTo(alice.node.configuration.p2pAddress, sslConfig)
        assertThatExceptionOfType(ActiveMQNotConnectedException::class.java).isThrownBy {
            attacker.start(PEER_USER, PEER_USER)
        }
    }

    @Test(timeout = 300_000)
    fun `login with different roots`() {
        val (rootCa2, intermediateCa2) = createDevIntermediateCaCertPath(X500Principal("CN=Root2"))
        val (rootCa3, intermediateCa3) = createDevIntermediateCaCertPath(X500Principal("CN=Root3"))

        val certificatesDirectory = baseDirectory(BOB_NAME).createDirectories() / "certificates"
        CertificateStoreStubs.P2P.TrustStore.withCertificatesDirectory(certificatesDirectory).get(true).let {
            it[CORDA_ROOT_CA] = DEV_ROOT_CA.certificate
            it["$CORDA_ROOT_CA-2"] = rootCa2.certificate
        }
        val bob = startNode(BOB_NAME)

        // Login with different trusted root.
        configureTestSSL(CHARLIE_NAME).apply {
            trustStore.get()[CORDA_ROOT_CA] = rootCa2.certificate
            trustStore.get()["$CORDA_ROOT_CA-2"] = DEV_ROOT_CA.certificate
            keyStore.get().registerDevP2pCertificates(CHARLIE_NAME, rootCa2.certificate, intermediateCa2)
            clientTo(bob.node.configuration.p2pAddress, this).start(PEER_USER, PEER_USER)
        }

        // Login with different non-trusted root.
        configureTestSSL(CHARLIE_NAME).apply {
            trustStore.get()[CORDA_ROOT_CA] = rootCa3.certificate
            trustStore.get()["$CORDA_ROOT_CA-2"] = DEV_ROOT_CA.certificate
            keyStore.get().registerDevP2pCertificates(CHARLIE_NAME, rootCa3.certificate, intermediateCa3)
            assertThatExceptionOfType(ActiveMQNotConnectedException::class.java).isThrownBy {
                clientTo(bob.node.configuration.p2pAddress, this).start(PEER_USER, PEER_USER)
            }
        }
    }

    override fun `send message to notifications address`() {
         assertProducerQueueCreationAttackFails(ArtemisMessagingComponent.NOTIFICATIONS_ADDRESS)
    }

    @Test(timeout=300_000)
    fun `send message on core protocol`() {
        val attacker = clientTo(alice.node.configuration.p2pAddress)
        attacker.start(PEER_USER, PEER_USER)
        val message = attacker.createMessage()
        assertEquals(true, attacker.producer.isBlockOnNonDurableSend)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.producer.send("${ArtemisMessagingComponent.P2P_PREFIX}${alice.info.singleIdentity().owningKey.toStringShort()}", message)
        }.withMessageContaining("CoreMessage").withMessageContaining("AMQPMessage")
    }

    @Test(timeout = 300_000)
    fun `send AMQP message with correct validated user in header`() {
        val attacker = amqpClientTo(alice.node.configuration.p2pAddress)
        val session = attacker.start(PEER_USER, PEER_USER)
        val message = session.createMessage()
        message.setStringProperty("_AMQ_VALIDATED_USER", "O=MegaCorp, L=London, C=GB")
        val queue = session.createQueue("${ArtemisMessagingComponent.P2P_PREFIX}${alice.info.singleIdentity().owningKey.toStringShort()}")
        val producer = session.createProducer(queue)
        producer.sendAndVerify(message)
    }

    @Test(timeout = 300_000)
    fun `send AMQP message with incorrect validated user in header`() {
        val attacker = amqpClientTo(alice.node.configuration.p2pAddress)
        val session = attacker.start(PEER_USER, PEER_USER)
        val message = session.createMessage()
        message.setStringProperty("_AMQ_VALIDATED_USER", "O=Bob, L=New York, C=US")
        val queue = session.createQueue("${ArtemisMessagingComponent.P2P_PREFIX}${alice.info.singleIdentity().owningKey.toStringShort()}")
        val producer = session.createProducer(queue)
        assertThatExceptionOfType(JMSSecurityException::class.java).isThrownBy {
            producer.sendAndVerify(message)
        }.withMessageContaining("_AMQ_VALIDATED_USER mismatch")
    }

    @Test(timeout = 300_000)
    fun `send AMQP message without header`() {
        val attacker = amqpClientTo(alice.node.configuration.p2pAddress)
        val session = attacker.start(PEER_USER, PEER_USER)
        val message = session.createMessage()
        val queue = session.createQueue("${ArtemisMessagingComponent.P2P_PREFIX}${alice.info.singleIdentity().owningKey.toStringShort()}")
        val producer = session.createProducer(queue)
        producer.sendAndVerify(message)
    }
}

package net.corda.bridge.services

import net.corda.bridge.createAndLoadConfigFromResource
import net.corda.bridge.createBridgeKeyStores
import net.corda.bridge.createNetworkParams
import net.corda.bridge.serverListening
import net.corda.bridge.services.receiver.BridgeAMQPListenerServiceImpl
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.Crypto.ECDSA_SECP256R1_SHA256
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.internal.readAll
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.PEERS_PREFIX
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.protonwrapper.messages.MessageStatus
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPClient
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPConfiguration
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.KeyStore
import kotlin.test.assertEquals

class AMQPListenerTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val serializationEnvironment = SerializationEnvironmentRule(true)

    @Test
    fun `Basic AMPQListenerService lifecycle test`() {
        val configResource = "/net/corda/bridge/singleprocess/firewall.conf"
        val maxMessageSize = createNetworkParams(tempFolder.root.toPath())
        val bridgeConfig = createAndLoadConfigFromResource(tempFolder.root.toPath() / "listener", configResource)
        bridgeConfig.createBridgeKeyStores(DUMMY_BANK_A_NAME)
        val auditService = TestAuditService()
        val amqpListenerService = BridgeAMQPListenerServiceImpl(bridgeConfig, maxMessageSize, auditService)
        val stateFollower = amqpListenerService.activeChange.toBlocking().iterator
        val connectionFollower = amqpListenerService.onConnection.toBlocking().iterator
        val auditFollower = auditService.onAuditEvent.toBlocking().iterator
        // Listener doesn't come up yet as not started
        assertEquals(false, stateFollower.next())
        amqpListenerService.start()
        // Listener still not up as audit not ready
        assertEquals(false, amqpListenerService.active)
        auditService.start()
        // Service 'active', but no listening activity yet
        assertEquals(true, stateFollower.next())
        assertEquals(true, amqpListenerService.active)
        assertEquals(false, serverListening("localhost", 10005))
        val keyStoreBytes = bridgeConfig.sslKeystore.readAll()
        val trustStoreBytes = bridgeConfig.trustStoreFile.readAll()
        // start listening
        amqpListenerService.provisionKeysAndActivate(keyStoreBytes,
                bridgeConfig.keyStorePassword.toCharArray(),
                bridgeConfig.keyStorePassword.toCharArray(),
                trustStoreBytes,
                bridgeConfig.trustStorePassword.toCharArray())
        // Fire lots of activity to prove we are good
        assertEquals(TestAuditService.AuditEvent.STATUS_CHANGE, auditFollower.next())
        assertEquals(true, amqpListenerService.active)
        // Definitely a socket there
        assertEquals(true, serverListening("localhost", 10005))
        // But not a valid SSL link
        assertEquals(false, connectionFollower.next().connected)
        assertEquals(TestAuditService.AuditEvent.FAILED_CONNECTION, auditFollower.next())
        val clientConfig = createAndLoadConfigFromResource(tempFolder.root.toPath() / "client", configResource)
        clientConfig.createBridgeKeyStores(DUMMY_BANK_B_NAME)
        val clientKeyStore = clientConfig.loadSslKeyStore().internal
        val clientTrustStore = clientConfig.loadTrustStore().internal
        val amqpConfig = object : AMQPConfiguration {
            override val keyStore: KeyStore = clientKeyStore
            override val keyStorePrivateKeyPassword: CharArray = clientConfig.keyStorePassword.toCharArray()
            override val trustStore: KeyStore = clientTrustStore
            override val maxMessageSize: Int = maxMessageSize
            override val trace: Boolean = true
        }
        // create and connect a real client
        val amqpClient = AMQPClient(listOf(NetworkHostAndPort("localhost", 10005)),
                setOf(DUMMY_BANK_A_NAME),
                amqpConfig)

        amqpClient.start()
        // Should see events to show we got a valid connection
        val connectedEvent = connectionFollower.next()
        assertEquals(true, connectedEvent.connected)
        assertEquals(DUMMY_BANK_B_NAME, CordaX500Name.build(connectedEvent.remoteCert!!.subjectX500Principal))
        assertEquals(TestAuditService.AuditEvent.SUCCESSFUL_CONNECTION, auditFollower.next())
        val receiver = amqpListenerService.onReceive.toBlocking().iterator
        // Send a test message
        val testMsg = "A test".toByteArray()
        val msg = amqpClient.createMessage(testMsg, "${PEERS_PREFIX}fake", DUMMY_BANK_A_NAME.toString(), emptyMap())
        amqpClient.write(msg)
        val receivedMessage = receiver.next()
        // confirm details match
        assertEquals(DUMMY_BANK_B_NAME, CordaX500Name.parse(receivedMessage.sourceLegalName))
        assertArrayEquals(testMsg, receivedMessage.payload)
        receivedMessage.complete(true)
        assertEquals(MessageStatus.Acknowledged, msg.onComplete.get())
        // Shutdown link
        amqpClient.stop()
        // verify audit events for disconnect
        val disconnectedEvent = connectionFollower.next()
        assertEquals(false, disconnectedEvent.connected)
        assertEquals(DUMMY_BANK_B_NAME, CordaX500Name.build(disconnectedEvent.remoteCert!!.subjectX500Principal))
        assertEquals(TestAuditService.AuditEvent.FAILED_CONNECTION, auditFollower.next())
        // tear down listener
        amqpListenerService.wipeKeysAndDeactivate()
        assertEquals(true, amqpListenerService.active)
        assertEquals(false, serverListening("localhost", 10005))
        amqpListenerService.stop()
        assertEquals(false, stateFollower.next())
        assertEquals(false, amqpListenerService.active)
    }


    @Test
    fun `Bad certificate audit check`() {
        val configResource = "/net/corda/bridge/singleprocess/firewall.conf"
        val maxMessageSize = createNetworkParams(tempFolder.root.toPath())
        val bridgeConfig = createAndLoadConfigFromResource(tempFolder.root.toPath() / "listener", configResource)
        bridgeConfig.createBridgeKeyStores(DUMMY_BANK_A_NAME)
        val auditService = TestAuditService()
        val amqpListenerService = BridgeAMQPListenerServiceImpl(bridgeConfig, maxMessageSize, auditService)
        amqpListenerService.start()
        auditService.start()
        val keyStoreBytes = bridgeConfig.sslKeystore.readAll()
        val trustStoreBytes = bridgeConfig.trustStoreFile.readAll()
        // start listening
        amqpListenerService.provisionKeysAndActivate(keyStoreBytes,
                bridgeConfig.keyStorePassword.toCharArray(),
                bridgeConfig.keyStorePassword.toCharArray(),
                trustStoreBytes,
                bridgeConfig.trustStorePassword.toCharArray())
        val connectionFollower = amqpListenerService.onConnection.toBlocking().iterator
        val auditFollower = auditService.onAuditEvent.toBlocking().iterator
        val clientKeys = Crypto.generateKeyPair(ECDSA_SECP256R1_SHA256)
        val clientCert = X509Utilities.createSelfSignedCACertificate(ALICE_NAME.x500Principal, clientKeys)
        val clientKeyStore = X509KeyStore("password")
        clientKeyStore.setPrivateKey("TLS_CERT", clientKeys.private, listOf(clientCert))
        val clientTrustStore = X509KeyStore("password")
        clientTrustStore.setCertificate("TLS_ROOT", clientCert)
        val amqpConfig = object : AMQPConfiguration {
            override val keyStore: KeyStore = clientKeyStore.internal
            override val keyStorePrivateKeyPassword: CharArray = "password".toCharArray()
            override val trustStore: KeyStore = clientTrustStore.internal
            override val maxMessageSize: Int = maxMessageSize
            override val trace: Boolean = true
        }
        // create and connect a real client
        val amqpClient = AMQPClient(listOf(NetworkHostAndPort("localhost", 10005)),
                setOf(DUMMY_BANK_A_NAME),
                amqpConfig)
        amqpClient.start()
        val connectionEvent = connectionFollower.next()
        assertEquals(false, connectionEvent.connected)
        assertEquals(TestAuditService.AuditEvent.FAILED_CONNECTION, auditFollower.next())
        amqpClient.stop()
        amqpListenerService.wipeKeysAndDeactivate()
        amqpListenerService.stop()
    }

}
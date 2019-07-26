package net.corda.bridge

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import com.r3.ha.utilities.BridgeSSLKeyTool
import com.r3.ha.utilities.InternalArtemisKeystoreGenerator
import com.r3.ha.utilities.InternalTunnelKeystoreGenerator
import net.corda.bridge.internal.FirewallInstance
import net.corda.bridge.services.api.FirewallConfiguration
import net.corda.bridge.services.api.FirewallMode
import net.corda.bridge.services.config.BridgeSSLConfigurationImpl
import net.corda.bridge.services.config.FirewallConfigurationImpl
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.node.services.config.EnterpriseConfiguration
import net.corda.node.services.config.MutualExclusionConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.nodeapi.internal.*
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.BRIDGE_CONTROL
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.BRIDGE_NOTIFY
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2P_PREFIX
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.PEERS_PREFIX
import net.corda.nodeapi.internal.bridging.BridgeControl
import net.corda.nodeapi.internal.bridging.BridgeEntry
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.core.*
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.stubs.CertificateStoreStubs
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import picocli.CommandLine
import java.nio.file.Path

class BridgeIntegrationDiffSslTest {

    companion object {
        private const val NODE_KEYSTORE_PASSWORD = "nodeKeyStorePassword"
        private val logger = contextLogger()
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val serializationEnvironment = SerializationEnvironmentRule(true)

    private abstract class AbstractNodeConfiguration : NodeConfiguration

    @Test
    fun `Load bridge (bridge Inner) and float outer and stand them up`() {
        val bridgeFolder = tempFolder.root.toPath()
        val bridgeConfigResource = "/net/corda/bridge/generatedwithcustomcerts/bridge/firewall.conf"
        val bridgeConfig = createAndLoadConfigFromResource(bridgeFolder, bridgeConfigResource) as FirewallConfigurationImpl

        generateInternalCerts(bridgeConfig)
        installNodesCerts(bridgeConfig)

        createNetworkParams(bridgeFolder)
        assertEquals(FirewallMode.BridgeInner, bridgeConfig.firewallMode)
        assertEquals(NetworkHostAndPort("localhost", 11005), bridgeConfig.outboundConfig!!.artemisBrokerAddress)
        val floatFolder = tempFolder.root.toPath() / "float"
        val floatConfigResource = "/net/corda/bridge/generatedwithcustomcerts/float/firewall.conf"
        val floatConfig = createAndLoadConfigFromResource(floatFolder, floatConfigResource) as FirewallConfigurationImpl

        assertEquals(FirewallMode.FloatOuter, floatConfig.firewallMode)
        assertEquals(NetworkHostAndPort("0.0.0.0", 10005), floatConfig.inboundConfig!!.listeningAddress)
        val (artemisServer, artemisClient) = createArtemis(bridgeConfig.outboundConfig!!.artemisSSLConfiguration!!)
        try {
            installBridgeControlResponder(artemisClient)
            val bridge = FirewallInstance(bridgeConfig, FirewallVersionInfo(1, "1.1", "Dummy", "Test"))
            val bridgeStateFollower = bridge.activeChange.toBlocking().iterator
            val float = FirewallInstance(floatConfig, FirewallVersionInfo(1, "1.1", "Dummy", "Test"))
            val floatStateFollower = float.activeChange.toBlocking().iterator
            assertEquals(false, floatStateFollower.next())
            float.start()
            assertEquals(true, floatStateFollower.next())
            assertEquals(true, float.active) // float is running
            assertEquals(false, serverListening("localhost", 10005)) // but not activated
            assertEquals(false, bridgeStateFollower.next())
            bridge.start()
            assertEquals(true, bridgeStateFollower.next())
            assertEquals(true, bridge.active)
            assertEquals(true, float.active)
            assertEquals(true, serverListening("localhost", 10005)) // now activated
            logger.info("Starting shutdown sequence")
            bridge.stop()
            assertEquals(false, bridgeStateFollower.next())
            assertEquals(false, bridge.active)
            assertEquals(true, float.active)
            assertEquals(false, serverListening("localhost", 10005)) // now de-activated
            float.stop()
            assertEquals(false, floatStateFollower.next())
            assertEquals(false, bridge.active)
            assertEquals(false, float.active)
        } finally {
            artemisClient.stop()
            artemisServer.stop()
        }
    }

    private fun generateInternalCerts(bridgeConfig: FirewallConfiguration) {
        val bridgeFolder = tempFolder.root.toPath()

        val tunnelGenerator = InternalTunnelKeystoreGenerator()
        with(bridgeConfig.bridgeInnerConfig!!.tunnelSSLConfiguration!!) {
            CommandLine.populateCommand(tunnelGenerator,
                    "--base-directory", bridgeFolder.toString(),
                    "--keyStorePassword", keyStore.storePassword,
                    "--entryPassword", keyStore.entryPassword,
                    "--trustStorePassword", trustStore.storePassword
            )
        }
        tunnelGenerator.runProgram()

        val artemisGenerator = InternalArtemisKeystoreGenerator()
        with(bridgeConfig.outboundConfig!!.artemisSSLConfiguration!!) {
            CommandLine.populateCommand(artemisGenerator,
                    "--base-directory", bridgeFolder.toString(),
                    "--keyStorePassword", keyStore.storePassword,
                    "--trustStorePassword", trustStore.storePassword
            )
        }
        artemisGenerator.runProgram()
    }

    private fun installNodesCerts(bridgeConfig: FirewallConfiguration) {

        val workingDirectory = tempFolder.root.toPath()

        createTLSKeystore(DUMMY_BANK_A_NAME, workingDirectory / "nodeA.jks")
        createTLSKeystore(DUMMY_BANK_B_NAME, workingDirectory / "nodeB.jks")

        val sslKeyTool = BridgeSSLKeyTool()

        CommandLine.populateCommand(sslKeyTool, "--base-directory", workingDirectory.toString(),
                "--bridge-keystore-password", NODE_KEYSTORE_PASSWORD,
                "--node-keystores", (workingDirectory / "nodeA.jks").toString(), (workingDirectory / "nodeB.jks").toString(),
                "--node-keystore-passwords", NODE_KEYSTORE_PASSWORD,
                "--bridge-keystore", bridgeConfig.sslKeystore.toString())

        sslKeyTool.runProgram()

        if (!bridgeConfig.trustStoreFile.exists()) {
            val password = bridgeConfig.publicSSLConfiguration.trustStore.storePassword
            val trustStore = CertificateStore.fromFile(bridgeConfig.trustStoreFile, password, password,true)
            loadDevCaTrustStore().copyTo(trustStore)
        }
    }

    private fun createTLSKeystore(name: CordaX500Name, path: Path) {
        // This is aligned with CryptoService.defaultTLSSignatureScheme
        val tlsSignatureScheme = X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME

        val nodeCAKey = Crypto.generateKeyPair(tlsSignatureScheme)
        val nodeCACert = X509Utilities.createCertificate(CertificateType.NODE_CA, DEV_INTERMEDIATE_CA.certificate, DEV_INTERMEDIATE_CA.keyPair, name.x500Principal, nodeCAKey.public)

        val tlsKey = Crypto.generateKeyPair(tlsSignatureScheme)
        val tlsCert = X509Utilities.createCertificate(CertificateType.TLS, nodeCACert, nodeCAKey, name.x500Principal, tlsKey.public)

        val certChain = listOf(tlsCert, nodeCACert, DEV_INTERMEDIATE_CA.certificate, DEV_ROOT_CA.certificate)

        X509KeyStore.fromFile(path, NODE_KEYSTORE_PASSWORD, createNew = true).update {
            setPrivateKey(X509Utilities.CORDA_CLIENT_TLS, tlsKey.private, certChain, NODE_KEYSTORE_PASSWORD)
        }
    }

    private fun createArtemis(artemisSSLConfiguration: BridgeSSLConfigurationImpl): Pair<ArtemisMessagingServer, ArtemisMessagingClient> {
        val baseDirectory = tempFolder.root.toPath()

        val certificatesDirectory = baseDirectory / "artemis"
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        val artemisConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDirectory).whenever(it).baseDirectory
            doReturn(ALICE_NAME).whenever(it).myLegalName
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(artemisSSLConfiguration).whenever(it).p2pSslOptions
            doReturn(NetworkHostAndPort("localhost", 11005)).whenever(it).p2pAddress
            doReturn(null).whenever(it).jmxMonitoringHttpPort
            doReturn(EnterpriseConfiguration(MutualExclusionConfiguration(false, "", 20000, 40000), externalBridge = true)).whenever(it).enterpriseConfiguration
        }
        val artemisServer = ArtemisMessagingServer(artemisConfig, NetworkHostAndPort("0.0.0.0", 11005), MAX_MESSAGE_SIZE)
        val artemisClient = ArtemisMessagingClient(artemisConfig.p2pSslOptions, NetworkHostAndPort("localhost", 11005), MAX_MESSAGE_SIZE)
        artemisServer.start()
        artemisClient.start()
        return Pair(artemisServer, artemisClient)
    }

    private fun installBridgeControlResponder(artemisClient: ArtemisMessagingClient) {
        val artemis = artemisClient.started!!
        val inboxAddress = SimpleString("${P2P_PREFIX}Test")
        val dummyOutQueue = SimpleString("${PEERS_PREFIX}12345")
        artemis.session.createQueue(inboxAddress, RoutingType.ANYCAST, inboxAddress, true)
        artemis.session.createQueue(dummyOutQueue, RoutingType.ANYCAST, dummyOutQueue, true)
        artemis.session.createQueue(BRIDGE_NOTIFY, RoutingType.ANYCAST, BRIDGE_NOTIFY, false)
        val controlConsumer = artemis.session.createConsumer(BRIDGE_NOTIFY)
        controlConsumer.setMessageHandler { msg ->
            val outEntry = listOf(BridgeEntry(dummyOutQueue.toString(), listOf(NetworkHostAndPort("localhost", 7890)), listOf(DUMMY_BANK_A_NAME), serviceAddress = false))
            val bridgeControl = BridgeControl.NodeToBridgeSnapshot(DUMMY_BANK_A_NAME.toString(), listOf(inboxAddress.toString()), outEntry)
            val controlPacket = bridgeControl.serialize(context = SerializationDefaults.P2P_CONTEXT).bytes
            val artemisMessage = artemis.session.createMessage(false)
            artemisMessage.writeBodyBufferBytes(controlPacket)
            artemis.producer.send(BRIDGE_CONTROL, artemisMessage)
            msg.acknowledge()
        }
    }
}
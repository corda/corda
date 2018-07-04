package net.corda.bridge.smoketest

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.*
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.node.services.config.CertChainPolicyConfig
import net.corda.node.services.config.EnterpriseConfiguration
import net.corda.node.services.config.MutualExclusionConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.nodeapi.internal.*
import net.corda.nodeapi.internal.bridging.BridgeControl
import net.corda.nodeapi.internal.config.NodeSSLConfiguration
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.crypto.*
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.testing.core.*
import net.corda.testing.internal.rigorousMock
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.curator.test.TestingServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.Socket
import java.nio.file.Path
import java.nio.file.Paths
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.streams.toList

class BridgeSmokeTest {
    companion object {
        val log = contextLogger()
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val serializationEnvironment = SerializationEnvironmentRule(true)

    private abstract class AbstractNodeConfiguration : NodeConfiguration

    @Before
    fun setup() {

    }

    @After
    fun cleanup() {

    }

    @Test
    fun `Run full features bridge from jar to ensure everything works`() {
        val artemisConfig = object : NodeSSLConfiguration {
            override val baseDirectory: Path = tempFolder.root.toPath()
            override val keyStorePassword: String = "cordacadevpass"
            override val trustStorePassword: String = "trustpass"
            override val crlCheckSoftFail: Boolean = true
        }
        artemisConfig.createBridgeKeyStores(DUMMY_BANK_A_NAME)
        copyBridgeResource("corda-firewall.jar")
        copyBridgeResource("firewall.conf")
        createNetworkParams(tempFolder.root.toPath())
        val (artemisServer, artemisClient) = createArtemis()
        val zkServer = TestingServer(11105, false)
        try {
            installBridgeControlResponder(artemisClient)
            zkServer.start()
            val bridge = startBridge(tempFolder.root.toPath())
            waitForBridge(bridge)
        } finally {
            zkServer.close()
            artemisClient.stop()
            artemisServer.stop()
        }
    }

    private fun copyBridgeResource(resourceName: String) {
        val testDir = tempFolder.root.toPath()
        // Find the finance jar file for the smoke tests of this module
        val bridgeJar = Paths.get("build", "resources/smokeTest/net/corda/bridge/smoketest").list {
            it.filter { resourceName in it.toString() }.toList().single()
        }
        bridgeJar.copyToDirectory(testDir)
    }

    fun createNetworkParams(baseDirectory: Path) {
        val dummyNotaryParty = TestIdentity(DUMMY_NOTARY_NAME)
        val notaryInfo = NotaryInfo(dummyNotaryParty.party, false)
        val copier = NetworkParametersCopier(NetworkParameters(
                minimumPlatformVersion = 1,
                notaries = listOf(notaryInfo),
                modifiedTime = Instant.now(),
                maxMessageSize = 10485760,
                maxTransactionSize = 40000,
                epoch = 1,
                whitelistedContractImplementations = emptyMap<String, List<AttachmentId>>()
        ), overwriteFile = true)
        copier.install(baseDirectory)
    }

    fun SSLConfiguration.createBridgeKeyStores(legalName: CordaX500Name,
                                               rootCert: X509Certificate = DEV_ROOT_CA.certificate,
                                               intermediateCa: CertificateAndKeyPair = DEV_INTERMEDIATE_CA) {

        certificatesDirectory.createDirectories()
        if (!trustStoreFile.exists()) {
            loadKeyStore(javaClass.classLoader.getResourceAsStream("certificates/${DEV_CA_TRUST_STORE_FILE}"), DEV_CA_TRUST_STORE_PASS).save(trustStoreFile, trustStorePassword)
        }

        val (nodeCaCert, nodeCaKeyPair) = createDevNodeCa(intermediateCa, legalName)

        val sslKeyStore = loadSslKeyStore(createNew = true)
        sslKeyStore.update {
            val tlsKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
            val tlsCert = X509Utilities.createCertificate(CertificateType.TLS, nodeCaCert, nodeCaKeyPair, legalName.x500Principal, tlsKeyPair.public)
            setPrivateKey(
                    X509Utilities.CORDA_CLIENT_TLS,
                    tlsKeyPair.private,
                    listOf(tlsCert, nodeCaCert, intermediateCa.certificate, rootCert))
        }
    }

    private fun startBridge(baseDirectory: Path): Process {
        val javaPath: Path = Paths.get(System.getProperty("java.home"), "bin", "java")
        val builder = ProcessBuilder()
                .command(javaPath.toString(), "-Dcapsule.log=verbose",
                        "-jar", "corda-firewall.jar")
                .directory(baseDirectory.toFile())
                .inheritIO()

        builder.environment().putAll(mapOf(
                "CAPSULE_CACHE_DIR" to (baseDirectory / "capsule").toString()
        ))

        log.info("Start bridge process in $baseDirectory")
        return builder.start()
    }

    private fun waitForBridge(process: Process) {
        var ok = false
        val executor = Executors.newSingleThreadScheduledExecutor()
        try {
            executor.scheduleWithFixedDelay({
                try {
                    if (!process.isAlive) {
                        log.error("Bridge has died.")
                        return@scheduleWithFixedDelay
                    }
                    if (!serverListening("localhost", 10005)) {
                        log.warn("Bridge not listening yet")
                        return@scheduleWithFixedDelay
                    }

                    ok = true

                    // Cancel the polling
                    executor.shutdown()
                } catch (e: Exception) {
                    log.warn("Bridge not ready yet (Error: {})", e.message)
                }
            }, 5, 1, TimeUnit.SECONDS)

            val setupOK = executor.awaitTermination(60, TimeUnit.SECONDS)
            check(setupOK && ok && process.isAlive) { "Bridge Failed to open listening port" }
        } catch (e: Exception) {
            throw e
        } finally {
            executor.shutdownNow()
            process.destroy()
        }
    }

    fun serverListening(host: String, port: Int): Boolean {
        var s: Socket? = null
        try {
            s = Socket(host, port)
            return true
        } catch (e: Exception) {
            return false
        } finally {
            try {
                s?.close()
            } catch (e: Exception) {
            }
        }
    }

    private fun createArtemis(): Pair<ArtemisMessagingServer, ArtemisMessagingClient> {
        val artemisConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(tempFolder.root.toPath()).whenever(it).baseDirectory
            doReturn(ALICE_NAME).whenever(it).myLegalName
            doReturn("trustpass").whenever(it).trustStorePassword
            doReturn("cordacadevpass").whenever(it).keyStorePassword
            doReturn(NetworkHostAndPort("localhost", 11005)).whenever(it).p2pAddress
            doReturn(null).whenever(it).jmxMonitoringHttpPort
            doReturn(emptyList<CertChainPolicyConfig>()).whenever(it).certificateChainCheckPolicies
            doReturn(EnterpriseConfiguration(MutualExclusionConfiguration(false, "", 20000, 40000), externalBridge = true)).whenever(it).enterpriseConfiguration
        }
        val artemisServer = ArtemisMessagingServer(artemisConfig, NetworkHostAndPort("0.0.0.0", 11005), MAX_MESSAGE_SIZE)
        val artemisClient = ArtemisMessagingClient(artemisConfig, NetworkHostAndPort("localhost", 11005), MAX_MESSAGE_SIZE)
        artemisServer.start()
        artemisClient.start()
        return Pair(artemisServer, artemisClient)
    }

    private fun installBridgeControlResponder(artemisClient: ArtemisMessagingClient) {
        val artemis = artemisClient.started!!
        val inboxAddress = SimpleString("${ArtemisMessagingComponent.P2P_PREFIX}Test")
        artemis.session.createQueue(inboxAddress, RoutingType.ANYCAST, inboxAddress, true)
        artemis.session.createQueue(ArtemisMessagingComponent.BRIDGE_NOTIFY, RoutingType.ANYCAST, ArtemisMessagingComponent.BRIDGE_NOTIFY, false)
        val controlConsumer = artemis.session.createConsumer(ArtemisMessagingComponent.BRIDGE_NOTIFY)
        controlConsumer.setMessageHandler { msg ->
            val bridgeControl = BridgeControl.NodeToBridgeSnapshot("Test", listOf(inboxAddress.toString()), emptyList())
            val controlPacket = bridgeControl.serialize(context = SerializationDefaults.P2P_CONTEXT).bytes
            val artemisMessage = artemis.session.createMessage(false)
            artemisMessage.writeBodyBufferBytes(controlPacket)
            artemis.producer.send(ArtemisMessagingComponent.BRIDGE_CONTROL, artemisMessage)
            msg.acknowledge()
        }
    }
}
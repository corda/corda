package net.corda.bridge

import com.r3.ha.utilities.BridgeSSLKeyTool
import com.typesafe.config.ConfigFactory
import net.corda.bridge.services.api.FirewallConfiguration
import net.corda.bridge.services.config.BridgeConfigHelper
import net.corda.bridge.services.config.parseAsFirewallConfiguration
import net.corda.core.concurrent.CordaFuture
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.flatMap
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.copyToDirectory
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.DEV_CA_KEY_STORE_PASS
import net.corda.nodeapi.internal.DEV_CA_TRUST_STORE_PASS
import net.corda.nodeapi.internal.bridging.BridgeControl
import net.corda.nodeapi.internal.bridging.BridgeEntry
import net.corda.nodeapi.internal.config.FileBasedCertificateStoreSupplier
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.loadKeyStore
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.MAX_MESSAGE_SIZE
import net.corda.testing.node.internal.*
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import picocli.CommandLine
import java.nio.file.Path
import java.nio.file.Paths

const val BRIDGE_KEYSTORE = "bridge.jks"

data class BridgeHandle(
        val baseDirectory: Path,
        val process: Process,
        val configuration: FirewallConfiguration,
        val bridgePort: Int,
        val brokerPort: Int,
        val debugPort: Int?
)

fun startBridgeProcess(bridgePath: Path, debugPort: Int?): Process {
    return ProcessUtilities.startJavaProcess(
            className = "net.corda.bridge.Firewall",
            arguments = listOf("--base-directory", bridgePath.toString()),
            jdwpPort = debugPort,
            workingDirectory = bridgePath,
            maximumHeapSize = "200m"
    )
}

// This method will copy ssl keystore from the node
fun DriverDSLImpl.startBridge(nodeName: CordaX500Name,
                              bridgePort: Int,
                              brokerPort: Int,
                              configOverrides: Map<String, Any>,
                              nodeDirectory: Path = baseDirectory(nodeName)): CordaFuture<BridgeHandle> {
    val bridgeBaseDir = Paths.get("$nodeDirectory-bridge")
    return waitAndCopyCertificateDir("$ALICE_NAME keystore creation", nodeDirectory / "certificates", bridgeBaseDir / "certificates").flatMap {
        startSingleProcessBridgeAndFloat(bridgeBaseDir, bridgePort, brokerPort, configOverrides, keystorePassword = DEV_CA_KEY_STORE_PASS, truststorePassword = DEV_CA_TRUST_STORE_PASS)
    }
}

fun DriverDSLImpl.startBridge(baseDir: Path,
                              artemisPort: Int,
                              advertisedP2PPort: Int,
                              vararg nodeSSLKeystores: Path,
                              configOverrides: Map<String, Any> = emptyMap(),
                              floatPort: Int? = null,
                              keyStorePassword: String = DEV_CA_KEY_STORE_PASS,
                              truststorePassword: String = DEV_CA_TRUST_STORE_PASS): CordaFuture<BridgeHandle> {
    val bridgePath = baseDir / "bridge"
    val artemisCertDir = baseDir / "artemis"
    val bridgeCertPath = bridgePath / "certificates"

    // Create bridge identity SSL keystore by combining nodes' SSL keystore.
    if(nodeSSLKeystores.isNotEmpty()){
        createBridgeKeystore(bridgeCertPath, *nodeSSLKeystores)
        (nodeSSLKeystores.first().parent / "truststore.jks").copyToDirectory(bridgeCertPath)
    }

    // Starting the bridge at the end, to test the NodeToBridgeSnapshot message's AMQP bridge convert to Loopback bridge code path.
    val bridge = startBridge(bridgePath, advertisedP2PPort, artemisPort, configOverrides + mapOf("sslKeystore" to (bridgeCertPath / BRIDGE_KEYSTORE).toString(),
            "keyStorePassword" to keyStorePassword,
            "trustStoreFile" to "$bridgeCertPath/truststore.jks",
            "trustStorePassword" to truststorePassword), artemisCertDir, floatPort, keyStorePassword, truststorePassword)

    val artemisSSLConfig = object : MutualSslConfiguration {
        override val useOpenSsl: Boolean = false
        override val keyStore = FileBasedCertificateStoreSupplier(artemisCertDir / ARTEMIS_KEYSTORE, keyStorePassword, keyStorePassword)
        override val trustStore = FileBasedCertificateStoreSupplier(artemisCertDir / ARTEMIS_TRUSTSTORE, truststorePassword, truststorePassword)
    }

    // Sending dummy NodeToBridgeSnapshot to fully start the bridge/float
    val artemisClient = ArtemisMessagingClient(artemisSSLConfig, NetworkHostAndPort("localhost", artemisPort), MAX_MESSAGE_SIZE)
    artemisClient.start()

    installBridgeControlResponder(artemisClient)

    return bridge.map {
        artemisClient.stop()
        it
    }
}

private fun DriverDSLImpl.startBridge(baseDirectory: Path,
                                      p2pPort: Int,
                                      brokerPort: Int,
                                      configOverrides: Map<String, Any>,
                                      artemisCertDir: Path?,
                                      floatPort: Int? = null,
                                      keystorePassword: String = DEV_CA_KEY_STORE_PASS,
                                      truststorePassword: String = DEV_CA_TRUST_STORE_PASS): CordaFuture<BridgeHandle> {
    return if (floatPort == null) {
        startSingleProcessBridgeAndFloat(baseDirectory, p2pPort, brokerPort, configOverrides, artemisCertDir, keystorePassword, truststorePassword)
    } else {
        startFloat(baseDirectory.parent / "float", p2pPort, brokerPort, floatPort, configOverrides, artemisCertDir!!, keystorePassword, truststorePassword)
        startBridge(baseDirectory, p2pPort, brokerPort, floatPort, configOverrides, artemisCertDir, keystorePassword, truststorePassword)
    }
}

private fun DriverDSLImpl.startBridge(baseDirectory: Path,
                                      p2pPort: Int,
                                      brokerPort: Int,
                                      floatPort: Int,
                                      configOverrides: Map<String, Any>,
                                      artemisCertDir: Path,
                                      keystorePassword: String,
                                      truststorePassword: String): CordaFuture<BridgeHandle> {

    val sslConfig = mapOf(
            "keyStorePassword" to keystorePassword,
            "trustStorePassword" to truststorePassword,
            "sslKeystore" to (artemisCertDir / ARTEMIS_KEYSTORE).toString(),
            "trustStoreFile" to (artemisCertDir / ARTEMIS_TRUSTSTORE).toString(),
            "crlCheckSoftFail" to true
    )

    baseDirectory.createDirectories()
    createNetworkParams(baseDirectory)
    val initialConfig = ConfigFactory.parseResources(ConfigTest::class.java, "/net/corda/bridge/withfloat/bridge/firewall.conf")
    val outboundConfig = mapOf(
            "artemisBrokerAddress" to "localhost:$brokerPort",
            "artemisSSLConfiguration" to sslConfig
    )

    val portConfig = ConfigFactory.parseMap(
            mapOf(
                    "outboundConfig" to outboundConfig,
                    "bridgeInnerConfig" to mapOf(
                            "floatAddresses" to listOf("localhost:$floatPort"),
                            "expectedCertificateSubject" to "CN=artemis, O=Corda, L=London, C=GB",
                            "tunnelSSLConfiguration" to sslConfig
                    )
            )
    )

    val config = ConfigFactory.parseMap(configOverrides).withFallback(portConfig).withFallback(initialConfig)
    writeConfig(baseDirectory, "firewall.conf", config)
    val bridgeConfig = BridgeConfigHelper.loadConfig(baseDirectory).parseAsFirewallConfiguration()
    val bridgeDebugPort = if (isDebug) debugPortAllocation.nextPort() else null

    val bridgeProcess = startBridgeProcess(baseDirectory, bridgeDebugPort)
    shutdownManager.registerProcessShutdown(bridgeProcess)
    return addressMustBeBoundFuture(executorService, NetworkHostAndPort("localhost", p2pPort)).map {
        BridgeHandle(
                baseDirectory = baseDirectory,
                process = bridgeProcess,
                configuration = bridgeConfig,
                bridgePort = p2pPort,
                brokerPort = brokerPort,
                debugPort = bridgeDebugPort
        )
    }
}

private fun DriverDSLImpl.startFloat(baseDirectory: Path,
                                     p2pPort: Int,
                                     brokerPort: Int,
                                     floatPort: Int,
                                     configOverrides: Map<String, Any>,
                                     artemisCertDir: Path,
                                     keystorePassword: String,
                                     truststorePassword: String): CordaFuture<BridgeHandle> {

    val sslConfig = mapOf(
            "keyStorePassword" to keystorePassword,
            "trustStorePassword" to truststorePassword,
            "sslKeystore" to (artemisCertDir / ARTEMIS_KEYSTORE).toString(),
            "trustStoreFile" to (artemisCertDir / ARTEMIS_TRUSTSTORE).toString(),
            "crlCheckSoftFail" to true
    )

    baseDirectory.createDirectories()
    createNetworkParams(baseDirectory)
    val initialConfig = ConfigFactory.parseResources(ConfigTest::class.java, "/net/corda/bridge/withfloat/float/firewall.conf")

    val portConfig = ConfigFactory.parseMap(
            mapOf(
                    "inboundConfig" to mapOf("listeningAddress" to "localhost:$p2pPort"),
                    "floatOuterConfig" to mapOf(
                            "floatAddress" to "localhost:$floatPort",
                            "expectedCertificateSubject" to "CN=artemis, O=Corda, L=London, C=GB",
                            "tunnelSSLConfiguration" to sslConfig
                    )
            )
    )

    val config = ConfigFactory.parseMap(configOverrides).withFallback(portConfig).withFallback(initialConfig)
    writeConfig(baseDirectory, "firewall.conf", config)
    val floatConfig = BridgeConfigHelper.loadConfig(baseDirectory).parseAsFirewallConfiguration()
    val bridgeDebugPort = if (isDebug) debugPortAllocation.nextPort() else null

    val bridgeProcess = startBridgeProcess(baseDirectory, bridgeDebugPort)
    shutdownManager.registerProcessShutdown(bridgeProcess)
    return addressMustBeBoundFuture(executorService, NetworkHostAndPort("localhost", p2pPort)).map {
        BridgeHandle(
                baseDirectory = baseDirectory,
                process = bridgeProcess,
                configuration = floatConfig,
                bridgePort = p2pPort,
                brokerPort = brokerPort,
                debugPort = bridgeDebugPort
        )
    }
}

private fun DriverDSLImpl.startSingleProcessBridgeAndFloat(baseDirectory: Path,
                                                           bridgePort: Int,
                                                           brokerPort: Int,
                                                           configOverrides: Map<String, Any>,
                                                           artemisCertDir: Path? = null,
                                                           keystorePassword: String,
                                                           truststorePassword: String): CordaFuture<BridgeHandle> {

    baseDirectory.createDirectories()
    createNetworkParams(baseDirectory)
    val initialConfig = ConfigFactory.parseResources(ConfigTest::class.java, "/net/corda/bridge/singleprocess/firewall.conf")

    val outboundConfig = if (artemisCertDir != null) {
        mapOf(
                "artemisBrokerAddress" to "localhost:$brokerPort",
                "artemisSSLConfiguration" to mapOf(
                        "keyStorePassword" to keystorePassword,
                        "trustStorePassword" to truststorePassword,
                        "sslKeystore" to (artemisCertDir / ARTEMIS_KEYSTORE).toString(),
                        "trustStoreFile" to (artemisCertDir / ARTEMIS_TRUSTSTORE).toString(),
                        "revocationConfig" to mapOf(
                                "mode" to "SOFT_FAIL"
                        )
                )
        )
    } else {
        mapOf(
                "artemisBrokerAddress" to "localhost:$brokerPort"
        )
    }

    val portConfig = ConfigFactory.parseMap(
            mapOf(
                    "outboundConfig" to outboundConfig,
                    "inboundConfig" to mapOf(
                            "listeningAddress" to "0.0.0.0:$bridgePort"
                    )
            )
    )
    val config = ConfigFactory.parseMap(configOverrides).withFallback(portConfig).withFallback(initialConfig)
    writeConfig(baseDirectory, "firewall.conf", config)
    val bridgeConfig = BridgeConfigHelper.loadConfig(baseDirectory).parseAsFirewallConfiguration()
    val bridgeDebugPort = if (isDebug) debugPortAllocation.nextPort() else null

    val bridgeProcess = startBridgeProcess(baseDirectory, bridgeDebugPort)
    shutdownManager.registerProcessShutdown(bridgeProcess)
    return addressMustBeBoundFuture(executorService, NetworkHostAndPort("localhost", bridgePort)).map {
        BridgeHandle(
                baseDirectory = baseDirectory,
                process = bridgeProcess,
                configuration = bridgeConfig,
                bridgePort = bridgePort,
                brokerPort = brokerPort,
                debugPort = bridgeDebugPort
        )
    }
}

private fun DriverDSLImpl.waitAndCopyCertificateDir(pollName: String, src: Path, dest: Path): CordaFuture<Boolean> {
    return pollUntilTrue(pollName) {
        try {
            val keyStore = loadKeyStore(src / "sslkeystore.jks", DEV_CA_KEY_STORE_PASS)
            keyStore.getCertificate(X509Utilities.CORDA_CLIENT_TLS)
            true
        } catch (throwable: Throwable) {
            false
        }
    }.map {
        src.toFile().copyRecursively(dest.toFile())
    }
}

fun DriverDSLImpl.bounceBridge(bridge: BridgeHandle) {
    bridge.process.destroyForcibly()
    val bridgeAddress = NetworkHostAndPort("localhost", bridge.bridgePort)
    addressMustNotBeBound(executorService, bridgeAddress)
    val newProcess = startBridgeProcess(bridge.baseDirectory, bridge.debugPort)
    shutdownManager.registerProcessShutdown(newProcess)
    addressMustBeBound(executorService, bridgeAddress)
}

private fun installBridgeControlResponder(artemisClient: ArtemisMessagingClient) {
    val artemis = artemisClient.started!!
    val inboxAddress = SimpleString("${ArtemisMessagingComponent.P2P_PREFIX}Test")
    val dummyOutQueue = SimpleString("${ArtemisMessagingComponent.PEERS_PREFIX}12345")
    artemis.session.createQueue(inboxAddress, RoutingType.ANYCAST, inboxAddress, true)
    artemis.session.createQueue(dummyOutQueue, RoutingType.ANYCAST, dummyOutQueue, true)
    artemis.session.createQueue(ArtemisMessagingComponent.BRIDGE_NOTIFY, RoutingType.ANYCAST, ArtemisMessagingComponent.BRIDGE_NOTIFY, false)
    val controlConsumer = artemis.session.createConsumer(ArtemisMessagingComponent.BRIDGE_NOTIFY)
    controlConsumer.setMessageHandler { msg ->
        val outEntry = listOf(BridgeEntry(dummyOutQueue.toString(), listOf(NetworkHostAndPort("localhost", 0)), listOf(DUMMY_BANK_A_NAME), serviceAddress = false))
        val bridgeControl = BridgeControl.NodeToBridgeSnapshot(DUMMY_BANK_A_NAME.toString(), listOf(inboxAddress.toString()), outEntry)
        val controlPacket = bridgeControl.serialize(context = SerializationDefaults.P2P_CONTEXT).bytes
        val artemisMessage = artemis.session.createMessage(false)
        artemisMessage.writeBodyBufferBytes(controlPacket)
        artemis.producer.send(ArtemisMessagingComponent.BRIDGE_CONTROL, artemisMessage)
        msg.acknowledge()
    }
}

private fun createBridgeKeystore(bridgeDir: Path, vararg nodeKeystores: Path, bridgeKeystorePassword: String = DEV_CA_KEY_STORE_PASS, nodeKeystorePassword: String = DEV_CA_KEY_STORE_PASS) {
    val bridgeKeytool = BridgeSSLKeyTool()
    CommandLine.populateCommand(bridgeKeytool,
            "--base-directory", bridgeDir.toString(),
            "--bridge-keystore-password", bridgeKeystorePassword,
            "--node-keystores", *nodeKeystores.map { it.toString() }.toTypedArray(),
            "--node-keystore-passwords", nodeKeystorePassword)
    bridgeKeytool.runProgram()
}
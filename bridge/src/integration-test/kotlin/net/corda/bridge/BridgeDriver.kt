package net.corda.bridge

import com.typesafe.config.ConfigFactory
import net.corda.bridge.services.api.FirewallConfiguration
import net.corda.bridge.services.config.BridgeConfigHelper
import net.corda.bridge.services.config.parseAsFirewallConfiguration
import net.corda.core.concurrent.CordaFuture
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.flatMap
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.DEV_CA_KEY_STORE_PASS
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.loadKeyStore
import net.corda.testing.node.internal.*
import java.io.File
import java.nio.file.Path


data class BridgeHandle(
        val baseDirectory: Path,
        val process: Process,
        val configuration: FirewallConfiguration,
        val bridgePort: Int,
        val brokerPort: Int,
        val debugPort: Int?
)

fun startBridgeProcess(bridgePath: Path, debugPort: Int?): Process {
    return ProcessUtilities.startCordaProcess(
            className = "net.corda.bridge.Firewall",
            arguments = listOf("--base-directory", bridgePath.toString()),
            jdwpPort = debugPort,
            extraJvmArguments = listOf(),
            workingDirectory = bridgePath,
            maximumHeapSize = "200m"
    )
}

fun DriverDSLImpl.startBridge(nodeName: CordaX500Name, bridgePort: Int, brokerPort: Int, configOverrides: Map<String, Any>): CordaFuture<BridgeHandle> {
    val nodeDirectory = baseDirectory(nodeName)
    val bridgeFolder = File("$nodeDirectory-bridge")
    bridgeFolder.mkdirs()
    createNetworkParams(bridgeFolder.toPath())
    val initialConfig = ConfigFactory.parseResources(ConfigTest::class.java, "/net/corda/bridge/singleprocess/firewall.conf")
    val portConfig = ConfigFactory.parseMap(
            mapOf(
                    "outboundConfig" to mapOf(
                            "artemisBrokerAddress" to "localhost:$brokerPort"
                    ),
                    "inboundConfig" to mapOf(
                            "listeningAddress" to "0.0.0.0:$bridgePort"
                    )
            )
    )
    val config = ConfigFactory.parseMap(configOverrides).withFallback(portConfig).withFallback(initialConfig)
    writeConfig(bridgeFolder.toPath(), "firewall.conf", config)
    val bridgeConfig = BridgeConfigHelper.loadConfig(bridgeFolder.toPath()).parseAsFirewallConfiguration()
    val nodeCertificateDirectory = nodeDirectory / "certificates"
    val bridgeDebugPort = if (isDebug) debugPortAllocation.nextPort() else null
    return pollUntilTrue("$nodeName keystore creation") {
        try {
            val keyStore = loadKeyStore(nodeCertificateDirectory / "sslkeystore.jks", DEV_CA_KEY_STORE_PASS)
            keyStore.getCertificate(X509Utilities.CORDA_CLIENT_TLS)
            true
        } catch (throwable: Throwable) {
            false
        }
    }.flatMap {
        nodeCertificateDirectory.toFile().copyRecursively(File("${bridgeFolder.absolutePath}/certificates"))

        val bridgeProcess = startBridgeProcess(bridgeFolder.toPath(), bridgeDebugPort)
        shutdownManager.registerProcessShutdown(bridgeProcess)
        addressMustBeBoundFuture(executorService, NetworkHostAndPort("localhost", bridgePort)).map {
            BridgeHandle(
                    baseDirectory = bridgeFolder.toPath(),
                    process = bridgeProcess,
                    configuration = bridgeConfig,
                    bridgePort = bridgePort,
                    brokerPort = brokerPort,
                    debugPort = bridgeDebugPort
            )
        }
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

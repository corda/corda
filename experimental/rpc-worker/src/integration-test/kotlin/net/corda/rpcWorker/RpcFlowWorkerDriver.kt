package net.corda.rpcWorker

import net.corda.bridge.FirewallVersionInfo
import net.corda.bridge.internal.FirewallInstance
import net.corda.bridge.services.api.FirewallConfiguration
import net.corda.bridge.services.api.FirewallMode
import net.corda.bridge.services.config.BridgeInboundConfigurationImpl
import net.corda.bridge.services.config.BridgeOutboundConfigurationImpl
import net.corda.bridge.services.config.FirewallConfigurationImpl
import net.corda.core.concurrent.CordaFuture
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.sign
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.flatMap
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.createDirectory
import net.corda.core.internal.div
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.flowworker.FlowWorker
import net.corda.flowworker.FlowWorkerServiceHub
import net.corda.node.internal.NetworkParametersReader
import net.corda.node.internal.artemis.ArtemisBroker
import net.corda.node.internal.security.RPCSecurityManagerImpl
import net.corda.node.services.config.*
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.node.services.network.NodeInfoWatcher
import net.corda.node.services.rpc.ArtemisRpcBroker
import net.corda.nodeapi.internal.NodeInfoAndSigned
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.getTestPartyAndCertificate
import net.corda.testing.driver.DriverParameters
import net.corda.testing.node.MockServices
import net.corda.testing.node.internal.DriverDSLImpl
import net.corda.testing.node.internal.InternalDriverDSL
import net.corda.testing.node.internal.TestCordappDirectories
import net.corda.testing.node.internal.genericDriver
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl
import org.apache.commons.io.FileUtils
import java.nio.file.Paths
import java.security.KeyPair
import java.security.cert.X509Certificate
import java.util.*

fun <A> rpcFlowWorkerDriver(
        defaultParameters: DriverParameters = DriverParameters(),
        dsl: RpcFlowWorkerDriverDSL.() -> A
): A {
    return genericDriver(
            defaultParameters = defaultParameters,
            driverDslWrapper = { driverDSL: DriverDSLImpl -> RpcFlowWorkerDriverDSL(driverDSL) },
            coerce = { it }, dsl = dsl
    )
}

data class RpcFlowWorkerHandle(val rpcAddress: NetworkHostAndPort)

data class RpcFlowWorkerDriverDSL(private val driverDSL: DriverDSLImpl) : InternalDriverDSL by driverDSL {

    fun startRpcFlowWorker(myLegalName: CordaX500Name, rpcUsers: List<net.corda.testing.node.User>, numberOfFlowWorkers: Int = 1): CordaFuture<RpcFlowWorkerHandle> {
        val (config, rpcWorkerConfig, flowWorkerConfigs, bridgeConfig) = generateConfigs(myLegalName, rpcUsers, numberOfFlowWorkers)

        val trustRoot = rpcWorkerConfig.p2pSslOptions.trustStore.get().query { getCertificate(X509Utilities.CORDA_ROOT_CA) }
        val nodeCa = rpcWorkerConfig.signingCertificateStore.get().query { getCertificate(X509Utilities.CORDA_CLIENT_CA) }

        val ourKeyPair = Crypto.generateKeyPair()
        val ourParty = Party(myLegalName, ourKeyPair.public)
        val ourPartyAndCertificate = getTestPartyAndCertificate(ourParty)
        val myInfo = NodeInfo(listOf(bridgeConfig.inboundConfig!!.listeningAddress), listOf(ourPartyAndCertificate), 4, 1)

        val nodeInfoAndSigned = NodeInfoAndSigned(myInfo) { _, serialised ->
            ourKeyPair.private.sign(serialised.bytes)
        }
        NodeInfoWatcher.saveToFile(rpcWorkerConfig.baseDirectory, nodeInfoAndSigned)

        return driverDSL.networkMapAvailability.flatMap {
            val visibilityHandle = driverDSL.networkVisibilityController.register(myLegalName)
            it!!.networkParametersCopier.install(rpcWorkerConfig.baseDirectory)
            it.nodeInfosCopier.addConfig(rpcWorkerConfig.baseDirectory)

            val signedNetworkParameters = NetworkParametersReader(trustRoot, null, rpcWorkerConfig.baseDirectory).read()

            val flowWorkerBroker = createFlowWorkerBroker(config, signedNetworkParameters.networkParameters.maxMessageSize)
            val rpcWorkerBroker = createRpcWorkerBroker(rpcWorkerConfig, signedNetworkParameters.networkParameters.maxMessageSize)

            flowWorkerConfigs.map {
                val (flowWorker, _) = createFlowWorker(it, myInfo, signedNetworkParameters.networkParameters, ourKeyPair, trustRoot, nodeCa)
                shutdownManager.registerShutdown { flowWorker.stop() }
            }

            val (rpcWorker, rpcWorkerServiceHub) = createRpcWorker(rpcWorkerConfig, myInfo, signedNetworkParameters, ourKeyPair, trustRoot, nodeCa, rpcWorkerBroker.serverControl)

            val bridge = createBridge(bridgeConfig)

            shutdownManager.registerShutdown {
                bridge.stop()
                rpcWorker.stop()
                flowWorkerBroker.stop()
                rpcWorkerBroker.stop()
            }

            visibilityHandle.listen(rpcWorkerServiceHub.rpcOps).map {
                RpcFlowWorkerHandle(rpcWorkerConfig.rpcOptions.address)
            }
        }
    }

    private data class ConfigWrapper(val config: NodeConfiguration, val rpcWorkerConfig: NodeConfiguration, val flowWorkerConfigs: List<NodeConfiguration>, val bridgeConfig: FirewallConfiguration)

    private fun generateConfigs(myLegalName: CordaX500Name, rpcUsers: List<net.corda.testing.node.User>, numberOfFlowWorkers: Int): ConfigWrapper {
        val cordappDirectories = TestCordappDirectories.cached(driverDSL.cordappsForAllNodes).toList()

        val rpcWorkerBrokerAddress = NetworkHostAndPort("localhost", driverDSL.portAllocation.nextPort())
        val rpcWorkerBrokerAdminAddress = NetworkHostAndPort("localhost", driverDSL.portAllocation.nextPort())
        val flowWorkerBrokerAddress = NetworkHostAndPort("localhost", driverDSL.portAllocation.nextPort())

        val bridgeListeningAddress = NetworkHostAndPort("localhost", driverDSL.portAllocation.nextPort())

        val baseDirectory = driverDSL.driverDirectory / myLegalName.organisation
        baseDirectory.createDirectory()

        val config = genericConfig().copy(myLegalName = myLegalName, baseDirectory = baseDirectory,
                messagingServerAddress = flowWorkerBrokerAddress, dataSourceProperties = MockServices.makeTestDataSourceProperties(),
                cordappDirectories = cordappDirectories)
        // create test certificates
        config.configureWithDevSSLCertificate()

        val rpcWorkerConfig = config.copy(baseDirectory = driverDSL.driverDirectory / myLegalName.organisation / "rpcWorker",
                rpcUsers = rpcUsers.map { User(it.username, it.password, it.permissions) },
                rpcSettings = NodeRpcSettings(rpcWorkerBrokerAddress, rpcWorkerBrokerAdminAddress, true, false, null))
        // copy over certificates to RpcWorker
        FileUtils.copyDirectory(config.certificatesDirectory.toFile(), (rpcWorkerConfig.baseDirectory / "certificates").toFile())

        val flowWorkerConfigs = (1..numberOfFlowWorkers).map {
            val flowWorkerConfig = config.copy(baseDirectory = driverDSL.driverDirectory / myLegalName.organisation / "flowWorker$it")
            // copy over certificates to FlowWorker
            FileUtils.copyDirectory(config.certificatesDirectory.toFile(), (flowWorkerConfig.baseDirectory / "certificates").toFile())

            flowWorkerConfig
        }

        val bridgeConfig = FirewallConfigurationImpl(baseDirectory = baseDirectory / "rpcWorker", crlCheckSoftFail = true,
                bridgeInnerConfig = null, keyStorePassword = "pass", trustStorePassword = "pass", firewallMode = FirewallMode.SenderReceiver,
                networkParametersPath = baseDirectory / "rpcWorker", outboundConfig = BridgeOutboundConfigurationImpl(flowWorkerBrokerAddress, listOf(), null, null),
                inboundConfig = BridgeInboundConfigurationImpl(bridgeListeningAddress, null), enableAMQPPacketTrace = false, floatOuterConfig = null, haConfig = null)

        return ConfigWrapper(config, rpcWorkerConfig, flowWorkerConfigs, bridgeConfig)
    }
}

private fun genericConfig(): NodeConfigurationImpl {
    return NodeConfigurationImpl(baseDirectory = Paths.get("."), myLegalName = DUMMY_BANK_A_NAME, emailAddress = "",
            keyStorePassword = "pass", trustStorePassword = "pass", crlCheckSoftFail = true, dataSourceProperties = Properties(),
            rpcUsers = listOf(), verifierType = VerifierType.InMemory, flowTimeout = FlowTimeoutConfiguration(5.seconds, 3, 1.0),
            p2pAddress = NetworkHostAndPort("localhost", 1), rpcSettings = NodeRpcSettings(NetworkHostAndPort("localhost", 1), null, ssl = null),
            relay = null, messagingServerAddress = null, enterpriseConfiguration = EnterpriseConfiguration(mutualExclusionConfiguration = MutualExclusionConfiguration(updateInterval = 0, waitInterval = 0), externalBridge = true),
            notary = null)
}

private fun createRpcWorkerBroker(config: NodeConfiguration, maxMessageSize: Int): ArtemisBroker {
    val rpcOptions = config.rpcOptions
    val securityManager = RPCSecurityManagerImpl(SecurityConfiguration.AuthService.fromUsers(config.rpcUsers))
    val broker = if (rpcOptions.useSsl) {
        ArtemisRpcBroker.withSsl(config.p2pSslOptions, rpcOptions.address, rpcOptions.adminAddress, rpcOptions.sslConfig!!, securityManager, maxMessageSize, false, config.baseDirectory / "artemis", false)
    } else {
        ArtemisRpcBroker.withoutSsl(config.p2pSslOptions, rpcOptions.address, rpcOptions.adminAddress, securityManager, maxMessageSize, false, config.baseDirectory / "artemis", false)
    }
    broker.start()
    return broker
}

private fun createRpcWorker(config: NodeConfiguration, myInfo: NodeInfo, signedNetworkParameters: NetworkParametersReader.NetworkParametersAndSigned, ourKeyPair: KeyPair, trustRoot: X509Certificate, nodeCa: X509Certificate, serverControl: ActiveMQServerControl): Pair<RpcWorker, RpcWorkerServiceHub> {
    val rpcWorkerServiceHub = RpcWorkerServiceHub(config, myInfo, signedNetworkParameters, ourKeyPair, trustRoot, nodeCa)
    val rpcWorker = RpcWorker(rpcWorkerServiceHub, serverControl)
    rpcWorker.start()
    return Pair(rpcWorker, rpcWorkerServiceHub)
}

private fun createFlowWorkerBroker(config: NodeConfiguration, maxMessageSize: Int): ArtemisBroker {
    val broker = ArtemisMessagingServer(config, config.messagingServerAddress!!, maxMessageSize)
    broker.start()
    return broker
}

private fun createFlowWorker(config: NodeConfiguration, myInfo: NodeInfo, networkParameters: NetworkParameters, ourKeyPair: KeyPair, trustRoot: X509Certificate, nodeCa: X509Certificate): Pair<FlowWorker, FlowWorkerServiceHub> {
    val flowWorkerServiceHub = FlowWorkerServiceHub(config, myInfo, networkParameters, ourKeyPair, trustRoot, nodeCa)
    val flowWorker = FlowWorker(UUID.randomUUID().toString(), flowWorkerServiceHub)
    flowWorker.start()
    return Pair(flowWorker, flowWorkerServiceHub)
}

private fun createBridge(firewallConfiguration: FirewallConfiguration): FirewallInstance {
    val bridge = FirewallInstance(firewallConfiguration, FirewallVersionInfo(4, "1.1", "Dummy", "Test"))
    bridge.start()
    return bridge
}
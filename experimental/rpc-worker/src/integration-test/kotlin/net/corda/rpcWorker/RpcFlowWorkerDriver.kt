package net.corda.rpcWorker

import net.corda.bridge.FirewallVersionInfo
import net.corda.bridge.internal.FirewallInstance
import net.corda.bridge.services.api.FirewallConfiguration
import net.corda.bridge.services.api.FirewallMode
import net.corda.bridge.services.config.AuditServiceConfigurationImpl
import net.corda.bridge.services.config.BridgeInboundConfigurationImpl
import net.corda.bridge.services.config.BridgeOutboundConfigurationImpl
import net.corda.bridge.services.config.FirewallConfigurationImpl
import net.corda.core.concurrent.CordaFuture
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.sign
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.concurrent.flatMap
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.concurrent.transpose
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.flowworker.FlowWorker
import net.corda.flowworker.FlowWorkerServiceHub
import net.corda.node.internal.NetworkParametersReader
import net.corda.node.internal.Node
import net.corda.node.internal.artemis.ArtemisBroker
import net.corda.node.internal.security.RPCSecurityManagerImpl
import net.corda.node.services.config.*
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.node.services.network.NodeInfoWatcher
import net.corda.node.services.rpc.ArtemisRpcBroker
import net.corda.node.utilities.EnterpriseNamedCacheFactory
import net.corda.node.utilities.profiling.getTracingConfig
import net.corda.nodeapi.internal.NodeInfoAndSigned
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.config.toConfig
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfig
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfigImpl
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.getTestPartyAndCertificate
import net.corda.testing.driver.DriverParameters
import net.corda.testing.node.MockServices
import net.corda.testing.node.internal.*
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
            driverDslWrapper = { driverDSL: DriverDSLImpl -> RpcFlowWorkerDriverDSL(driverDSL, defaultParameters) },
            coerce = { it }, dsl = dsl
    )
}

data class RpcFlowWorkerHandle(val rpcAddress: NetworkHostAndPort)

data class RpcFlowWorkerDriverDSL(private val driverDSL: DriverDSLImpl, private val driverParameters: DriverParameters) : InternalDriverDSL by driverDSL {

    fun startRpcFlowWorker(myLegalName: CordaX500Name, rpcUsers: List<net.corda.testing.node.User>, numberOfFlowWorkers: Int = 1): CordaFuture<RpcFlowWorkerHandle> {
        return startRpcFlowWorker(Collections.singleton(myLegalName), rpcUsers, numberOfFlowWorkers)
    }

    fun startRpcFlowWorker(legalNames: Set<CordaX500Name>, rpcUsers: List<net.corda.testing.node.User>, numberOfFlowWorkers: Int = 1): CordaFuture<RpcFlowWorkerHandle> {

        val rpcWorkerServiceHubsFuture = legalNames.map { myLegalName ->

            val (config, flowWorkerConfigs) = generateNodeAndFlowConfigs(myLegalName, numberOfFlowWorkers)

            val bridgeConfig = generateBridgeConfig(config)

            val trustRoot = config.p2pSslOptions.trustStore.get().query { getCertificate(X509Utilities.CORDA_ROOT_CA) }
            val nodeCa = config.signingCertificateStore.get().query { getCertificate(X509Utilities.CORDA_CLIENT_CA) }

            val ourKeyPair = Crypto.generateKeyPair()
            val ourParty = Party(myLegalName, ourKeyPair.public)
            val ourPartyAndCertificate = getTestPartyAndCertificate(ourParty)
            val myInfo = NodeInfo(listOf(bridgeConfig.inboundConfig!!.listeningAddress), listOf(ourPartyAndCertificate), PLATFORM_VERSION, 1)

            val nodeInfoAndSigned = NodeInfoAndSigned(myInfo) { _, serialised ->
                ourKeyPair.private.sign(serialised.bytes)
            }
            NodeInfoWatcher.saveToFile(config.baseDirectory, nodeInfoAndSigned)

            driverDSL.networkMapAvailability.flatMap {
                val visibilityHandle = driverDSL.networkVisibilityController.register(myLegalName)
                it!!.networkParametersCopier.install(config.baseDirectory)
                it.nodeInfosCopier.addConfig(config.baseDirectory)

                val signedNetworkParameters = NetworkParametersReader(trustRoot, null, config.baseDirectory).read()
                val maxMessageSize = signedNetworkParameters.networkParameters.maxMessageSize

                val flowWorkerBroker = createFlowWorkerBroker(config, maxMessageSize)

                val flowWorkers = flowWorkerConfigs.map {
                    val (flowWorker, _) = createFlowWorker(it, myInfo, ourKeyPair, trustRoot, nodeCa, signedNetworkParameters)
                    flowWorker
                }

                val rpcWorkerServiceHub = createRpcWorkerServiceHub(config, myInfo, signedNetworkParameters, ourKeyPair, trustRoot, nodeCa)
                rpcWorkerServiceHub.start()

                val bridge = createBridge(bridgeConfig, config as NodeConfigurationImpl)

                shutdownManager.registerShutdown {
                    // Gracefully shutdown bottom-up, i.e.: FlowWorker, RPC Worker, Brokers, Bridge
                    flowWorkers.forEach { it.stop() }
                    flowWorkerBroker.stop()
                    rpcWorkerServiceHub.stop()
                    bridge.stop()
                }

                visibilityHandle.listen(rpcWorkerServiceHub.rpcOps).map { rpcWorkerServiceHub }
            }
        }

        val rpcWorkerConfig = generateRpcWorkerConfig(rpcUsers)

        //install cordapps
        TestCordappInternal.installCordapps(
                rpcWorkerConfig.baseDirectory,
                (driverParameters.cordappsForAllNodes ?: emptySet()).mapTo(HashSet()) { it as TestCordappInternal }
        )

        val rpcWorkerFuture = rpcWorkerServiceHubsFuture.transpose().map { rpcWorkerServiceHubs ->

            val rpcWorkerBroker = createRpcWorkerBroker(rpcWorkerConfig)

            val rpcWorker = RpcWorker(rpcWorkerBroker.serverControl, rpcWorkerConfig, *rpcWorkerServiceHubs.toTypedArray()).start()
            shutdownManager.registerShutdown {
                rpcWorker.stop()
                rpcWorkerBroker.stop()
            }
            rpcWorker
        }

        return rpcWorkerFuture.map {
            RpcFlowWorkerHandle(rpcWorkerConfig.rpcOptions.address)
        }
    }

    private fun generateRpcWorkerConfig(rpcUsers: List<net.corda.testing.node.User>): NodeConfiguration {
        val rpcWorkerBrokerAddress = NetworkHostAndPort("localhost", driverDSL.portAllocation.nextPort())
        val rpcWorkerBrokerAdminAddress = NetworkHostAndPort("localhost", driverDSL.portAllocation.nextPort())

        val RPC_WORKER_LEGAL_NAME = CordaX500Name("RpcWorker", "Kiev", "UA")

        val rpcWorkerConfig = genericConfig().copy(
                myLegalName = RPC_WORKER_LEGAL_NAME,
                baseDirectory = driverDSL.driverDirectory / "rpcWorker",
                rpcUsers = rpcUsers.map { User(it.username, it.password, it.permissions) },
                rpcSettings = NodeRpcSettings(rpcWorkerBrokerAddress, rpcWorkerBrokerAdminAddress, true, false, null))
        // create test certificates
        rpcWorkerConfig.configureWithDevSSLCertificate()

        // Write config (for reference)
        writeConfig(rpcWorkerConfig.baseDirectory, "rpcWorker.conf", rpcWorkerConfig.toConfig())

        return rpcWorkerConfig
    }

    private fun generateNodeAndFlowConfigs(myLegalName: CordaX500Name, numberOfFlowWorkers: Int): Pair<NodeConfiguration, List<NodeConfiguration>> {
        val flowWorkerBrokerAddress = NetworkHostAndPort("localhost", driverDSL.portAllocation.nextPort())

        val baseDirectory = driverDSL.driverDirectory / myLegalName.organisation
        baseDirectory.createDirectories()

        val dataSourceProperties = MockServices.makeTestDataSourceProperties()
        dataSourceProperties.setProperty("maximumPoolSize", "10")

        driverDSL.cordappsForAllNodes?.let { TestCordappInternal.installCordapps(baseDirectory, it.toSet()) }
        val config = genericConfig().copy(
                myLegalName = myLegalName,
                baseDirectory = baseDirectory,
                messagingServerAddress = flowWorkerBrokerAddress,
                dataSourceProperties = dataSourceProperties,
                cordappDirectories = listOf(baseDirectory / "cordapps")
        )
        // create test certificates
        config.configureWithDevSSLCertificate()

        val flowWorkerConfigs = (1..numberOfFlowWorkers).map {
            val flowWorkerConfig = config.copy(baseDirectory = driverDSL.driverDirectory / myLegalName.organisation / "flowWorker$it")
            // copy over certificates to FlowWorker
            FileUtils.copyDirectory(config.certificatesDirectory.toFile(), (flowWorkerConfig.baseDirectory / "certificates").toFile())

            // Write config (for reference)
            writeConfig(flowWorkerConfig.baseDirectory, "flowWorker.conf", flowWorkerConfig.toConfig())
            flowWorkerConfig
        }

        // Write config (for reference)
        writeConfig(config.baseDirectory, "node.conf", config.toConfig())

        return Pair(config, flowWorkerConfigs)
    }

    private fun generateBridgeConfig(nodeConfig: NodeConfiguration): FirewallConfiguration {

        val bridgeListeningAddress = NetworkHostAndPort("localhost", driverDSL.portAllocation.nextPort())

        val baseDirectory = driverDSL.driverDirectory / "bridge_${nodeConfig.myLegalName.organisation}"

        val bridgeConfig = FirewallConfigurationImpl(baseDirectory = baseDirectory,
                bridgeInnerConfig = null, keyStorePassword = "pass", trustStorePassword = "pass", firewallMode = FirewallMode.SenderReceiver,
                networkParametersPath = baseDirectory, outboundConfig = BridgeOutboundConfigurationImpl(nodeConfig.messagingServerAddress!!, listOf(), null, null),
                inboundConfig = BridgeInboundConfigurationImpl(bridgeListeningAddress, null), enableAMQPPacketTrace = false, floatOuterConfig = null, haConfig = null,
                auditServiceConfiguration = AuditServiceConfigurationImpl(120), p2pTlsSigningCryptoServiceConfig = null, tunnelingCryptoServiceConfig = null, artemisCryptoServiceConfig = null, revocationConfig = RevocationConfigImpl(RevocationConfig.Mode.SOFT_FAIL))

        baseDirectory.createDirectories()
        // Write config (for reference)
        writeConfig(bridgeConfig.baseDirectory, "bridge.conf", bridgeConfig.toConfig())
        return bridgeConfig
    }

    private fun genericConfig(): NodeConfigurationImpl {
        return NodeConfigurationImpl(baseDirectory = Paths.get("."), myLegalName = DUMMY_BANK_A_NAME, emailAddress = "",
                keyStorePassword = "pass", trustStorePassword = "pass", crlCheckSoftFail = true, dataSourceProperties = Properties(),
                rpcUsers = listOf(), verifierType = VerifierType.InMemory, flowTimeout = FlowTimeoutConfiguration(5.seconds, 3, 1.0),
                p2pAddress = NetworkHostAndPort("localhost", 1), rpcSettings = NodeRpcSettings(NetworkHostAndPort("localhost", 1), null, ssl = null),
                relay = null, messagingServerAddress = null, enterpriseConfiguration = EnterpriseConfiguration(mutualExclusionConfiguration = MutualExclusionConfiguration(updateInterval = 0, waitInterval = 0), externalBridge = true),
                database = DatabaseConfig(runMigration = true),
                notary = null, flowOverrides = FlowOverrideConfig(listOf()))
    }

    private fun createRpcWorkerBroker(config: NodeConfiguration): ArtemisBroker {
        val rpcOptions = config.rpcOptions
        val securityManager = RPCSecurityManagerImpl(SecurityConfiguration.AuthService.fromUsers(config.rpcUsers), EnterpriseNamedCacheFactory(config.enterpriseConfiguration.getTracingConfig()))
        val broker = if (rpcOptions.useSsl) {
            ArtemisRpcBroker.withSsl(config.p2pSslOptions, rpcOptions.address, rpcOptions.adminAddress, rpcOptions.sslConfig!!, securityManager,
                    Node.MAX_RPC_MESSAGE_SIZE, false, config.baseDirectory / "artemis", false)
        } else {
            ArtemisRpcBroker.withoutSsl(config.p2pSslOptions, rpcOptions.address, rpcOptions.adminAddress, securityManager,
                    Node.MAX_RPC_MESSAGE_SIZE, false, config.baseDirectory / "artemis", false)
        }
        broker.start()
        return broker
    }

    private fun createRpcWorkerServiceHub(config: NodeConfiguration, myInfo: NodeInfo, signedNetworkParameters: NetworkParametersReader.NetworkParametersAndSigned, ourKeyPair: KeyPair, trustRoot: X509Certificate, nodeCa: X509Certificate): RpcWorkerServiceHub {
        return RpcWorkerServiceHub(config, myInfo, signedNetworkParameters, ourKeyPair, trustRoot, nodeCa)
    }

    private fun createFlowWorkerBroker(config: NodeConfiguration, maxMessageSize: Int): ArtemisBroker {
        val broker = ArtemisMessagingServer(config, config.messagingServerAddress!!, maxMessageSize)
        broker.start()
        return broker
    }

    private fun createFlowWorker(config: NodeConfiguration, myInfo: NodeInfo, ourKeyPair: KeyPair,
                                 trustRoot: X509Certificate, nodeCa: X509Certificate, signedNetworkParameters: NetworkParametersReader.NetworkParametersAndSigned): Pair<FlowWorker, FlowWorkerServiceHub> {
        val flowWorkerServiceHub = FlowWorkerServiceHub(config, myInfo, ourKeyPair, trustRoot, nodeCa, signedNetworkParameters)
        val flowWorker = FlowWorker(UUID.randomUUID().toString(), flowWorkerServiceHub)
        flowWorker.start()
        return Pair(flowWorker, flowWorkerServiceHub)
    }

    private fun createBridge(firewallConfiguration: FirewallConfiguration, config: NodeConfigurationImpl): FirewallInstance {
        // Copy key stores
        val certificatesTarget = firewallConfiguration.baseDirectory / "certificates"
        FileUtils.copyDirectory(config.certificatesDirectory.toFile(), certificatesTarget.toFile())
        // Install network parameters
        NetworkParametersCopier(driverDSL.networkParameters).install(firewallConfiguration.baseDirectory)

        val bridge = FirewallInstance(firewallConfiguration, FirewallVersionInfo(PLATFORM_VERSION, "1.1", "Dummy", "Test"))
        bridge.start()
        return bridge
    }
}
package net.corda.rpcWorker

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.node.NodeInfo
import net.corda.node.internal.NetworkParametersReader
import net.corda.node.internal.Node
import net.corda.node.internal.artemis.ArtemisBroker
import net.corda.node.internal.security.RPCSecurityManagerImpl
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.SecurityConfiguration
import net.corda.node.services.messaging.InternalRPCMessagingClient
import net.corda.node.services.messaging.RPCServerConfiguration
import net.corda.node.services.rpc.ArtemisRpcBroker
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl
import picocli.CommandLine
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.security.KeyPair
import java.security.cert.X509Certificate
import java.util.*
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val main = Main()
    try {
        CommandLine.run(main, *args)
    } catch (e: CommandLine.ExecutionException) {
        val throwable = e.cause ?: e
        if (main.verbose) {
            throwable.printStackTrace()
        } else {
            System.err.println("ERROR: ${throwable.message
                    ?: ""}. Please use '--verbose' option to obtain more details.")
        }
        exitProcess(1)
    }
}

@CommandLine.Command(
        name = "RPC Worker",
        mixinStandardHelpOptions = true,
        showDefaultValues = true,
        description = ["Standalone RPC server endpoint with pluggable set of operations."]
)
class Main : Runnable {
    @CommandLine.Option(
            names = ["--conf"],
            description = [
                "Configuration file to be used for starting RpcWorker."
            ],
            required = true
    )
    private var confFile: String? = null

    @CommandLine.Option(names = ["--verbose"], description = ["Enable verbose output."])
    var verbose: Boolean = false

    override fun run() {
        if (verbose) {
            System.setProperty("logLevel", "trace")
        }

        val parseOptions = ConfigParseOptions.defaults().setAllowMissing(false)
        val config = ConfigFactory.parseFile(File(confFile), parseOptions)

        val port = config.getInt("port")
        val user = User(config.getString("userName"), config.getString("password"), emptySet())
        val artemisDir = FileSystems.getDefault().getPath(config.getString("artemisDir"))

        val rpcWorkerConfig = getRpcWorkerConfig(port, user, artemisDir)
        val ourKeyPair = getIdentity()
        val myInfo = getNodeInfo()

        val trustRoot = rpcWorkerConfig.loadTrustStore().getCertificate(X509Utilities.CORDA_ROOT_CA)
        val nodeCa = rpcWorkerConfig.loadNodeKeyStore().getCertificate(X509Utilities.CORDA_CLIENT_CA)

        val signedNetworkParameters = NetworkParametersReader(trustRoot, null, rpcWorkerConfig.baseDirectory).read()
        val rpcWorkerBroker = createRpcWorkerBroker(rpcWorkerConfig, signedNetworkParameters.networkParameters.maxMessageSize)
        createRpcWorker(rpcWorkerConfig, myInfo, signedNetworkParameters, ourKeyPair, trustRoot, nodeCa, rpcWorkerBroker.serverControl)
    }

    private fun getRpcWorkerConfig(port: Int, user: User, artemisDir: Path): NodeConfiguration {
        TODO("" + port + user + artemisDir)
    }

    private fun getIdentity(): KeyPair {
        TODO()
    }

    private fun getNodeInfo(): NodeInfo {
        TODO()
    }

    private fun createRpcWorkerBroker(config: NodeConfiguration, maxMessageSize: Int): ArtemisBroker {
        val rpcOptions = config.rpcOptions
        val securityManager = RPCSecurityManagerImpl(SecurityConfiguration.AuthService.fromUsers(config.rpcUsers))
        val broker = if (rpcOptions.useSsl) {
            ArtemisRpcBroker.withSsl(config, rpcOptions.address, rpcOptions.adminAddress, rpcOptions.sslConfig!!, securityManager, maxMessageSize, false, config.baseDirectory / "artemis", false)
        } else {
            ArtemisRpcBroker.withoutSsl(config, rpcOptions.address, rpcOptions.adminAddress, securityManager, maxMessageSize, false, config.baseDirectory / "artemis", false)
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
}

class RpcWorker(private val rpcWorkerServiceHub: RpcWorkerServiceHub, private val serverControl: ActiveMQServerControl) {

    private val runOnStop = ArrayList<() -> Any?>()

    fun start() {
        val rpcServerConfiguration = RPCServerConfiguration.DEFAULT.copy(
                rpcThreadPoolSize = rpcWorkerServiceHub.configuration.enterpriseConfiguration.tuning.rpcThreadPoolSize
        )
        val securityManager = RPCSecurityManagerImpl(SecurityConfiguration.AuthService.fromUsers(rpcWorkerServiceHub.configuration.rpcUsers))
        val nodeName = CordaX500Name.build(rpcWorkerServiceHub.configuration.loadSslKeyStore().getCertificate(X509Utilities.CORDA_CLIENT_TLS).subjectX500Principal)

        val internalRpcMessagingClient = InternalRPCMessagingClient(rpcWorkerServiceHub.configuration, rpcWorkerServiceHub.configuration.rpcOptions.adminAddress, Node.MAX_RPC_MESSAGE_SIZE, nodeName, rpcServerConfiguration)
        internalRpcMessagingClient.init(rpcWorkerServiceHub.rpcOps, securityManager)
        internalRpcMessagingClient.start(serverControl)

        runOnStop += { rpcWorkerServiceHub.stop() }
        rpcWorkerServiceHub.start()

        runOnStop += { internalRpcMessagingClient.stop() }
    }

    fun stop() {
        for (toRun in runOnStop.reversed()) {
            toRun()
        }
        runOnStop.clear()
    }
}
package net.corda.rpcWorker

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.AuthServiceId
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.isAbstractClass
import net.corda.core.messaging.RPCOps
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.*
import net.corda.node.internal.Startable
import net.corda.node.internal.Stoppable
import net.corda.node.internal.security.RPCSecurityManagerImpl
import net.corda.node.serialization.amqp.AMQPServerSerializationScheme
import net.corda.node.services.messaging.RPCServer
import net.corda.node.services.messaging.RPCServerConfiguration
import net.corda.nodeapi.ArtemisTcpTransport
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.config.User
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.SerializationFactoryImpl
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.api.core.client.ActiveMQClient
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl
import org.apache.activemq.artemis.core.config.Configuration
import org.apache.activemq.artemis.core.config.CoreQueueConfiguration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.security.CheckType
import org.apache.activemq.artemis.core.security.Role
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy
import org.apache.activemq.artemis.core.settings.impl.AddressSettings
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager3
import picocli.CommandLine
import java.io.File
import java.lang.IllegalArgumentException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.Executors
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
            System.err.println("ERROR: ${throwable.message ?: ""}. Please use '--verbose' option to obtain more details.")
        }
        exitProcess(1)
    }
}

@CommandLine.Command(
        name = "RPC Worker",
        mixinStandardHelpOptions = true,
        showDefaultValues = true,
        description = [ "Standalone RPC server endpoint with pluggable set of operations." ]
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
        val rpcOps = instantiateAndValidate(config.getString("rpcOpsImplClass"))
        val artemisDir = FileSystems.getDefault().getPath(config.getString("artemisDir"))

        initialiseSerialization()
        RpcWorker(NetworkHostAndPort("localhost", port), user, rpcOps, artemisDir).start()
    }

    private fun instantiateAndValidate(rpcOpsImplClassName: String): RPCOps {
        try {
            val klass = Class.forName(rpcOpsImplClassName)
            if (klass.isAbstractClass) {
                throw IllegalArgumentException("$rpcOpsImplClassName must not be abstract")
            }
            val instance = klass.newInstance()
            return instance as? RPCOps ?: throw IllegalArgumentException("class '$rpcOpsImplClassName' is not extending RPCOps")
        } catch (ex: ClassNotFoundException) {
            throw IllegalArgumentException("class '$rpcOpsImplClassName' not found in the classpath")
        }
    }

    private fun initialiseSerialization() {
        synchronized(this) {
            if (nodeSerializationEnv == null) {
                val classloader = this::class.java.classLoader
                nodeSerializationEnv = SerializationEnvironmentImpl(
                        SerializationFactoryImpl().apply {
                            registerScheme(AMQPServerSerializationScheme(emptyList()))
                        },
                        p2pContext = AMQP_P2P_CONTEXT.withClassLoader(classloader),
                        rpcServerContext = AMQP_P2P_CONTEXT.withClassLoader(classloader)
                )
            }
        }
    }
}

/**
 * Note once `stop()` been called, there is no longer an option to call `start()` and the instance should be discarded
 */
class RpcWorker(private val hostAndPort: NetworkHostAndPort, private val user: User, private val ops: RPCOps, private val artemisPath: Path) : Startable, Stoppable {

    private companion object {
        const val MAX_MESSAGE_SIZE: Int = 10485760
        const val notificationAddress = "notifications"
        private val fakeNodeLegalName = CordaX500Name(organisation = "Not:a:real:name", locality = "Nowhere", country = "GB")
        private val DEFAULT_TIMEOUT = 60.seconds

        private val logger = contextLogger()
    }

    private val executorService = Executors.newScheduledThreadPool(2, ThreadFactoryBuilder().setNameFormat("RpcWorker-pool-thread-%d").build())
    private val registeredShutdowns = mutableListOf(doneFuture({executorService.shutdown()}))

    override var started = false

    override fun start() {
        started = true

        startRpcServer().getOrThrow(DEFAULT_TIMEOUT)
    }

    override fun stop() {
        val shutdownOutcomes = registeredShutdowns.map { Try.on { it.getOrThrow(DEFAULT_TIMEOUT) } }
        shutdownOutcomes.reversed().forEach {
            when (it) {
                is Try.Success ->
                    try {
                        it.value()
                    } catch (t: Throwable) {
                        logger.warn("Exception while calling a shutdown action, this might create resource leaks", t)
                    }
                is Try.Failure -> logger.warn("Exception while getting shutdown method, disregarding", it.exception)
            }
        }

        started = false
    }

    private fun startRpcServer(): CordaFuture<RPCServer> {
        return startRpcBroker().map { serverControl ->
            startRpcServerWithBrokerRunning(serverControl = serverControl)
        }
    }

    private fun startRpcBroker(
            maxFileSize: Int = MAX_MESSAGE_SIZE,
            maxBufferedBytesPerClient: Long = 10L * MAX_MESSAGE_SIZE
    ): CordaFuture<ActiveMQServerControl> {
        return executorService.fork {
            logger.info("Artemis files will be stored in: $artemisPath")
            val artemisConfig = createRpcServerArtemisConfig(maxFileSize, maxBufferedBytesPerClient, artemisPath, hostAndPort)
            val server = ActiveMQServerImpl(artemisConfig, SingleUserSecurityManager(user))
            server.start()
            registeredShutdowns.add(doneFuture({
                server.stop()
            }))
            server.activeMQServerControl
        }
    }

    private fun createNettyClientTransportConfiguration(): TransportConfiguration {
        return ArtemisTcpTransport.rpcConnectorTcpTransport(hostAndPort, null)
    }

    private fun startRpcServerWithBrokerRunning(
            nodeLegalName: CordaX500Name = fakeNodeLegalName,
            configuration: RPCServerConfiguration = RPCServerConfiguration.DEFAULT,
            serverControl: ActiveMQServerControl
    ): RPCServer {
        val locator = ActiveMQClient.createServerLocatorWithoutHA(createNettyClientTransportConfiguration()).apply {
            minLargeMessageSize = MAX_MESSAGE_SIZE
            isUseGlobalPools = false
        }
        val rpcSecurityManager = RPCSecurityManagerImpl.fromUserList(users = listOf(User(user.username, user.password, user.permissions)),
                id = AuthServiceId("RPC_WORKER_SECURITY_MANAGER"))
        val rpcServer = RPCServer(
                ops,
                user.username,
                user.password,
                locator,
                rpcSecurityManager,
                nodeLegalName,
                configuration
        )
        registeredShutdowns.add(doneFuture({
            rpcServer.close()
            locator.close()
        }))
        rpcServer.start(serverControl)
        return rpcServer
    }

    private fun createRpcServerArtemisConfig(maxFileSize: Int, maxBufferedBytesPerClient: Long, baseDirectory: Path, hostAndPort: NetworkHostAndPort): Configuration {
        return ConfigurationImpl().apply {
            val artemisDir = "$baseDirectory/artemis"
            bindingsDirectory = "$artemisDir/bindings"
            journalDirectory = "$artemisDir/journal"
            largeMessagesDirectory = "$artemisDir/large-messages"
            acceptorConfigurations = setOf(ArtemisTcpTransport.rpcAcceptorTcpTransport(hostAndPort, null))
            configureCommonSettings(maxFileSize, maxBufferedBytesPerClient)
        }
    }

    private fun ConfigurationImpl.configureCommonSettings(maxFileSize: Int, maxBufferedBytesPerClient: Long) {
        managementNotificationAddress = SimpleString(notificationAddress)
        isPopulateValidatedUser = true
        journalBufferSize_NIO = maxFileSize
        journalBufferSize_AIO = maxFileSize
        journalFileSize = maxFileSize
        queueConfigurations = listOf(
                CoreQueueConfiguration().apply {
                    name = RPCApi.RPC_SERVER_QUEUE_NAME
                    address = RPCApi.RPC_SERVER_QUEUE_NAME
                    isDurable = false
                },
                CoreQueueConfiguration().apply {
                    name = RPCApi.RPC_CLIENT_BINDING_REMOVALS
                    address = notificationAddress
                    filterString = RPCApi.RPC_CLIENT_BINDING_REMOVAL_FILTER_EXPRESSION
                    isDurable = false
                },
                CoreQueueConfiguration().apply {
                    name = RPCApi.RPC_CLIENT_BINDING_ADDITIONS
                    address = notificationAddress
                    filterString = RPCApi.RPC_CLIENT_BINDING_ADDITION_FILTER_EXPRESSION
                    isDurable = false
                }
        )
        addressesSettings = mapOf(
                "${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.#" to AddressSettings().apply {
                    maxSizeBytes = maxBufferedBytesPerClient
                    addressFullMessagePolicy = AddressFullMessagePolicy.FAIL
                }
        )
    }
}

private class SingleUserSecurityManager(val rpcUser: User) : ActiveMQSecurityManager3 {
    override fun validateUser(user: String?, password: String?) = isValid(user, password)
    override fun validateUserAndRole(user: String?, password: String?, roles: MutableSet<Role>?, checkType: CheckType?) = isValid(user, password)
    override fun validateUser(user: String?, password: String?, connection: RemotingConnection?): String? {
        return validate(user, password)
    }

    override fun validateUserAndRole(user: String?, password: String?, roles: MutableSet<Role>?, checkType: CheckType?, address: String?, connection: RemotingConnection?): String? {
        return validate(user, password)
    }

    private fun isValid(user: String?, password: String?): Boolean {
        return rpcUser.username == user && rpcUser.password == password
    }

    private fun validate(user: String?, password: String?): String? {
        return if (isValid(user, password)) user else null
    }
}
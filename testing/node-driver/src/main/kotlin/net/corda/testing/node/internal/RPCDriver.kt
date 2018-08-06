/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.node.internal

import net.corda.client.mock.Generator
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.internal.RPCClient
import net.corda.client.rpc.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.AuthServiceId
import net.corda.core.context.Trace
import net.corda.core.crypto.random63BitValue
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.div
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.RPCOps
import net.corda.core.node.NetworkParameters
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.node.internal.security.RPCSecurityManagerImpl
import net.corda.node.services.messaging.RPCServer
import net.corda.node.services.messaging.RPCServerConfiguration
import net.corda.nodeapi.ArtemisTcpTransport
import net.corda.nodeapi.RPCApi
import net.corda.serialization.internal.AMQP_RPC_CLIENT_CONTEXT
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.MAX_MESSAGE_SIZE
import net.corda.testing.driver.JmxPolicy
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.TestCorDapp
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import net.corda.testing.node.internal.DriverDSLImpl.Companion.cordappsInCurrentAndAdditionalPackages
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.api.core.client.ActiveMQClient
import org.apache.activemq.artemis.api.core.client.ActiveMQClient.DEFAULT_ACK_BATCH_SIZE
import org.apache.activemq.artemis.api.core.client.ClientSession
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl
import org.apache.activemq.artemis.core.config.Configuration
import org.apache.activemq.artemis.core.config.CoreQueueConfiguration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory
import org.apache.activemq.artemis.core.security.CheckType
import org.apache.activemq.artemis.core.security.Role
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy
import org.apache.activemq.artemis.core.settings.impl.AddressSettings
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager3
import java.lang.reflect.Method
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.*
import net.corda.nodeapi.internal.config.User as InternalUser

inline fun <reified I : RPCOps> RPCDriverDSL.startInVmRpcClient(
        username: String = rpcTestUser.username,
        password: String = rpcTestUser.password,
        configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT
) = startInVmRpcClient(I::class.java, username, password, configuration)

inline fun <reified I : RPCOps> RPCDriverDSL.startRandomRpcClient(
        hostAndPort: NetworkHostAndPort,
        username: String = rpcTestUser.username,
        password: String = rpcTestUser.password
) = startRandomRpcClient(I::class.java, hostAndPort, username, password)

inline fun <reified I : RPCOps> RPCDriverDSL.startRpcClient(
        rpcAddress: NetworkHostAndPort,
        username: String = rpcTestUser.username,
        password: String = rpcTestUser.password,
        configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT
) = startRpcClient(I::class.java, rpcAddress, username, password, configuration)

inline fun <reified I : RPCOps> RPCDriverDSL.startRpcClient(
        haAddressPool: List<NetworkHostAndPort>,
        username: String = rpcTestUser.username,
        password: String = rpcTestUser.password,
        configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT
) = startRpcClient(I::class.java, haAddressPool, username, password, configuration)

data class RpcBrokerHandle(
        val hostAndPort: NetworkHostAndPort?,
        /** null if this is an InVM broker */
        val clientTransportConfiguration: TransportConfiguration,
        val serverControl: ActiveMQServerControl
)

data class RpcServerHandle(
        val broker: RpcBrokerHandle,
        val rpcServer: RPCServer
)

val rpcTestUser = User("user1", "test", permissions = emptySet())
val fakeNodeLegalName = CordaX500Name(organisation = "Not:a:real:name", locality = "Nowhere", country = "GB")

// Use a global pool so that we can run RPC tests in parallel
private val globalPortAllocation = PortAllocation.Incremental(10000)
private val globalDebugPortAllocation = PortAllocation.Incremental(5005)

fun <A> rpcDriver(
        isDebug: Boolean = false,
        driverDirectory: Path = Paths.get("build", getTimestampAsDirectoryName()),
        portAllocation: PortAllocation = globalPortAllocation,
        debugPortAllocation: PortAllocation = globalDebugPortAllocation,
        systemProperties: Map<String, String> = emptyMap(),
        useTestClock: Boolean = false,
        startNodesInProcess: Boolean = false,
        waitForNodesToFinish: Boolean = false,
        notarySpecs: List<NotarySpec> = emptyList(),
        externalTrace: Trace? = null,
        jmxPolicy: JmxPolicy = JmxPolicy(),
        networkParameters: NetworkParameters = testNetworkParameters(notaries = emptyList()),
        notaryCustomOverrides: Map<String, Any?> = emptyMap(),
        inMemoryDB: Boolean = true,
        cordappsForAllNodes: Set<TestCorDapp> = cordappsInCurrentAndAdditionalPackages(),
        dsl: RPCDriverDSL.() -> A
): A {
    return genericDriver(
            driverDsl = RPCDriverDSL(
                    DriverDSLImpl(
                            portAllocation = portAllocation,
                            debugPortAllocation = debugPortAllocation,
                            systemProperties = systemProperties,
                            driverDirectory = driverDirectory.toAbsolutePath(),
                            useTestClock = useTestClock,
                            isDebug = isDebug,
                            startNodesInProcess = startNodesInProcess,
                            waitForAllNodesToFinish = waitForNodesToFinish,
                            notarySpecs = notarySpecs,
                            jmxPolicy = jmxPolicy,
                            compatibilityZone = null,
                            networkParameters = networkParameters,
                            notaryCustomOverrides = notaryCustomOverrides,
                            inMemoryDB = inMemoryDB,
                            cordappsForAllNodes = cordappsForAllNodes
                    ), externalTrace
            ),
            coerce = { it },
            dsl = dsl,
            initialiseSerialization = false
    )
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

data class RPCDriverDSL(
        private val driverDSL: DriverDSLImpl, private val externalTrace: Trace?
) : InternalDriverDSL by driverDSL {
    private companion object {
        const val notificationAddress = "notifications"

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

        fun createInVmRpcServerArtemisConfig(maxFileSize: Int, maxBufferedBytesPerClient: Long): Configuration {
            return ConfigurationImpl().apply {
                acceptorConfigurations = setOf(TransportConfiguration(InVMAcceptorFactory::class.java.name))
                isPersistenceEnabled = false
                configureCommonSettings(maxFileSize, maxBufferedBytesPerClient)
            }
        }

        fun createRpcServerArtemisConfig(maxFileSize: Int, maxBufferedBytesPerClient: Long, baseDirectory: Path, hostAndPort: NetworkHostAndPort): Configuration {
            return ConfigurationImpl().apply {
                val artemisDir = "$baseDirectory/artemis"
                bindingsDirectory = "$artemisDir/bindings"
                journalDirectory = "$artemisDir/journal"
                largeMessagesDirectory = "$artemisDir/large-messages"
                acceptorConfigurations = setOf(ArtemisTcpTransport.rpcAcceptorTcpTransport(hostAndPort, null))
                configureCommonSettings(maxFileSize, maxBufferedBytesPerClient)
            }
        }

        val inVmClientTransportConfiguration = TransportConfiguration(InVMConnectorFactory::class.java.name)
        fun createNettyClientTransportConfiguration(hostAndPort: NetworkHostAndPort): TransportConfiguration {
            return ArtemisTcpTransport.rpcConnectorTcpTransport(hostAndPort, null)
        }
    }

    /**
     * Starts an In-VM RPC server. Note that only a single one may be started.
     *
     * @param rpcUser The single user who can access the server through RPC, and their permissions.
     * @param nodeLegalName The legal name of the node to check against to authenticate a super user.
     * @param configuration The RPC server configuration.
     * @param ops The server-side implementation of the RPC interface.
     */
    fun <I : RPCOps> startInVmRpcServer(
            rpcUser: User = rpcTestUser,
            nodeLegalName: CordaX500Name = fakeNodeLegalName,
            maxFileSize: Int = MAX_MESSAGE_SIZE,
            maxBufferedBytesPerClient: Long = 10L * MAX_MESSAGE_SIZE,
            configuration: RPCServerConfiguration = RPCServerConfiguration.DEFAULT,
            ops: I,
            queueDrainTimeout: Duration = 5.seconds
    ): CordaFuture<RpcServerHandle> {
        return startInVmRpcBroker(rpcUser, maxFileSize, maxBufferedBytesPerClient).map { broker ->
            startRpcServerWithBrokerRunning(rpcUser, nodeLegalName, configuration, ops, broker, queueDrainTimeout)
        }
    }

    /**
     * Starts an In-VM RPC client.
     *
     * @param rpcOpsClass The [Class] of the RPC interface.
     * @param username The username to authenticate with.
     * @param password The password to authenticate with.
     * @param configuration The RPC client configuration.
     */
    fun <I : RPCOps> startInVmRpcClient(
            rpcOpsClass: Class<I>,
            username: String = rpcTestUser.username,
            password: String = rpcTestUser.password,
            configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT
    ): CordaFuture<I> {
        return driverDSL.executorService.fork {
            val client = RPCClient<I>(inVmClientTransportConfiguration, configuration)
            val connection = client.start(rpcOpsClass, username, password, externalTrace)
            driverDSL.shutdownManager.registerShutdown {
                connection.close()
            }
            connection.proxy
        }
    }

    /**
     * Starts an In-VM Artemis session connecting to the RPC server.
     *
     * @param username The username to authenticate with.
     * @param password The password to authenticate with.
     */
    fun startInVmArtemisSession(
            username: String = rpcTestUser.username,
            password: String = rpcTestUser.password
    ): ClientSession {
        val locator = ActiveMQClient.createServerLocatorWithoutHA(inVmClientTransportConfiguration)
        val sessionFactory = locator.createSessionFactory()
        val session = sessionFactory.createSession(username, password, false, true, true, locator.isPreAcknowledge, DEFAULT_ACK_BATCH_SIZE)
        driverDSL.shutdownManager.registerShutdown {
            session.close()
            sessionFactory.close()
            locator.close()
        }
        return session
    }

    /**
     * Starts a Netty RPC server.
     *
     * @param serverName The name of the server, to be used for the folder created for Artemis files.
     * @param rpcUser The single user who can access the server through RPC, and their permissions.
     * @param nodeLegalName The legal name of the node to check against to authenticate a super user.
     * @param configuration The RPC server configuration.
     * @param ops The server-side implementation of the RPC interface.
     */
    fun <I : RPCOps> startRpcServer(
            serverName: String = "driver-rpc-server-${random63BitValue()}",
            rpcUser: User = rpcTestUser,
            nodeLegalName: CordaX500Name = fakeNodeLegalName,
            maxFileSize: Int = MAX_MESSAGE_SIZE,
            maxBufferedBytesPerClient: Long = 10L * MAX_MESSAGE_SIZE,
            configuration: RPCServerConfiguration = RPCServerConfiguration.DEFAULT,
            customPort: NetworkHostAndPort? = null,
            ops: I
    ): CordaFuture<RpcServerHandle> {
        return startRpcBroker(serverName, rpcUser, maxFileSize, maxBufferedBytesPerClient, customPort).map { broker ->
            startRpcServerWithBrokerRunning(rpcUser, nodeLegalName, configuration, ops, broker)
        }
    }

    /**
     * Starts a Netty RPC client.
     *
     * @param rpcOpsClass The [Class] of the RPC interface.
     * @param rpcAddress The address of the RPC server to connect to.
     * @param username The username to authenticate with.
     * @param password The password to authenticate with.
     * @param configuration The RPC client configuration.
     */
    fun <I : RPCOps> startRpcClient(
            rpcOpsClass: Class<I>,
            rpcAddress: NetworkHostAndPort,
            username: String = rpcTestUser.username,
            password: String = rpcTestUser.password,
            configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT
    ): CordaFuture<I> {
        return driverDSL.executorService.fork {
            val client = RPCClient<I>(ArtemisTcpTransport.rpcConnectorTcpTransport(rpcAddress, null), configuration)
            val connection = client.start(rpcOpsClass, username, password, externalTrace)
            driverDSL.shutdownManager.registerShutdown {
                connection.close()
            }
            connection.proxy
        }
    }

    /**
     * Starts a Netty RPC client.
     *
     * @param rpcOpsClass The [Class] of the RPC interface.
     * @param haAddressPool The addresses of the RPC servers(configured in HA mode) to connect to.
     * @param username The username to authenticate with.
     * @param password The password to authenticate with.
     * @param configuration The RPC client configuration.
     */
    fun <I : RPCOps> startRpcClient(
            rpcOpsClass: Class<I>,
            haAddressPool: List<NetworkHostAndPort>,
            username: String = rpcTestUser.username,
            password: String = rpcTestUser.password,
            configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT
    ): CordaFuture<I> {
        return driverDSL.executorService.fork {
            val client = RPCClient<I>(haAddressPool, null, configuration)
            val connection = client.start(rpcOpsClass, username, password, externalTrace)
            driverDSL.shutdownManager.registerShutdown {
                connection.close()
            }
            connection.proxy
        }
    }

    /**
     * Starts a Netty RPC client in a new JVM process that calls random RPCs with random arguments.
     *
     * @param rpcOpsClass The [Class] of the RPC interface.
     * @param rpcAddress The address of the RPC server to connect to.
     * @param username The username to authenticate with.
     * @param password The password to authenticate with.
     */
    fun <I : RPCOps> startRandomRpcClient(
            rpcOpsClass: Class<I>,
            rpcAddress: NetworkHostAndPort,
            username: String = rpcTestUser.username,
            password: String = rpcTestUser.password
    ): CordaFuture<Process> {
        val process = ProcessUtilities.startJavaProcess<RandomRpcUser>(listOf(rpcOpsClass.name, rpcAddress.toString(), username, password))
        driverDSL.shutdownManager.registerProcessShutdown(process)
        return doneFuture(process)
    }

    /**
     * Starts a Netty Artemis session connecting to an RPC server.
     *
     * @param rpcAddress The address of the RPC server.
     * @param username The username to authenticate with.
     * @param password The password to authenticate with.
     */
    fun startArtemisSession(
            rpcAddress: NetworkHostAndPort,
            username: String = rpcTestUser.username,
            password: String = rpcTestUser.password
    ): ClientSession {
        val locator = ActiveMQClient.createServerLocatorWithoutHA(createNettyClientTransportConfiguration(rpcAddress))
        val sessionFactory = locator.createSessionFactory()
        val session = sessionFactory.createSession(username, password, false, true, true, false, DEFAULT_ACK_BATCH_SIZE)
        driverDSL.shutdownManager.registerShutdown {
            session.close()
            sessionFactory.close()
            locator.close()
        }

        return session
    }

    fun startRpcBroker(
            serverName: String = "driver-rpc-server-${random63BitValue()}",
            rpcUser: User = rpcTestUser,
            maxFileSize: Int = MAX_MESSAGE_SIZE,
            maxBufferedBytesPerClient: Long = 10L * MAX_MESSAGE_SIZE,
            customPort: NetworkHostAndPort? = null
    ): CordaFuture<RpcBrokerHandle> {
        val hostAndPort = customPort ?: driverDSL.portAllocation.nextHostAndPort()
        addressMustNotBeBound(driverDSL.executorService, hostAndPort)
        return driverDSL.executorService.fork {
            val artemisConfig = createRpcServerArtemisConfig(maxFileSize, maxBufferedBytesPerClient, driverDSL.driverDirectory / serverName, hostAndPort)
            val server = ActiveMQServerImpl(artemisConfig, SingleUserSecurityManager(rpcUser))
            server.start()
            driverDSL.shutdownManager.registerShutdown {
                server.stop()
                addressMustNotBeBound(driverDSL.executorService, hostAndPort)
            }
            RpcBrokerHandle(
                    hostAndPort = hostAndPort,
                    clientTransportConfiguration = createNettyClientTransportConfiguration(hostAndPort),
                    serverControl = server.activeMQServerControl
            )
        }
    }

    private fun startInVmRpcBroker(
            rpcUser: User = rpcTestUser,
            maxFileSize: Int = MAX_MESSAGE_SIZE,
            maxBufferedBytesPerClient: Long = 10L * MAX_MESSAGE_SIZE
    ): CordaFuture<RpcBrokerHandle> {
        return driverDSL.executorService.fork {
            val artemisConfig = createInVmRpcServerArtemisConfig(maxFileSize, maxBufferedBytesPerClient)
            val server = EmbeddedActiveMQ()
            server.setConfiguration(artemisConfig)
            server.setSecurityManager(SingleUserSecurityManager(rpcUser))
            server.start()
            driverDSL.shutdownManager.registerShutdown {
                server.activeMQServer.stop()
                server.stop()
            }
            RpcBrokerHandle(
                    hostAndPort = null,
                    clientTransportConfiguration = inVmClientTransportConfiguration,
                    serverControl = server.activeMQServer.activeMQServerControl
            )
        }
    }

    fun <I : RPCOps> startRpcServerWithBrokerRunning(
            rpcUser: User = rpcTestUser,
            nodeLegalName: CordaX500Name = fakeNodeLegalName,
            configuration: RPCServerConfiguration = RPCServerConfiguration.DEFAULT,
            ops: I,
            brokerHandle: RpcBrokerHandle,
            queueDrainTimeout: Duration = 5.seconds
    ): RpcServerHandle {
        val locator = ActiveMQClient.createServerLocatorWithoutHA(brokerHandle.clientTransportConfiguration).apply {
            minLargeMessageSize = MAX_MESSAGE_SIZE
            isUseGlobalPools = false
        }
        val rpcSecurityManager = RPCSecurityManagerImpl.fromUserList(users = listOf(InternalUser(rpcUser.username, rpcUser.password, rpcUser.permissions)), id = AuthServiceId("TEST_SECURITY_MANAGER"))
        val rpcServer = RPCServer(
                ops,
                rpcUser.username,
                rpcUser.password,
                locator,
                rpcSecurityManager,
                nodeLegalName,
                configuration
        )
        driverDSL.shutdownManager.registerShutdown {
            rpcServer.close(queueDrainTimeout)
            locator.close()
        }
        rpcServer.start(brokerHandle.serverControl)
        return RpcServerHandle(brokerHandle, rpcServer)
    }
}

/**
 * An out-of-process RPC user that connects to an RPC server and issues random RPCs with random arguments.
 */
class RandomRpcUser {

    companion object {
        private inline fun <reified T> HashMap<Class<*>, Generator<*>>.add(generator: Generator<T>) = this.putIfAbsent(T::class.java, generator)
        private val generatorStore = HashMap<Class<*>, Generator<*>>().apply {
            add(Generator.string())
            add(Generator.int())
        }

        data class Call(val method: Method, val call: () -> Any?)

        @JvmStatic
        fun main(args: Array<String>) {
            require(args.size == 4)
            val rpcClass: Class<RPCOps> = uncheckedCast(Class.forName(args[0]))
            val hostAndPort = NetworkHostAndPort.parse(args[1])
            val username = args[2]
            val password = args[3]
            AMQPClientSerializationScheme.initialiseSerialization()
            val handle = RPCClient<RPCOps>(hostAndPort, null, serializationContext = AMQP_RPC_CLIENT_CONTEXT).start(rpcClass, username, password)
            val callGenerators = rpcClass.declaredMethods.map { method ->
                Generator.sequence(method.parameters.map {
                    generatorStore[it.type] ?: throw Exception("No generator for ${it.type}")
                }).map { arguments ->
                    Call(method) { method.invoke(handle.proxy, *arguments.toTypedArray()) }
                }
            }
            val callGenerator = Generator.choice(callGenerators)
            val random = SplittableRandom()

            while (true) {
                val call = callGenerator.generateOrFail(random)
                call.call()
                Thread.sleep(100)
            }
        }
    }
}

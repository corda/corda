package net.corda.node.services.messaging

import net.corda.core.internal.ThreadBox
import net.corda.core.internal.errors.AddressBindingException
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.internal.artemis.ArtemisBroker
import net.corda.node.internal.artemis.BrokerAddresses
import net.corda.node.internal.artemis.BrokerJaasLoginModule
import net.corda.node.internal.artemis.BrokerJaasLoginModule.Companion.NODE_P2P_ROLE
import net.corda.node.internal.artemis.BrokerJaasLoginModule.Companion.NODE_SECURITY_CONFIG
import net.corda.node.internal.artemis.BrokerJaasLoginModule.Companion.P2P_SECURITY_CONFIG
import net.corda.node.internal.artemis.BrokerJaasLoginModule.Companion.PEER_ROLE
import net.corda.node.internal.artemis.BrokerJaasLoginModule.Companion.RPC_SECURITY_CONFIG
import net.corda.node.internal.artemis.NodeJaasConfig
import net.corda.node.internal.artemis.P2PJaasConfig
import net.corda.node.internal.artemis.RPCJaasConfig
import net.corda.node.internal.artemis.SecureArtemisConfiguration
import net.corda.node.internal.artemis.UserValidationPlugin
import net.corda.node.internal.artemis.isBindingError
import net.corda.node.internal.security.RPCSecurityManager
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.shell.INTERNAL_SHELL_USER
import net.corda.node.services.config.shouldStartLocalShell
import net.corda.node.services.rpc.RolesAdderOnLogin
import net.corda.node.services.rpc.RpcBrokerConfiguration.Companion.queueConfigurations
import net.corda.node.utilities.artemis.startSynchronously
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.AmqpMessageSizeChecksInterceptor
import net.corda.nodeapi.internal.ArtemisMessageSizeChecksInterceptor
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.INTERNAL_PREFIX
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.JOURNAL_HEADER_SIZE
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NOTIFICATIONS_ADDRESS
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2P_PREFIX
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.SECURITY_INVALIDATION_INTERVAL
import net.corda.nodeapi.internal.ArtemisTcpTransport.Companion.p2pAcceptorTcpTransport
import net.corda.nodeapi.internal.ArtemisTcpTransport.Companion.rpcAcceptorTcpTransport
import net.corda.nodeapi.internal.ArtemisTcpTransport.Companion.rpcInternalAcceptorTcpTransport
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfig
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfigImpl
import net.corda.nodeapi.internal.protonwrapper.netty.trustManagerFactoryWithRevocation
import net.corda.nodeapi.internal.requireOnDefaultFileSystem
import net.corda.nodeapi.internal.revocation.CertDistPointCrlSource
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.core.security.Role
import org.apache.activemq.artemis.core.server.ActiveMQServer
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import java.lang.Long.max
import javax.annotation.concurrent.ThreadSafe
import javax.security.auth.login.AppConfigurationEntry
import javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag.REQUIRED
import kotlin.io.path.div

// TODO: Verify that nobody can connect to us and fiddle with our config over the socket due to the secman.
// TODO: Implement a discovery engine that can trigger builds of new connections when another node registers? (later)

/**
 * This class configures and manages an Apache Artemis message queue broker.
 *
 * Nodes communication is managed using an Artemis specific protocol, but it supports other protocols like AMQP/1.0
 * as well for interop.
 *
 * The current implementation is skeletal and lacks features like security or firewall tunnelling (that is, you must
 * be able to receive TCP connections in order to receive messages). It is good enough for local communication within
 * a fully connected network, trusted network or on localhost.
 */
@ThreadSafe
class ArtemisMessagingServer(
        private val config: NodeConfiguration,
        private val messagingServerAddress: NetworkHostAndPort,
        private val maxMessageSize: Int,
        private val journalBufferTimeout: Int? = null,
        private val threadPoolName: String = "P2PServer",
        private val trace: Boolean = false,
        private val distPointCrlSource: CertDistPointCrlSource = CertDistPointCrlSource.SINGLETON,
        private val remotingThreads: Int? = null,
        private val rpcAddresses: BrokerAddresses? = null,
        val rpcSecurityManager: RPCSecurityManager? = null,
        val rpcSslOptions: MutualSslConfiguration? = null,
) : ArtemisBroker, SingletonSerializeAsToken() {
    companion object {
        private val log = contextLogger()
    }

    private class InnerState {
        var running = false
    }

    private val mutex = ThreadBox(InnerState())
    private lateinit var activeMQServer: ActiveMQServer
    override val serverControl: ActiveMQServerControl get() = activeMQServer.activeMQServerControl

    init {
        config.baseDirectory.requireOnDefaultFileSystem()
    }

    override fun start() = mutex.locked {
        if (!running) {
            configureAndStartServer()
            running = true
        }
    }

    override fun stop() = mutex.locked {
        activeMQServer.stop()
        rpcSecurityManager?.close()
        running = false
    }

    override val addresses = config.p2pAddress.let { BrokerAddresses(it, it) }

    override val started: Boolean
        get() = activeMQServer.isStarted

    @Suppress("ThrowsCount", "NestedBlockDepth")
    private fun configureAndStartServer() {
        val artemisConfig = createArtemisConfig()
        val securityManager = createArtemisSecurityManager(artemisConfig.loginListener)
        activeMQServer = ActiveMQServerImpl(artemisConfig, securityManager).apply {
            // Some types of queue might need special preparation on our side, like dialling back or preparing
            // a lazily initialised subsystem.
            registerPostQueueCreationCallback { log.debug { "Queue Created: $it" } }
            registerPostQueueDeletionCallback { address, qName -> log.debug { "Queue deleted: $qName for $address" } }
        }

        try {
            activeMQServer.startSynchronously()
        } catch (e: Throwable) {
            log.error("Unable to start message broker", e)
            if (e.isBindingError()) {
                if (e is IllegalStateException && e.message?.startsWith("AMQ229230:") ?: false) {
                    // java.lang.IllegalStateException: AMQ229230: Failed to bind acceptor 07340871-69cc-11ef-b363-a2d564e2b212 to 0.0.0.0:62165
                    val actualHostAndPort = NetworkHostAndPort.parse(e.message!!.substringAfterLast(' '))
                    if (actualHostAndPort.port != config.p2pAddress.port) throw AddressBindingException(actualHostAndPort)
                }
                throw AddressBindingException(config.p2pAddress)
            } else {
                log.error("Unexpected error starting message broker", e)
                throw e
            }
        }
        activeMQServer.remotingService.addIncomingInterceptor(ArtemisMessageSizeChecksInterceptor(maxMessageSize, config.p2pAddress.port))
        activeMQServer.remotingService.addIncomingInterceptor(AmqpMessageSizeChecksInterceptor(maxMessageSize, config.p2pAddress.port))
        // Config driven switch between legacy CORE bridges and the newer AMQP protocol bridges.
        log.info("P2P messaging server listening on $messagingServerAddress")
    }

    internal abstract class P2PBrokerConfig : SecureArtemisConfiguration() {
        abstract val loginListener: (String) -> Unit
    }

    @Suppress("ComplexMethod", "MagicNumber")
    private fun createArtemisConfig() = object : P2PBrokerConfig() {
        override val loginListener: (String) -> Unit

        init {
            name = if (rpcAddresses != null) "P2PAndRPC" else "P2P"

            val artemisDir = config.baseDirectory / "artemis"
            bindingsDirectory = (artemisDir / "bindings").toString()
            journalDirectory = (artemisDir / "journal").toString()
            largeMessagesDirectory = (artemisDir / "large-messages").toString()
            pagingDirectory = (artemisDir / "paging").toString()
            // The transaction cache is configurable, and drives other cache sizes.
            globalMaxSize = max(config.transactionCacheSizeBytes, 10L * maxMessageSize)

            val revocationMode = if (config.crlCheckArtemisServer) {
                if (config.crlCheckSoftFail) RevocationConfig.Mode.SOFT_FAIL else RevocationConfig.Mode.HARD_FAIL
            } else {
                RevocationConfig.Mode.OFF
            }
            val trustManagerFactory = trustManagerFactoryWithRevocation(
                    config.p2pSslOptions.trustStore.get(),
                    RevocationConfigImpl(revocationMode),
                    distPointCrlSource
            )
            addAcceptorConfiguration(p2pAcceptorTcpTransport(
                    NetworkHostAndPort(messagingServerAddress.host, messagingServerAddress.port),
                    config.p2pSslOptions,
                    trustManagerFactory,
                    threadPoolName = threadPoolName,
                    trace = trace,
                    remotingThreads = remotingThreads
            ))
            if (rpcAddresses != null) {
                addAcceptorConfiguration(rpcAcceptorTcpTransport(rpcAddresses.primary, config.rpcOptions.sslConfig, enableSSL = config.rpcOptions.useSsl, threadPoolName = "RPCServer"))
                if (rpcAddresses.admin != rpcAddresses.primary) {
                    addAcceptorConfiguration(rpcInternalAcceptorTcpTransport(rpcAddresses.admin, rpcSslOptions!!, threadPoolName = "RPCServerAdmin"))
                }
                queueConfigs = queueConfigurations()
            }
            // Enable built in message deduplication. Note we still have to do our own as the delayed commits
            // and our own definition of commit mean that the built in deduplication cannot remove all duplicates.
            idCacheSize = 2000 // Artemis Default duplicate cache size i.e. a guess
            isPersistIDCache = true
            isPopulateValidatedUser = true
            journalBufferSize_NIO = maxMessageSize + JOURNAL_HEADER_SIZE // Artemis default is 490KiB - required to address IllegalArgumentException (when Artemis uses Java NIO): Record is too large to store.
            journalBufferSize_AIO = maxMessageSize + JOURNAL_HEADER_SIZE // Required to address IllegalArgumentException (when Artemis uses Linux Async IO): Record is too large to store.
            journalBufferTimeout_NIO = journalBufferTimeout ?: ActiveMQDefaultConfiguration.getDefaultJournalBufferTimeoutNio()
            journalBufferTimeout_AIO = journalBufferTimeout ?: ActiveMQDefaultConfiguration.getDefaultJournalBufferTimeoutAio()
            journalFileSize = maxMessageSize + JOURNAL_HEADER_SIZE// The size of each journal file in bytes. Artemis default is 10MiB.
            managementNotificationAddress = SimpleString.of(NOTIFICATIONS_ADDRESS)

            // JMX enablement
            if (config.jmxMonitoringHttpPort != null) {
                isJMXManagementEnabled = true
                isJMXUseBrokerName = true
            }
            // Validate user in AMQP message header against authenticated session
            registerBrokerPlugin(UserValidationPlugin())

            if (rpcSecurityManager == null) {
                loginListener = { }
            } else {
                val nodeInternalRole = Role(BrokerJaasLoginModule.NODE_RPC_ROLE, true, true, true, true, true, true, true, true, true, true, false, false)
                val addRPCRoleToUsers = if (config.shouldStartLocalShell()) listOf(INTERNAL_SHELL_USER) else emptyList()
                val rolesAdderOnLogin = RolesAdderOnLogin(addRPCRoleToUsers) { username ->
                    "${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.$username.#" to setOf(nodeInternalRole, restrictedRole(
                            "${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.$username",
                            consume = true,
                            createNonDurableQueue = true,
                            deleteNonDurableQueue = true)
                    )
                }
                securitySettingPlugins.add(rolesAdderOnLogin)
                loginListener = { username: String -> rolesAdderOnLogin.onLogin(username) }
            }
        }
    }.configureAddressSecurity()

    /**
     * Authenticated clients connecting to us fall in one of the following groups:
     * 1. The node itself. It is given full access to all valid queues.
     * 2. Peers on the same network as us. These are only given permission to send to our P2P inbound queue.
     * 3. RPC users. These are only given sufficient access to perform RPC with us.
     * 4. Verifiers. These are given read access to the verification request queue and write access to the response queue.
     */
    private fun P2PBrokerConfig.configureAddressSecurity(): P2PBrokerConfig {
        val nodeInternalP2PRole = Role(NODE_P2P_ROLE, true, true, true, true, true, true, true, true, true, true, false, false)
        securityRoles["$P2P_PREFIX#"] = setOf(nodeInternalP2PRole, restrictedRole(PEER_ROLE, send = true))
        if (rpcAddresses != null) {
            val nodeInternalRPCRole = Role(BrokerJaasLoginModule.NODE_RPC_ROLE, true, true, true, true, true, true, true, true, true, true, false, false)
            securityRoles["$INTERNAL_PREFIX#"] = setOf(nodeInternalP2PRole, nodeInternalRPCRole)  // Do not add any other roles here as it's only for the node
            securityRoles[RPCApi.RPC_SERVER_QUEUE_NAME] = setOf(nodeInternalRPCRole, restrictedRole(BrokerJaasLoginModule.RPC_ROLE, send = true))
        } else {
            securityRoles["$INTERNAL_PREFIX#"] = setOf(nodeInternalP2PRole)  // Do not add any other roles here as it's only for the node
        }
        securityInvalidationInterval = SECURITY_INVALIDATION_INTERVAL
        return this
    }

    private fun restrictedRole(name: String, send: Boolean = false, consume: Boolean = false, createDurableQueue: Boolean = false,
                               deleteDurableQueue: Boolean = false, createNonDurableQueue: Boolean = false,
                               deleteNonDurableQueue: Boolean = false, manage: Boolean = false, browse: Boolean = false): Role {
        return Role(name, send, consume, createDurableQueue, deleteDurableQueue, createNonDurableQueue,
                deleteNonDurableQueue, manage, browse, createDurableQueue || createNonDurableQueue, deleteDurableQueue || deleteNonDurableQueue, false, false)
    }

    private fun createArtemisSecurityManager(loginListener: (String) -> Unit): ActiveMQJAASSecurityManager {
        val keyStore = config.p2pSslOptions.keyStore.get().value.internal
        val trustStore = config.p2pSslOptions.trustStore.get().value.internal
        val revocationMode = when {
            config.crlCheckArtemisServer && config.crlCheckSoftFail -> RevocationConfig.Mode.SOFT_FAIL
            config.crlCheckArtemisServer && !config.crlCheckSoftFail -> RevocationConfig.Mode.HARD_FAIL
            else -> RevocationConfig.Mode.OFF
        }

        val securityConfig = object : SecurityConfiguration() {
            // Override to make it work with our login module
            override fun getAppConfigurationEntry(name: String): Array<AppConfigurationEntry> {
                val options = mutableMapOf(
                        P2P_SECURITY_CONFIG to P2PJaasConfig(keyStore, trustStore, revocationMode),
                        NODE_SECURITY_CONFIG to NodeJaasConfig(keyStore, trustStore)
                )
                if (rpcSecurityManager != null) {
                    options[RPC_SECURITY_CONFIG] = RPCJaasConfig(rpcSecurityManager, loginListener, config.rpcOptions.useSsl)
                }
                return arrayOf(AppConfigurationEntry(name, REQUIRED, options))
            }
        }
        return InterceptingActiveMQJAASSecurityManager(BrokerJaasLoginModule::class.java.name, securityConfig, messagingServerAddress.port, rpcAddresses?.primary?.port
                ?: -1, rpcAddresses?.admin?.port ?: -1)
    }
}

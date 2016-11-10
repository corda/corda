package net.corda.node.services.messaging

import com.google.common.net.HostAndPort
import net.corda.core.ThreadBox
import net.corda.core.crypto.AddressFormatException
import net.corda.core.div
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.utilities.loggerFor
import net.corda.node.services.RPCUserService
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.ArtemisMessagingServer.NodeLoginModule.Companion.NODE_USER
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.core.config.BridgeConfiguration
import org.apache.activemq.artemis.core.config.Configuration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.core.security.Role
import org.apache.activemq.artemis.core.server.ActiveMQServer
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal
import rx.Subscription
import java.io.IOException
import java.security.Principal
import java.util.*
import javax.annotation.concurrent.ThreadSafe
import javax.security.auth.Subject
import javax.security.auth.callback.CallbackHandler
import javax.security.auth.callback.NameCallback
import javax.security.auth.callback.PasswordCallback
import javax.security.auth.callback.UnsupportedCallbackException
import javax.security.auth.login.AppConfigurationEntry
import javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag.REQUIRED
import javax.security.auth.login.FailedLoginException
import javax.security.auth.login.LoginException
import javax.security.auth.spi.LoginModule

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
class ArtemisMessagingServer(override val config: NodeConfiguration,
                             val myHostPort: HostAndPort,
                             val networkMapCache: NetworkMapCache,
                             val userService: RPCUserService) : ArtemisMessagingComponent() {

    companion object {
        private val log = loggerFor<ArtemisMessagingServer>()
    }

    private class InnerState {
        var running = false
    }

    private val mutex = ThreadBox(InnerState())
    private lateinit var activeMQServer: ActiveMQServer
    private var networkChangeHandle: Subscription? = null

    init {
        config.basedir.expectedOnDefaultFileSystem()
    }

    fun start() = mutex.locked {
        if (!running) {
            configureAndStartServer()
            networkChangeHandle = networkMapCache.changed.subscribe { onNetworkChange(it) }
            running = true
        }
    }

    fun stop() = mutex.locked {
        networkChangeHandle?.unsubscribe()
        networkChangeHandle = null
        activeMQServer.stop()
        running = false
    }

    fun bridgeToNetworkMapService(networkMapService: SingleMessageRecipient?) {
        if ((networkMapService != null) && (networkMapService is NetworkMapAddress)) {
            val query = activeMQServer.queueQuery(NETWORK_MAP_ADDRESS)
            if (!query.isExists) {
                activeMQServer.createQueue(NETWORK_MAP_ADDRESS, NETWORK_MAP_ADDRESS, null, true, false)
            }

            maybeDeployBridgeForAddress(NETWORK_MAP_ADDRESS, networkMapService)
        }
    }

    private fun onNetworkChange(change: NetworkMapCache.MapChange) {
        val address = change.node.address
        if (address is ArtemisMessagingComponent.ArtemisAddress) {
            val queueName = address.queueName
            when (change.type) {
                NetworkMapCache.MapChangeType.Added -> {
                    val query = activeMQServer.queueQuery(queueName)
                    if (query.isExists) {
                        // Queue exists so now wire up bridge
                        maybeDeployBridgeForAddress(queueName, change.node.address)
                    }
                }

                NetworkMapCache.MapChangeType.Modified -> {
                    (change.prevNodeInfo?.address as? ArtemisMessagingComponent.ArtemisAddress)?.let {
                        // remove any previous possibly different bridge
                        maybeDestroyBridge(it.queueName)
                    }
                    val query = activeMQServer.queueQuery(queueName)
                    if (query.isExists) {
                        // Deploy new bridge
                        maybeDeployBridgeForAddress(queueName, change.node.address)
                    }
                }

                NetworkMapCache.MapChangeType.Removed -> {
                    (change.prevNodeInfo?.address as? ArtemisMessagingComponent.ArtemisAddress)?.let {
                        // Remove old bridge
                        maybeDestroyBridge(it.queueName)
                    }
                    // just in case of NetworkMapCache version issues
                    maybeDestroyBridge(queueName)
                }
            }
        }
    }

    private fun configureAndStartServer() {
        val config = createArtemisConfig()
        val securityManager = createArtemisSecurityManager()

        activeMQServer = ActiveMQServerImpl(config, securityManager).apply {
            // Throw any exceptions which are detected during startup
            registerActivationFailureListener { exception -> throw exception }

            // Some types of queue might need special preparation on our side, like dialling back or preparing
            // a lazily initialised subsystem.
            registerPostQueueCreationCallback { queueName ->
                log.debug("Queue created: $queueName")
                if (queueName.startsWith(PEERS_PREFIX) && queueName != NETWORK_MAP_ADDRESS) {
                    try {
                        val identity = parseKeyFromQueueName(queueName.toString())
                        val nodeInfo = networkMapCache.getNodeByPublicKey(identity)
                        if (nodeInfo != null) {
                            maybeDeployBridgeForAddress(queueName, nodeInfo.address)
                        } else {
                            log.error("Queue created for a peer that we don't know from the network map: $queueName")
                        }
                    } catch (e: AddressFormatException) {
                        log.error("Protocol violation: Could not parse queue name as Base 58: $queueName")
                    }
                }
            }

            registerPostQueueDeletionCallback { address, qName ->
                if (qName == address)
                    log.debug("Queue deleted: $qName")
                else
                    log.debug("Queue deleted: $qName for $address")
            }
        }
        activeMQServer.start()
    }

    private fun createArtemisConfig(): Configuration = ConfigurationImpl().apply {
        val artemisDir = config.basedir / "artemis"
        bindingsDirectory = (artemisDir / "bindings").toString()
        journalDirectory = (artemisDir / "journal").toString()
        largeMessagesDirectory = (artemisDir / "largemessages").toString()
        acceptorConfigurations = setOf(
                tcpTransport(ConnectionDirection.INBOUND, "0.0.0.0", myHostPort.port)
        )
        // Enable built in message deduplication. Note we still have to do our own as the delayed commits
        // and our own definition of commit mean that the built in deduplication cannot remove all duplicates.
        idCacheSize = 2000 // Artemis Default duplicate cache size i.e. a guess
        isPersistIDCache = true
        isPopulateValidatedUser = true
        configureQueueSecurity()
    }

    private fun ConfigurationImpl.configureQueueSecurity() {
        val nodeRPCSendRole = restrictedRole(NODE_USER, send = true)  // The node needs to be able to send responses on the client queues

        for ((username) in userService.users) {
            // Clients need to be able to consume the responses on their queues, and they're also responsible for creating and destroying them
            val clientRole = restrictedRole(username, consume = true, createNonDurableQueue = true, deleteNonDurableQueue = true)
            securityRoles["$CLIENTS_PREFIX$username.rpc.*"] = setOf(nodeRPCSendRole, clientRole)
        }

        // TODO Restrict this down to just what the node needs
        securityRoles["#"] = setOf(Role(NODE_USER, true, true, true, true, true, true, true, true))
        securityRoles[RPC_REQUESTS_QUEUE] = setOf(
                restrictedRole(NODE_USER, createNonDurableQueue = true, deleteNonDurableQueue = true),
                restrictedRole(RPC_REQUESTS_QUEUE, send = true))  // Clients need to be able to send their requests
    }

    private fun restrictedRole(name: String, send: Boolean = false, consume: Boolean = false, createDurableQueue: Boolean = false,
                               deleteDurableQueue: Boolean = false, createNonDurableQueue: Boolean = false,
                               deleteNonDurableQueue: Boolean = false, manage: Boolean = false, browse: Boolean = false): Role {
        return Role(name, send, consume, createDurableQueue, deleteDurableQueue, createNonDurableQueue,
                deleteNonDurableQueue, manage, browse)
    }

    private fun createArtemisSecurityManager(): ActiveMQJAASSecurityManager {
        val securityConfig = object : SecurityConfiguration() {
            // Override to make it work with our login module
            override fun getAppConfigurationEntry(name: String): Array<AppConfigurationEntry> {
                val options = mapOf(RPCUserService::class.java.name to userService)
                return arrayOf(AppConfigurationEntry(name, REQUIRED, options))
            }
        }

        return ActiveMQJAASSecurityManager(NodeLoginModule::class.java.name, securityConfig)
    }

    private fun connectorExists(hostAndPort: HostAndPort) = hostAndPort.toString() in activeMQServer.configuration.connectorConfigurations

    private fun addConnector(hostAndPort: HostAndPort) = activeMQServer.configuration.addConnectorConfiguration(
            hostAndPort.toString(),
            tcpTransport(
                    ConnectionDirection.OUTBOUND,
                    hostAndPort.hostText,
                    hostAndPort.port
            )
    )

    private fun bridgeExists(name: SimpleString) = activeMQServer.clusterManager.bridges.containsKey(name.toString())

    private fun deployBridge(hostAndPort: HostAndPort, name: String) {
        activeMQServer.deployBridge(BridgeConfiguration().apply {
            setName(name)
            queueName = name
            forwardingAddress = name
            staticConnectors = listOf(hostAndPort.toString())
            confirmationWindowSize = 100000 // a guess
            isUseDuplicateDetection = true // Enable the bridges automatic deduplication logic
        })
    }

    /**
     * For every queue created we need to have a bridge deployed in case the address of the queue
     * is that of a remote party.
     */
    private fun maybeDeployBridgeForAddress(name: SimpleString, nodeInfo: SingleMessageRecipient) {
        require(name.startsWith(PEERS_PREFIX))
        val hostAndPort = toHostAndPort(nodeInfo)
        if (hostAndPort == myHostPort)
            return
        if (!connectorExists(hostAndPort))
            addConnector(hostAndPort)
        if (!bridgeExists(name))
            deployBridge(hostAndPort, name.toString())
    }

    private fun maybeDestroyBridge(name: SimpleString) {
        if (bridgeExists(name)) {
            activeMQServer.destroyBridge(name.toString())
        }
    }


    // We could have used the built-in PropertiesLoginModule but that exposes a roles properties file. Roles are used
    // for queue access control and our RPC users must only have access to the queues they need and this cannot be allowed
    // to be modified.
    class NodeLoginModule : LoginModule {

        companion object {
            const val NODE_USER = "Node"
        }

        private var loginSucceeded: Boolean = false
        private lateinit var subject: Subject
        private lateinit var callbackHandler: CallbackHandler
        private lateinit var userService: RPCUserService
        private val principals = ArrayList<Principal>()

        override fun initialize(subject: Subject, callbackHandler: CallbackHandler, sharedState: Map<String, *>, options: Map<String, *>) {
            this.subject = subject
            this.callbackHandler = callbackHandler
            userService = options[RPCUserService::class.java.name] as RPCUserService
        }

        override fun login(): Boolean {
            val nameCallback = NameCallback("Username: ")
            val passwordCallback = PasswordCallback("Password: ", false)

            try {
                callbackHandler.handle(arrayOf(nameCallback, passwordCallback))
            } catch (e: IOException) {
                throw LoginException(e.message)
            } catch (e: UnsupportedCallbackException) {
                throw LoginException("${e.message} not available to obtain information from user")
            }

            val username = nameCallback.name ?: throw FailedLoginException("User name is null")
            val receivedPassword = passwordCallback.password ?: throw FailedLoginException("Password is null")
            val password = if (username == NODE_USER) "Node" else userService.getUser(username)?.password ?: throw FailedLoginException("User does not exist")
            if (password != String(receivedPassword)) {
                throw FailedLoginException("Password does not match")
            }

            principals += UserPrincipal(username)
            principals += RolePrincipal(username)  // The roles are configured using the usernames
            if (username != NODE_USER) {
                principals += RolePrincipal(RPC_REQUESTS_QUEUE)  // This enables the RPC client to send requests
            }

            loginSucceeded = true
            return loginSucceeded
        }

        override fun commit(): Boolean {
            val result = loginSucceeded
            if (result) {
                subject.principals.addAll(principals)
            }
            clear()
            return result
        }

        override fun abort(): Boolean {
            clear()
            return true
        }

        override fun logout(): Boolean {
            subject.principals.removeAll(principals)
            return true
        }

        private fun clear() {
            loginSucceeded = false
        }
    }

}

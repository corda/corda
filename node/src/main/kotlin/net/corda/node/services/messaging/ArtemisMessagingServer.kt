package net.corda.node.services.messaging

import com.google.common.net.HostAndPort
import net.corda.core.ThreadBox
import net.corda.core.crypto.AddressFormatException
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.X509Utilities
import net.corda.core.crypto.X509Utilities.CORDA_CLIENT_CA
import net.corda.core.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.core.crypto.newSecureRandom
import net.corda.core.div
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.NetworkMapCache.MapChange
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.node.printBasicNodeInfo
import net.corda.node.services.RPCUserService
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.ArtemisMessagingComponent.ConnectionDirection.INBOUND
import net.corda.node.services.messaging.ArtemisMessagingComponent.ConnectionDirection.OUTBOUND
import net.corda.node.services.messaging.ArtemisMessagingServer.NodeLoginModule.Companion.NODE_ROLE
import net.corda.node.services.messaging.ArtemisMessagingServer.NodeLoginModule.Companion.PEER_ROLE
import net.corda.node.services.messaging.ArtemisMessagingServer.NodeLoginModule.Companion.RPC_ROLE
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.core.config.BridgeConfiguration
import org.apache.activemq.artemis.core.config.Configuration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.core.security.Role
import org.apache.activemq.artemis.core.server.ActiveMQServer
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import org.apache.activemq.artemis.spi.core.security.jaas.CertificateCallback
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal
import rx.Subscription
import java.io.IOException
import java.math.BigInteger
import java.security.Principal
import java.security.PublicKey
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
            networkChangeHandle = networkMapCache.changed.subscribe { destroyPossibleStaleBridge(it) }
            running = true
        }
    }

    fun stop() = mutex.locked {
        networkChangeHandle?.unsubscribe()
        networkChangeHandle = null
        activeMQServer.stop()
        running = false
    }

    fun bridgeToNetworkMapService(networkMapService: NetworkMapAddress) {
        val query = activeMQServer.queueQuery(NETWORK_MAP_ADDRESS)
        if (!query.isExists) {
            activeMQServer.createQueue(NETWORK_MAP_ADDRESS, NETWORK_MAP_ADDRESS, null, true, false)
        }
        maybeDeployBridgeForAddress(networkMapService)
    }

    private fun destroyPossibleStaleBridge(change: MapChange) {
        val staleNodeInfo = when (change) {
            is MapChange.Modified -> change.previousNode
            is MapChange.Removed -> change.node
            is MapChange.Added -> return
        }
        (staleNodeInfo.address as? ArtemisAddress)?.let { maybeDestroyBridge(it.queueName) }
    }

    private fun configureAndStartServer() {
        val config = createArtemisConfig()
        val securityManager = createArtemisSecurityManager()
        activeMQServer = ActiveMQServerImpl(config, securityManager).apply {
            // Throw any exceptions which are detected during startup
            registerActivationFailureListener { exception -> throw exception }
            // Some types of queue might need special preparation on our side, like dialling back or preparing
            // a lazily initialised subsystem.
            registerPostQueueCreationCallback { deployBridgeFromNewPeerQueue(it) }
            registerPostQueueDeletionCallback { address, qName -> log.debug { "Queue deleted: $qName for $address" } }
        }
        activeMQServer.start()
        printBasicNodeInfo("Node listening on address", myHostPort.toString())
    }

    private fun deployBridgeFromNewPeerQueue(queueName: SimpleString) {
        log.debug { "Queue created: $queueName" }
        if (!queueName.startsWith(PEERS_PREFIX)) return
        try {
            val identity = CompositeKey.parseFromBase58(queueName.substring(PEERS_PREFIX.length))
            val nodeInfo = networkMapCache.getNodeByCompositeKey(identity)
            if (nodeInfo != null) {
                val address = nodeInfo.address
                if (address is NodeAddress) {
                    maybeDeployBridgeForAddress(address)
                } else {
                    log.error("Don't know how to deal with $address")
                }
            } else {
                log.error("Queue created for a peer that we don't know from the network map: $queueName")
            }
        } catch (e: AddressFormatException) {
            log.error("Flow violation: Could not parse queue name as Base 58: $queueName")
        }
    }

    private fun createArtemisConfig(): Configuration = ConfigurationImpl().apply {
        val artemisDir = config.basedir / "artemis"
        bindingsDirectory = (artemisDir / "bindings").toString()
        journalDirectory = (artemisDir / "journal").toString()
        largeMessagesDirectory = (artemisDir / "large-messages").toString()
        acceptorConfigurations = setOf(tcpTransport(INBOUND, "0.0.0.0", myHostPort.port))
        // Enable built in message deduplication. Note we still have to do our own as the delayed commits
        // and our own definition of commit mean that the built in deduplication cannot remove all duplicates.
        idCacheSize = 2000 // Artemis Default duplicate cache size i.e. a guess
        isPersistIDCache = true
        isPopulateValidatedUser = true
        managementNotificationAddress = SimpleString(NOTIFICATIONS_ADDRESS)
        // Artemis allows multiple servers to be grouped together into a cluster for load balancing purposes. The cluster
        // user is used for connecting the nodes together. It has super-user privileges and so it's imperative that its
        // password is changed from the default (as warned in the docs). Since we don't need this feature we turn it off
        // by having its password be an unknown securely random 128-bit value.
        clusterPassword = BigInteger(128, newSecureRandom()).toString(16)
        configureAddressSecurity()
    }

    /**
     * Authenticated clients connecting to us fall in one of three groups:
     * 1. The node hosting us and any of its logically connected components. These are given full access to all valid queues.
     * 2. Peers on the same network as us. These are only given permission to send to our P2P inbound queue.
     * 3. RPC users. These are only given sufficient access to perform RPC with us.
     */
    private fun ConfigurationImpl.configureAddressSecurity() {
        val nodeInternalRole = Role(NODE_ROLE, true, true, true, true, true, true, true, true)
        securityRoles["$INTERNAL_PREFIX#"] = setOf(nodeInternalRole)  // Do not add any other roles here as it's only for the node
        securityRoles[P2P_QUEUE] = setOf(nodeInternalRole, restrictedRole(PEER_ROLE, send = true))
        securityRoles[RPC_REQUESTS_QUEUE] = setOf(nodeInternalRole, restrictedRole(RPC_ROLE, send = true))
        // TODO remove NODE_USER once webserver doesn't need it
        val possibleClientUserNames = userService.users.map { it.username } + listOf(NODE_USER)
        for (username in possibleClientUserNames) {
            securityRoles["$CLIENTS_PREFIX$username.rpc.*"] = setOf(
                    nodeInternalRole,
                    restrictedRole("$CLIENTS_PREFIX$username", consume = true, createNonDurableQueue = true, deleteNonDurableQueue = true))
        }
    }

    private fun restrictedRole(name: String, send: Boolean = false, consume: Boolean = false, createDurableQueue: Boolean = false,
                               deleteDurableQueue: Boolean = false, createNonDurableQueue: Boolean = false,
                               deleteNonDurableQueue: Boolean = false, manage: Boolean = false, browse: Boolean = false): Role {
        return Role(name, send, consume, createDurableQueue, deleteDurableQueue, createNonDurableQueue,
                deleteNonDurableQueue, manage, browse)
    }

    private fun createArtemisSecurityManager(): ActiveMQJAASSecurityManager {
        val ourRootCAPublicKey = X509Utilities
                .loadCertificateFromKeyStore(config.trustStorePath, config.trustStorePassword, CORDA_ROOT_CA)
                .publicKey
        val ourPublicKey = X509Utilities
                .loadCertificateFromKeyStore(config.keyStorePath, config.keyStorePassword, CORDA_CLIENT_CA)
                .publicKey
        val securityConfig = object : SecurityConfiguration() {
            // Override to make it work with our login module
            override fun getAppConfigurationEntry(name: String): Array<AppConfigurationEntry> {
                val options = mapOf(
                        RPCUserService::class.java.name to userService,
                        CORDA_ROOT_CA to ourRootCAPublicKey,
                        CORDA_CLIENT_CA to ourPublicKey)
                return arrayOf(AppConfigurationEntry(name, REQUIRED, options))
            }
        }
        return ActiveMQJAASSecurityManager(NodeLoginModule::class.java.name, securityConfig)
    }

    private fun connectorExists(hostAndPort: HostAndPort) = hostAndPort.toString() in activeMQServer.configuration.connectorConfigurations

    private fun addConnector(hostAndPort: HostAndPort) = activeMQServer.configuration.addConnectorConfiguration(
            hostAndPort.toString(),
            tcpTransport(OUTBOUND, hostAndPort.hostText, hostAndPort.port)
    )

    private fun bridgeExists(name: SimpleString) = activeMQServer.clusterManager.bridges.containsKey(name.toString())

    private fun maybeDeployBridgeForAddress(address: ArtemisAddress) {
        if (!connectorExists(address.hostAndPort)) {
            addConnector(address.hostAndPort)
        }
        if (!bridgeExists(address.queueName)) {
            deployBridge(address)
        }
    }

    /**
     * All nodes are expected to have a public facing address called [ArtemisMessagingComponent.P2P_QUEUE] for receiving
     * messages from other nodes. When we want to send a message to a node we send it to our internal address/queue for it,
     * as defined by ArtemisAddress.queueName. A bridge is then created to forward messages from this queue to the node's
     * P2P address.
     */
    private fun deployBridge(address: ArtemisAddress) {
        activeMQServer.deployBridge(BridgeConfiguration().apply {
            name = address.queueName.toString()
            queueName = address.queueName.toString()
            forwardingAddress = P2P_QUEUE
            staticConnectors = listOf(address.hostAndPort.toString())
            confirmationWindowSize = 100000 // a guess
            isUseDuplicateDetection = true // Enable the bridge's automatic deduplication logic
            // As a peer of the target node we must connect to it using the peer user. Actual authentication is done using
            // our TLS certificate.
            user = PEER_USER
            password = PEER_USER
        })
    }

    private fun maybeDestroyBridge(name: SimpleString) {
        if (bridgeExists(name)) {
            activeMQServer.destroyBridge(name.toString())
        }
    }

    /**
     * Clients must connect to us with a username and password and must use TLS. If a someone connects with
     * [ArtemisMessagingComponent.NODE_USER] then we confirm it's just us as the node by checking their TLS certificate
     * is the same as our one in our key store. Then they're given full access to all valid queues. If they connect with
     * [ArtemisMessagingComponent.PEER_USER] then we confirm they belong on our P2P network by checking their root CA is
     * the same as our root CA. If that's the case the only access they're given is the ablility send to our P2P address.
     * In both cases the messages these authenticated nodes send to us are tagged with their subject DN and we assume
     * the CN within that is their legal name.
     * Otherwise if the username is neither of the above we assume it's an RPC user and authenticate against our list of
     * valid RPC users. RPC clients are given permission to perform RPC and nothing else.
     */
    class NodeLoginModule : LoginModule {

        companion object {
            // Include forbidden username character to prevent name clash with any RPC usernames
            const val PEER_ROLE = "SystemRoles/Peer"
            const val NODE_ROLE = "SystemRoles/Node"
            const val RPC_ROLE = "SystemRoles/RPC"
        }

        private var loginSucceeded: Boolean = false
        private lateinit var subject: Subject
        private lateinit var callbackHandler: CallbackHandler
        private lateinit var userService: RPCUserService
        private lateinit var ourRootCAPublicKey: PublicKey
        private lateinit var ourPublicKey: PublicKey
        private val principals = ArrayList<Principal>()

        override fun initialize(subject: Subject, callbackHandler: CallbackHandler, sharedState: Map<String, *>, options: Map<String, *>) {
            this.subject = subject
            this.callbackHandler = callbackHandler
            userService = options[RPCUserService::class.java.name] as RPCUserService
            ourRootCAPublicKey = options[CORDA_ROOT_CA] as PublicKey
            ourPublicKey = options[CORDA_CLIENT_CA] as PublicKey
        }

        override fun login(): Boolean {
            val nameCallback = NameCallback("Username: ")
            val passwordCallback = PasswordCallback("Password: ", false)
            val certificateCallback = CertificateCallback()

            try {
                callbackHandler.handle(arrayOf(nameCallback, passwordCallback, certificateCallback))
            } catch (e: IOException) {
                throw LoginException(e.message)
            } catch (e: UnsupportedCallbackException) {
                throw LoginException("${e.message} not available to obtain information from user")
            }

            val username = nameCallback.name ?: throw FailedLoginException("Username not provided")
            val password = String(passwordCallback.password ?: throw FailedLoginException("Password not provided"))

            val validatedUser = if (username == PEER_USER || username == NODE_USER) {
                val certificates = certificateCallback.certificates ?: throw FailedLoginException("No TLS?")
                val peerCertificate = certificates.first()
                val role = if (username == NODE_USER) {
                    if (peerCertificate.publicKey != ourPublicKey) {
                        throw FailedLoginException("Only the node can login as $NODE_USER")
                    }
                    NODE_ROLE
                } else {
                    val theirRootCAPublicKey = certificates.last().publicKey
                    if (theirRootCAPublicKey != ourRootCAPublicKey) {
                        throw FailedLoginException("Peer does not belong on our network. Their root CA: $theirRootCAPublicKey")
                    }
                    PEER_ROLE  // This enables the peer to send to our P2P address
                }
                principals += RolePrincipal(role)
                peerCertificate.subjectDN.name
            } else {
                // Otherwise assume they're an RPC user
                val rpcUser = userService.getUser(username) ?: throw FailedLoginException("User does not exist")
                if (password != rpcUser.password) {
                    // TODO Switch to hashed passwords
                    // TODO Retrieve client IP address to include in exception message
                    throw FailedLoginException("Password for user $username does not match")
                }
                principals += RolePrincipal(RPC_ROLE)  // This enables the RPC client to send requests
                principals += RolePrincipal("$CLIENTS_PREFIX$username")  // This enables the RPC client to receive responses
                username
            }
            principals += UserPrincipal(validatedUser)

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

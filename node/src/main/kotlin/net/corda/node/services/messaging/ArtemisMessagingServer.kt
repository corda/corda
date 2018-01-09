package net.corda.node.services.messaging

import net.corda.core.crypto.AddressFormatException
import net.corda.core.crypto.newSecureRandom
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.ThreadBox
import net.corda.core.internal.div
import net.corda.core.internal.noneOrSingle
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.NetworkMapCache.MapChange
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.core.utilities.parsePublicKeyBase58
import net.corda.node.internal.Node
import net.corda.node.internal.security.Password
import net.corda.node.internal.security.RPCSecurityManager
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.NodeLoginModule.Companion.NODE_ROLE
import net.corda.node.services.messaging.NodeLoginModule.Companion.PEER_ROLE
import net.corda.node.services.messaging.NodeLoginModule.Companion.RPC_ROLE
import net.corda.node.services.messaging.NodeLoginModule.Companion.VERIFIER_ROLE
import net.corda.nodeapi.ArtemisTcpTransport
import net.corda.nodeapi.ConnectionDirection
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.VerifierApi
import net.corda.nodeapi.internal.ArtemisMessagingComponent.ArtemisPeerAddress
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.INTERNAL_PREFIX
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NODE_USER
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NOTIFICATIONS_ADDRESS
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2P_QUEUE
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.PEERS_PREFIX
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.PEER_USER
import net.corda.nodeapi.internal.ArtemisMessagingComponent.NodeAddress
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_TLS
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.nodeapi.internal.crypto.loadKeyStore
import net.corda.nodeapi.internal.requireOnDefaultFileSystem
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl
import org.apache.activemq.artemis.core.config.Configuration
import org.apache.activemq.artemis.core.config.CoreQueueConfiguration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory
import org.apache.activemq.artemis.core.security.Role
import org.apache.activemq.artemis.core.server.ActiveMQServer
import org.apache.activemq.artemis.core.server.SecuritySettingPlugin
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl
import org.apache.activemq.artemis.core.settings.HierarchicalRepository
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy
import org.apache.activemq.artemis.core.settings.impl.AddressSettings
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import org.apache.activemq.artemis.spi.core.security.jaas.CertificateCallback
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal
import rx.Subscription
import java.io.IOException
import java.math.BigInteger
import java.security.KeyStore
import java.security.KeyStoreException
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
import javax.security.cert.CertificateException

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
class ArtemisMessagingServer(private val config: NodeConfiguration,
                             private val p2pPort: Int,
                             val rpcPort: Int?,
                             val networkMapCache: NetworkMapCache,
                             val securityManager: RPCSecurityManager,
                             val maxMessageSize: Int) : SingletonSerializeAsToken() {
    companion object {
        private val log = contextLogger()
    }
    private class InnerState {
        var running = false
    }

    private val mutex = ThreadBox(InnerState())
    private lateinit var activeMQServer: ActiveMQServer
    val serverControl: ActiveMQServerControl get() = activeMQServer.activeMQServerControl
    private var networkChangeHandle: Subscription? = null
    private lateinit var bridgeManager: BridgeManager

    init {
        config.baseDirectory.requireOnDefaultFileSystem()
    }

    /**
     * The server will make sure the bridge exists on network map changes, see method [updateBridgesOnNetworkChange]
     * We assume network map will be updated accordingly when the client node register with the network map.
     */
    @Throws(IOException::class, KeyStoreException::class)
    fun start() = mutex.locked {
        if (!running) {
            configureAndStartServer()
            networkChangeHandle = networkMapCache.changed.subscribe { updateBridgesOnNetworkChange(it) }
            running = true
        }
    }

    fun stop() = mutex.locked {
        bridgeManager.close()
        networkChangeHandle?.unsubscribe()
        networkChangeHandle = null
        activeMQServer.stop()
        running = false
    }

    // TODO: Maybe wrap [IOException] on a key store load error so that it's clearly splitting key store loading from
    // Artemis IO errors
    @Throws(IOException::class, KeyStoreException::class)
    private fun configureAndStartServer() {
        val (artemisConfig, securityPlugin) = createArtemisConfig()
        val securityManager = createArtemisSecurityManager(securityPlugin)
        activeMQServer = ActiveMQServerImpl(artemisConfig, securityManager).apply {
            // Throw any exceptions which are detected during startup
            registerActivationFailureListener { exception -> throw exception }
            // Some types of queue might need special preparation on our side, like dialling back or preparing
            // a lazily initialised subsystem.
            registerPostQueueCreationCallback { deployBridgesFromNewQueue(it.toString()) }
            registerPostQueueDeletionCallback { address, qName -> log.debug { "Queue deleted: $qName for $address" } }
        }
        // Config driven switch between legacy CORE bridges and the newer AMQP protocol bridges.
        bridgeManager = if (config.useAMQPBridges) {
            AMQPBridgeManager(config, NetworkHostAndPort("localhost", p2pPort), maxMessageSize)
        } else {
            CoreBridgeManager(config, activeMQServer)
        }
        activeMQServer.start()
        bridgeManager.start()
        Node.printBasicNodeInfo("Listening on port", p2pPort.toString())
        if (rpcPort != null) {
            Node.printBasicNodeInfo("RPC service listening on port", rpcPort.toString())
        }
    }

    private fun createArtemisConfig() = ConfigurationImpl().apply {
        val artemisDir = config.baseDirectory / "artemis"
        bindingsDirectory = (artemisDir / "bindings").toString()
        journalDirectory = (artemisDir / "journal").toString()
        largeMessagesDirectory = (artemisDir / "large-messages").toString()
        val connectionDirection = ConnectionDirection.Inbound(
                acceptorFactoryClassName = NettyAcceptorFactory::class.java.name
        )
        val acceptors = mutableSetOf(createTcpTransport(connectionDirection, "0.0.0.0", p2pPort))
        if (rpcPort != null) {
            acceptors.add(createTcpTransport(connectionDirection, "0.0.0.0", rpcPort, enableSSL = false))
        }
        acceptorConfigurations = acceptors
        // Enable built in message deduplication. Note we still have to do our own as the delayed commits
        // and our own definition of commit mean that the built in deduplication cannot remove all duplicates.
        idCacheSize = 2000 // Artemis Default duplicate cache size i.e. a guess
        isPersistIDCache = true
        isPopulateValidatedUser = true
        journalBufferSize_NIO = maxMessageSize // Artemis default is 490KiB - required to address IllegalArgumentException (when Artemis uses Java NIO): Record is too large to store.
        journalBufferSize_AIO = maxMessageSize // Required to address IllegalArgumentException (when Artemis uses Linux Async IO): Record is too large to store.
        journalFileSize = maxMessageSize // The size of each journal file in bytes. Artemis default is 10MiB.
        managementNotificationAddress = SimpleString(NOTIFICATIONS_ADDRESS)
        // Artemis allows multiple servers to be grouped together into a cluster for load balancing purposes. The cluster
        // user is used for connecting the nodes together. It has super-user privileges and so it's imperative that its
        // password be changed from the default (as warned in the docs). Since we don't need this feature we turn it off
        // by having its password be an unknown securely random 128-bit value.
        clusterPassword = BigInteger(128, newSecureRandom()).toString(16)
        queueConfigurations = listOf(
                queueConfig(P2P_QUEUE, durable = true),
                // Create an RPC queue: this will service locally connected clients only (not via a bridge) and those
                // clients must have authenticated. We could use a single consumer for everything and perhaps we should,
                // but these queues are not worth persisting.
                queueConfig(RPCApi.RPC_SERVER_QUEUE_NAME, durable = false),
                queueConfig(
                        name = RPCApi.RPC_CLIENT_BINDING_REMOVALS,
                        address = NOTIFICATIONS_ADDRESS,
                        filter = RPCApi.RPC_CLIENT_BINDING_REMOVAL_FILTER_EXPRESSION,
                        durable = false
                ),
                queueConfig(
                        name = RPCApi.RPC_CLIENT_BINDING_ADDITIONS,
                        address = NOTIFICATIONS_ADDRESS,
                        filter = RPCApi.RPC_CLIENT_BINDING_ADDITION_FILTER_EXPRESSION,
                        durable = false
                )
        )
        addressesSettings = mapOf(
                "${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.#" to AddressSettings().apply {
                    maxSizeBytes = 10L * maxMessageSize
                    addressFullMessagePolicy = AddressFullMessagePolicy.FAIL
                }
        )
        // JMX enablement
        if (config.exportJMXto.isNotEmpty()) {
            isJMXManagementEnabled = true
            isJMXUseBrokerName = true
        }

    }.configureAddressSecurity()


    private fun queueConfig(name: String, address: String = name, filter: String? = null, durable: Boolean): CoreQueueConfiguration {
        return CoreQueueConfiguration().apply {
            this.name = name
            this.address = address
            filterString = filter
            isDurable = durable
        }
    }

    /**
     * Authenticated clients connecting to us fall in one of the following groups:
     * 1. The node itself. It is given full access to all valid queues.
     * 2. Peers on the same network as us. These are only given permission to send to our P2P inbound queue.
     * 3. RPC users. These are only given sufficient access to perform RPC with us.
     * 4. Verifiers. These are given read access to the verification request queue and write access to the response queue.
     */
    private fun ConfigurationImpl.configureAddressSecurity(): Pair<Configuration, LoginListener> {
        val nodeInternalRole = Role(NODE_ROLE, true, true, true, true, true, true, true, true)
        securityRoles["$INTERNAL_PREFIX#"] = setOf(nodeInternalRole)  // Do not add any other roles here as it's only for the node
        securityRoles[P2P_QUEUE] = setOf(nodeInternalRole, restrictedRole(PEER_ROLE, send = true))
        securityRoles[RPCApi.RPC_SERVER_QUEUE_NAME] = setOf(nodeInternalRole, restrictedRole(RPC_ROLE, send = true))
        // Each RPC user must have its own role and its own queue. This prevents users accessing each other's queues
        // and stealing RPC responses.
        val rolesAdderOnLogin = RolesAdderOnLogin { username ->
            Pair(
                    "${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.$username.#",
                    setOf(
                            nodeInternalRole,
                            restrictedRole(
                                    "${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.$username",
                                    consume = true,
                                    createNonDurableQueue = true,
                                    deleteNonDurableQueue = true)))
        }
        securitySettingPlugins.add(rolesAdderOnLogin)
        securityRoles[VerifierApi.VERIFICATION_REQUESTS_QUEUE_NAME] = setOf(nodeInternalRole, restrictedRole(VERIFIER_ROLE, consume = true))
        securityRoles["${VerifierApi.VERIFICATION_RESPONSES_QUEUE_NAME_PREFIX}.#"] = setOf(nodeInternalRole, restrictedRole(VERIFIER_ROLE, send = true))
        val onLoginListener = { username: String -> rolesAdderOnLogin.onLogin(username) }
        return Pair(this, onLoginListener)
    }

    private fun restrictedRole(name: String, send: Boolean = false, consume: Boolean = false, createDurableQueue: Boolean = false,
                               deleteDurableQueue: Boolean = false, createNonDurableQueue: Boolean = false,
                               deleteNonDurableQueue: Boolean = false, manage: Boolean = false, browse: Boolean = false): Role {
        return Role(name, send, consume, createDurableQueue, deleteDurableQueue, createNonDurableQueue,
                deleteNonDurableQueue, manage, browse)
    }

    @Throws(IOException::class, KeyStoreException::class)
    private fun createArtemisSecurityManager(loginListener: LoginListener): ActiveMQJAASSecurityManager {
        val keyStore = loadKeyStore(config.sslKeystore, config.keyStorePassword)
        val trustStore = loadKeyStore(config.trustStoreFile, config.trustStorePassword)

        val defaultCertPolicies = mapOf(
                PEER_ROLE to CertificateChainCheckPolicy.RootMustMatch,
                NODE_ROLE to CertificateChainCheckPolicy.LeafMustMatch,
                VERIFIER_ROLE to CertificateChainCheckPolicy.RootMustMatch
        )
        val certChecks = defaultCertPolicies.mapValues { (role, defaultPolicy) ->
            val configPolicy = config.certificateChainCheckPolicies.noneOrSingle { it.role == role }?.certificateChainCheckPolicy
            (configPolicy ?: defaultPolicy).createCheck(keyStore, trustStore)
        }
        val securityConfig = object : SecurityConfiguration() {
            // Override to make it work with our login module
            override fun getAppConfigurationEntry(name: String): Array<AppConfigurationEntry> {
                val options = mapOf(
                        LoginListener::javaClass.name to loginListener,
                        RPCSecurityManager::class.java.name to securityManager,
                        NodeLoginModule.CERT_CHAIN_CHECKS_OPTION_NAME to certChecks)
                return arrayOf(AppConfigurationEntry(name, REQUIRED, options))
            }
        }
        return ActiveMQJAASSecurityManager(NodeLoginModule::class.java.name, securityConfig)
    }

    private fun deployBridgesFromNewQueue(queueName: String) {
        log.debug { "Queue created: $queueName, deploying bridge(s)" }
        fun deployBridgeToPeer(nodeInfo: NodeInfo) {
            log.debug("Deploying bridge for $queueName to $nodeInfo")
            val address = nodeInfo.addresses.first()
            bridgeManager.deployBridge(queueName, address, nodeInfo.legalIdentitiesAndCerts.map { it.name }.toSet())
        }

        if (queueName.startsWith(PEERS_PREFIX)) {
            try {
                val identity = parsePublicKeyBase58(queueName.substring(PEERS_PREFIX.length))
                val nodeInfos = networkMapCache.getNodesByLegalIdentityKey(identity)
                if (nodeInfos.isNotEmpty()) {
                    nodeInfos.forEach { deployBridgeToPeer(it) }
                } else {
                    log.error("Queue created for a peer that we don't know from the network map: $queueName")
                }
            } catch (e: AddressFormatException) {
                log.error("Flow violation: Could not parse peer queue name as Base 58: $queueName")
            }
        }
    }

    /**
     * The bridge will be created automatically when the queues are created, however, this is not the case when the network map restarted.
     * The queues are restored from the journal, and because the queues are added before we register the callback handler, this method will never get called for existing queues.
     * This results in message queues up and never get send out. (https://github.com/corda/corda/issues/37)
     *
     * We create the bridges indirectly now because the network map is not persisted and there are no ways to obtain host and port information on startup.
     * TODO : Create the bridge directly from the list of queues on start up when we have a persisted network map service.
     */
    private fun updateBridgesOnNetworkChange(change: MapChange) {
        log.debug { "Updating bridges on network map change: ${change.node}" }
        fun gatherAddresses(node: NodeInfo): Sequence<ArtemisPeerAddress> {
            val address = node.addresses.first()
            return node.legalIdentitiesAndCerts.map { NodeAddress(it.party.owningKey, address) }.asSequence()
        }

        fun deployBridges(node: NodeInfo) {
            gatherAddresses(node)
                    .filter { queueExists(it.queueName) && !bridgeManager.bridgeExists(it.bridgeName) }
                    .forEach { deployBridge(it, node.legalIdentitiesAndCerts.map { it.name }.toSet()) }
        }

        when (change) {
            is MapChange.Added -> {
                deployBridges(change.node)
            }
            is MapChange.Removed -> {
                bridgeManager.destroyBridges(change.node)
            }
            is MapChange.Modified -> {
                // TODO Figure out what has actually changed and only destroy those bridges that need to be.
                bridgeManager.destroyBridges(change.previousNode)
                deployBridges(change.node)
            }
        }
    }

    private fun deployBridge(address: ArtemisPeerAddress, legalNames: Set<CordaX500Name>) {
        bridgeManager.deployBridge(address.queueName, address.hostAndPort, legalNames)
    }

    private fun createTcpTransport(connectionDirection: ConnectionDirection, host: String, port: Int, enableSSL: Boolean = true) =
            ArtemisTcpTransport.tcpTransport(connectionDirection, NetworkHostAndPort(host, port), config, enableSSL = enableSSL)

    private fun queueExists(queueName: String): Boolean = activeMQServer.queueQuery(SimpleString(queueName)).isExists

    private val ArtemisPeerAddress.bridgeName: String get() = getBridgeName(queueName, hostAndPort)

    private fun getBridgeName(queueName: String, hostAndPort: NetworkHostAndPort): String = "$queueName -> $hostAndPort"
}

sealed class CertificateChainCheckPolicy {

    @FunctionalInterface
    interface Check {
        fun checkCertificateChain(theirChain: Array<javax.security.cert.X509Certificate>)
    }

    abstract fun createCheck(keyStore: KeyStore, trustStore: KeyStore): Check

    object Any : CertificateChainCheckPolicy() {
        override fun createCheck(keyStore: KeyStore, trustStore: KeyStore): Check {
            return object : Check {
                override fun checkCertificateChain(theirChain: Array<javax.security.cert.X509Certificate>) {
                }
            }
        }
    }

    object RootMustMatch : CertificateChainCheckPolicy() {
        override fun createCheck(keyStore: KeyStore, trustStore: KeyStore): Check {
            val rootPublicKey = trustStore.getCertificate(CORDA_ROOT_CA).publicKey
            return object : Check {
                override fun checkCertificateChain(theirChain: Array<javax.security.cert.X509Certificate>) {
                    val theirRoot = theirChain.last().publicKey
                    if (rootPublicKey != theirRoot) {
                        throw CertificateException("Root certificate mismatch, their root = $theirRoot")
                    }
                }
            }
        }
    }

    object LeafMustMatch : CertificateChainCheckPolicy() {
        override fun createCheck(keyStore: KeyStore, trustStore: KeyStore): Check {
            val ourPublicKey = keyStore.getCertificate(CORDA_CLIENT_TLS).publicKey
            return object : Check {
                override fun checkCertificateChain(theirChain: Array<javax.security.cert.X509Certificate>) {
                    val theirLeaf = theirChain.first().publicKey
                    if (ourPublicKey != theirLeaf) {
                        throw CertificateException("Leaf certificate mismatch, their leaf = $theirLeaf")
                    }
                }
            }
        }
    }

    data class MustContainOneOf(val trustedAliases: Set<String>) : CertificateChainCheckPolicy() {
        override fun createCheck(keyStore: KeyStore, trustStore: KeyStore): Check {
            val trustedPublicKeys = trustedAliases.map { trustStore.getCertificate(it).publicKey }.toSet()
            return object : Check {
                override fun checkCertificateChain(theirChain: Array<javax.security.cert.X509Certificate>) {
                    if (!theirChain.any { it.publicKey in trustedPublicKeys }) {
                        throw CertificateException("Their certificate chain contained none of the trusted ones")
                    }
                }
            }
        }
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
        const val VERIFIER_ROLE = "SystemRoles/Verifier"

        const val CERT_CHAIN_CHECKS_OPTION_NAME = "CertChainChecks"
        private val log = contextLogger()
    }

    private var loginSucceeded: Boolean = false
    private lateinit var subject: Subject
    private lateinit var callbackHandler: CallbackHandler
    private lateinit var securityManager: RPCSecurityManager
    private lateinit var loginListener: LoginListener
    private lateinit var peerCertCheck: CertificateChainCheckPolicy.Check
    private lateinit var nodeCertCheck: CertificateChainCheckPolicy.Check
    private lateinit var verifierCertCheck: CertificateChainCheckPolicy.Check
    private val principals = ArrayList<Principal>()

    override fun initialize(subject: Subject, callbackHandler: CallbackHandler, sharedState: Map<String, *>, options: Map<String, *>) {
        this.subject = subject
        this.callbackHandler = callbackHandler
        securityManager = options[RPCSecurityManager::class.java.name] as RPCSecurityManager
        loginListener = options[LoginListener::javaClass.name] as LoginListener
        val certChainChecks: Map<String, CertificateChainCheckPolicy.Check> = uncheckedCast(options[CERT_CHAIN_CHECKS_OPTION_NAME])
        peerCertCheck = certChainChecks[PEER_ROLE]!!
        nodeCertCheck = certChainChecks[NODE_ROLE]!!
        verifierCertCheck = certChainChecks[VERIFIER_ROLE]!!
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
        val certificates = certificateCallback.certificates

        log.debug { "Processing login for $username" }

        try {
            val validatedUser = when (determineUserRole(certificates, username)) {
                PEER_ROLE -> authenticatePeer(certificates)
                NODE_ROLE -> authenticateNode(certificates)
                VERIFIER_ROLE -> authenticateVerifier(certificates)
                RPC_ROLE -> authenticateRpcUser(username, Password(password))
                else -> throw FailedLoginException("Peer does not belong on our network")
            }
            principals += UserPrincipal(validatedUser)

            loginSucceeded = true
            return loginSucceeded
        } catch (exception: FailedLoginException) {
            log.warn("$exception")
            throw exception
        }
    }

    private fun authenticateNode(certificates: Array<javax.security.cert.X509Certificate>): String {
        nodeCertCheck.checkCertificateChain(certificates)
        principals += RolePrincipal(NODE_ROLE)
        return certificates.first().subjectDN.name
    }

    private fun authenticateVerifier(certificates: Array<javax.security.cert.X509Certificate>): String {
        verifierCertCheck.checkCertificateChain(certificates)
        principals += RolePrincipal(VERIFIER_ROLE)
        return certificates.first().subjectDN.name
    }

    private fun authenticatePeer(certificates: Array<javax.security.cert.X509Certificate>): String {
        peerCertCheck.checkCertificateChain(certificates)
        principals += RolePrincipal(PEER_ROLE)
        return certificates.first().subjectDN.name
    }

    private fun authenticateRpcUser(username: String, password: Password): String {
        securityManager.authenticate(username, password)
        loginListener(username)
        principals += RolePrincipal(RPC_ROLE)  // This enables the RPC client to send requests
        principals += RolePrincipal("${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.$username")  // This enables the RPC client to receive responses
        return username
    }

    private fun determineUserRole(certificates: Array<javax.security.cert.X509Certificate>?, username: String): String? {
        fun requireTls() = require(certificates != null) { "No TLS?" }
        return when (username) {
            PEER_USER -> {
                requireTls()
                PEER_ROLE
            }
            NODE_USER -> {
                requireTls()
                NODE_ROLE
            }
            VerifierApi.VERIFIER_USERNAME -> {
                requireTls()
                VERIFIER_ROLE
            }
            else -> {
                // Assume they're an RPC user if its from a non-ssl connection
                if (certificates == null) {
                    RPC_ROLE
                } else {
                    null
                }
            }
        }
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

typealias LoginListener = (String) -> Unit
typealias RolesRepository = HierarchicalRepository<MutableSet<Role>>

/**
 * Helper class to dynamically assign security roles to RPC users
 * on their authentication. This object is plugged into the server
 * as [SecuritySettingPlugin]. It responds to authentication events
 * from [NodeLoginModule] by adding the address -> roles association
 * generated by the given [source], unless already done before.
 */
private class RolesAdderOnLogin(val source: (String) -> Pair<String, Set<Role>>)
    : SecuritySettingPlugin {

    // Artemis internal container storing roles association
    private lateinit var repository: RolesRepository

    fun onLogin(username: String) {
        val (address, roles) = source(username)
        val entry = repository.getMatch(address)
        if (entry == null || entry.isEmpty()) {
            repository.addMatch(address, roles.toMutableSet())
        }
    }

    // Initializer called by the Artemis framework
    override fun setSecurityRepository(repository: RolesRepository) {
        this.repository = repository
    }

    // Part of SecuritySettingPlugin interface which is no-op in this case
    override fun stop() = this

    override fun init(options: MutableMap<String, String>?) = this

    override fun getSecurityRoles() = null
}

package net.corda.node.services.messaging

import com.google.common.util.concurrent.ListenableFuture
import io.netty.handler.ssl.SslHandler
import net.corda.core.*
import net.corda.core.concurrent.CordaFuture
import net.corda.core.concurrent.openFuture
import net.corda.core.crypto.*
import net.corda.core.crypto.X509Utilities.CORDA_CLIENT_TLS
import net.corda.core.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.NetworkMapCache.MapChange
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.node.internal.Node
import net.corda.node.services.RPCUserService
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.NodeLoginModule.Companion.NODE_ROLE
import net.corda.node.services.messaging.NodeLoginModule.Companion.PEER_ROLE
import net.corda.node.services.messaging.NodeLoginModule.Companion.RPC_ROLE
import net.corda.node.services.messaging.NodeLoginModule.Companion.VERIFIER_ROLE
import net.corda.nodeapi.*
import net.corda.nodeapi.ArtemisMessagingComponent.Companion.NODE_USER
import net.corda.nodeapi.ArtemisMessagingComponent.Companion.PEER_USER
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl
import org.apache.activemq.artemis.core.config.BridgeConfiguration
import org.apache.activemq.artemis.core.config.Configuration
import org.apache.activemq.artemis.core.config.CoreQueueConfiguration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.core.message.impl.CoreMessage
import org.apache.activemq.artemis.core.remoting.impl.netty.*
import org.apache.activemq.artemis.core.security.Role
import org.apache.activemq.artemis.core.server.ActiveMQServer
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl
import org.apache.activemq.artemis.core.server.impl.RoutingContextImpl
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy
import org.apache.activemq.artemis.core.settings.impl.AddressSettings
import org.apache.activemq.artemis.spi.core.remoting.*
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import org.apache.activemq.artemis.spi.core.security.jaas.CertificateCallback
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal
import org.apache.activemq.artemis.utils.ConfigurationHelper
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import rx.Subscription
import java.io.IOException
import java.math.BigInteger
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.Principal
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService
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
import javax.security.cert.X509Certificate

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
                             val p2pPort: Int,
                             val rpcPort: Int?,
                             val networkMapCache: NetworkMapCache,
                             val userService: RPCUserService) : ArtemisMessagingComponent() {
    companion object {
        private val log = loggerFor<ArtemisMessagingServer>()
        /** 10 MiB maximum allowed file size for attachments, including message headers. TODO: acquire this value from Network Map when supported. */
        @JvmStatic val MAX_FILE_SIZE = 10485760

        val ipDetectRequestProperty = "ip-request-id"
        val ipDetectResponseProperty = "ip-address"
    }

    private class InnerState {
        var running = false
    }

    private val mutex = ThreadBox(InnerState())
    private lateinit var activeMQServer: ActiveMQServer
    val serverControl: ActiveMQServerControl get() = activeMQServer.activeMQServerControl
    private val _networkMapConnectionFuture = config.networkMapService?.let { openFuture<Unit>() }
    /**
     * A [ListenableFuture] which completes when the server successfully connects to the network map node. If a
     * non-recoverable error is encountered then the Future will complete with an exception.
     */
    val networkMapConnectionFuture: CordaFuture<Unit>? get() = _networkMapConnectionFuture
    private var networkChangeHandle: Subscription? = null
    private val nodeRunsNetworkMapService = config.networkMapService == null

    init {
        config.baseDirectory.expectedOnDefaultFileSystem()
    }

    /**
     * The server will make sure the bridge exists on network map changes, see method [updateBridgesOnNetworkChange]
     * We assume network map will be updated accordingly when the client node register with the network map server.
     */
    @Throws(IOException::class, KeyStoreException::class)
    fun start() = mutex.locked {
        if (!running) {
            configureAndStartServer()
            // Deploy bridge to the network map service
            config.networkMapService?.let { deployBridge(NetworkMapAddress(it.address), it.legalName) }
            networkChangeHandle = networkMapCache.changed.subscribe { updateBridgesOnNetworkChange(it) }
            running = true
        }
    }

    fun stop() = mutex.locked {
        networkChangeHandle?.unsubscribe()
        networkChangeHandle = null
        activeMQServer.stop()
        running = false
    }

    // TODO: Maybe wrap [IOException] on a key store load error so that it's clearly splitting key store loading from
    // Artemis IO errors
    @Throws(IOException::class, KeyStoreException::class)
    private fun configureAndStartServer() {
        val artemisConfig = createArtemisConfig()
        val securityManager = createArtemisSecurityManager()
        activeMQServer = ActiveMQServerImpl(artemisConfig, securityManager).apply {
            // Throw any exceptions which are detected during startup
            registerActivationFailureListener { exception -> throw exception }
            // Some types of queue might need special preparation on our side, like dialling back or preparing
            // a lazily initialised subsystem.
            registerPostQueueCreationCallback { deployBridgesFromNewQueue(it.toString()) }
            if (nodeRunsNetworkMapService) registerPostQueueCreationCallback { handleIpDetectionRequest(it.toString()) }
            registerPostQueueDeletionCallback { address, qName -> log.debug { "Queue deleted: $qName for $address" } }
        }
        activeMQServer.start()
        Node.printBasicNodeInfo("Listening on port", p2pPort.toString())
        if (rpcPort != null) {
            Node.printBasicNodeInfo("RPC service listening on port", rpcPort.toString())
        }
    }

    private fun createArtemisConfig(): Configuration = ConfigurationImpl().apply {
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
        journalBufferSize_NIO = MAX_FILE_SIZE // Artemis default is 490KiB - required to address IllegalArgumentException (when Artemis uses Java NIO): Record is too large to store.
        journalBufferSize_AIO = MAX_FILE_SIZE // Required to address IllegalArgumentException (when Artemis uses Linux Async IO): Record is too large to store.
        journalFileSize = MAX_FILE_SIZE // The size of each journal file in bytes. Artemis default is 10MiB.
        managementNotificationAddress = SimpleString(NOTIFICATIONS_ADDRESS)
        // Artemis allows multiple servers to be grouped together into a cluster for load balancing purposes. The cluster
        // user is used for connecting the nodes together. It has super-user privileges and so it's imperative that its
        // password be changed from the default (as warned in the docs). Since we don't need this feature we turn it off
        // by having its password be an unknown securely random 128-bit value.
        clusterPassword = BigInteger(128, newSecureRandom()).toString(16)
        queueConfigurations = listOf(
                queueConfig(NETWORK_MAP_QUEUE, durable = true),
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
                    maxSizeBytes = 10L * MAX_FILE_SIZE
                    addressFullMessagePolicy = AddressFullMessagePolicy.FAIL
                }
        )
        configureAddressSecurity()
    }

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
    private fun ConfigurationImpl.configureAddressSecurity() {
        val nodeInternalRole = Role(NODE_ROLE, true, true, true, true, true, true, true, true)
        securityRoles["$INTERNAL_PREFIX#"] = setOf(nodeInternalRole)  // Do not add any other roles here as it's only for the node
        securityRoles[P2P_QUEUE] = setOf(nodeInternalRole, restrictedRole(PEER_ROLE, send = true))
        securityRoles[RPCApi.RPC_SERVER_QUEUE_NAME] = setOf(nodeInternalRole, restrictedRole(RPC_ROLE, send = true))
        // TODO remove the NODE_USER role once the webserver doesn't need it
        securityRoles["${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.$NODE_USER.#"] = setOf(nodeInternalRole)
        if (nodeRunsNetworkMapService) {
            securityRoles["$IP_REQUEST_PREFIX*"] = setOf(
                    nodeInternalRole,
                    restrictedRole(PEER_ROLE, consume = true, createNonDurableQueue = true, deleteNonDurableQueue = true)
            )
        }
        for ((username) in userService.users) {
            securityRoles["${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.$username.#"] = setOf(
                    nodeInternalRole,
                    restrictedRole("${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.$username", consume = true, createNonDurableQueue = true, deleteNonDurableQueue = true))
        }
        securityRoles[VerifierApi.VERIFICATION_REQUESTS_QUEUE_NAME] = setOf(nodeInternalRole, restrictedRole(VERIFIER_ROLE, consume = true))
        securityRoles["${VerifierApi.VERIFICATION_RESPONSES_QUEUE_NAME_PREFIX}.#"] = setOf(nodeInternalRole, restrictedRole(VERIFIER_ROLE, send = true))
    }

    private fun restrictedRole(name: String, send: Boolean = false, consume: Boolean = false, createDurableQueue: Boolean = false,
                               deleteDurableQueue: Boolean = false, createNonDurableQueue: Boolean = false,
                               deleteNonDurableQueue: Boolean = false, manage: Boolean = false, browse: Boolean = false): Role {
        return Role(name, send, consume, createDurableQueue, deleteDurableQueue, createNonDurableQueue,
                deleteNonDurableQueue, manage, browse)
    }

    @Throws(IOException::class, KeyStoreException::class)
    private fun createArtemisSecurityManager(): ActiveMQJAASSecurityManager {
        val keyStore = KeyStoreUtilities.loadKeyStore(config.sslKeystore, config.keyStorePassword)
        val trustStore = KeyStoreUtilities.loadKeyStore(config.trustStoreFile, config.trustStorePassword)
        val ourCertificate = keyStore.getX509Certificate(CORDA_CLIENT_TLS)

        // This is a sanity check and should not fail unless things have been misconfigured
        require(ourCertificate.subject == config.myLegalName) {
            "Legal name does not match with our subject CN: ${ourCertificate.subject}"
        }
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
                        RPCUserService::class.java.name to userService,
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
            val address = nodeInfo.addresses.first() // TODO Load balancing.
            deployBridge(queueName, address, nodeInfo.legalIdentity.name)
        }

        when {
            queueName.startsWith(PEERS_PREFIX) -> try {
                val identity = parsePublicKeyBase58(queueName.substring(PEERS_PREFIX.length))
                val nodeInfo = networkMapCache.getNodeByLegalIdentityKey(identity)
                if (nodeInfo != null) {
                    deployBridgeToPeer(nodeInfo)
                } else {
                    log.error("Queue created for a peer that we don't know from the network map: $queueName")
                }
            } catch (e: AddressFormatException) {
                log.error("Flow violation: Could not parse peer queue name as Base 58: $queueName")
            }

            queueName.startsWith(SERVICES_PREFIX) -> try {
                val identity = parsePublicKeyBase58(queueName.substring(SERVICES_PREFIX.length))
                val nodeInfos = networkMapCache.getNodesByAdvertisedServiceIdentityKey(identity)
                // Create a bridge for each node advertising the service.
                for (nodeInfo in nodeInfos) {
                    deployBridgeToPeer(nodeInfo)
                }
            } catch (e: AddressFormatException) {
                log.error("Flow violation: Could not parse service queue name as Base 58: $queueName")
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
        fun gatherAddresses(node: NodeInfo): Sequence<ArtemisPeerAddress> {
            val peerAddress = getArtemisPeerAddress(node)
            val addresses = mutableListOf(peerAddress)
            node.advertisedServices.mapTo(addresses) { NodeAddress.asService(it.identity.owningKey, peerAddress.hostAndPort) }
            return addresses.asSequence()
        }

        fun deployBridges(node: NodeInfo) {
            gatherAddresses(node)
                    .filter { queueExists(it.queueName) && !bridgeExists(it.bridgeName) }
                    .forEach { deployBridge(it, node.legalIdentity.name) }
        }

        fun destroyBridges(node: NodeInfo) {
            gatherAddresses(node).forEach {
                activeMQServer.destroyBridge(it.bridgeName)
            }
        }

        when (change) {
            is MapChange.Added -> {
                deployBridges(change.node)
            }
            is MapChange.Removed -> {
                destroyBridges(change.node)
            }
            is MapChange.Modified -> {
                // TODO Figure out what has actually changed and only destroy those bridges that need to be.
                destroyBridges(change.previousNode)
                deployBridges(change.node)
            }
        }
    }

    private fun deployBridge(address: ArtemisPeerAddress, legalName: X500Name) {
        deployBridge(address.queueName, address.hostAndPort, legalName)
    }

    private fun createTcpTransport(connectionDirection: ConnectionDirection, host: String, port: Int, enableSSL: Boolean = true) =
            ArtemisTcpTransport.tcpTransport(connectionDirection, NetworkHostAndPort(host, port), config, enableSSL = enableSSL)

    /**
     * All nodes are expected to have a public facing address called [ArtemisMessagingComponent.P2P_QUEUE] for receiving
     * messages from other nodes. When we want to send a message to a node we send it to our internal address/queue for it,
     * as defined by ArtemisAddress.queueName. A bridge is then created to forward messages from this queue to the node's
     * P2P address.
     */
    private fun deployBridge(queueName: String, target: NetworkHostAndPort, legalName: X500Name) {
        val connectionDirection = ConnectionDirection.Outbound(
                connectorFactoryClassName = VerifyingNettyConnectorFactory::class.java.name,
                expectedCommonName = legalName
        )
        val tcpTransport = createTcpTransport(connectionDirection, target.host, target.port)
        tcpTransport.params[ArtemisMessagingServer::class.java.name] = this
        // We intentionally overwrite any previous connector config in case the peer legal name changed
        activeMQServer.configuration.addConnectorConfiguration(target.toString(), tcpTransport)

        activeMQServer.deployBridge(BridgeConfiguration().apply {
            name = getBridgeName(queueName, target)
            this.queueName = queueName
            forwardingAddress = P2P_QUEUE
            staticConnectors = listOf(target.toString())
            confirmationWindowSize = 100000 // a guess
            isUseDuplicateDetection = true // Enable the bridge's automatic deduplication logic
            // We keep trying until the network map deems the node unreachable and tells us it's been removed at which
            // point we destroy the bridge
            // TODO Give some thought to the retry settings
            retryInterval = 5.seconds.toMillis()
            retryIntervalMultiplier = 1.5  // Exponential backoff
            maxRetryInterval = 3.minutes.toMillis()
            // As a peer of the target node we must connect to it using the peer user. Actual authentication is done using
            // our TLS certificate.
            user = PEER_USER
            password = PEER_USER
        })
    }

    private fun queueExists(queueName: String): Boolean = activeMQServer.queueQuery(SimpleString(queueName)).isExists

    private fun bridgeExists(bridgeName: String): Boolean = activeMQServer.clusterManager.bridges.containsKey(bridgeName)

    private val ArtemisPeerAddress.bridgeName: String get() = getBridgeName(queueName, hostAndPort)

    private fun getBridgeName(queueName: String, hostAndPort: NetworkHostAndPort): String = "$queueName -> $hostAndPort"

    // This is called on one of Artemis' background threads
    internal fun hostVerificationFail(expectedLegalName: X500Name, errorMsg: String?) {
        log.error(errorMsg)
        if (expectedLegalName == config.networkMapService?.legalName) {
            // If the peer that failed host verification was the network map node then we're in big trouble and need to bail!
            _networkMapConnectionFuture!!.setException(IOException("${config.networkMapService} failed host verification check"))
        }
    }

    // This is called on one of Artemis' background threads
    internal fun onTcpConnection(peerLegalName: X500Name) {
        if (peerLegalName == config.networkMapService?.legalName) {
            _networkMapConnectionFuture!!.set(Unit)
        }
    }

    private fun handleIpDetectionRequest(queueName: String) {
        fun getRemoteAddress(requestId: String): String? {
            val session = activeMQServer.sessions.first {
                it.getMetaData(ipDetectRequestProperty) == requestId
            }
            return session.remotingConnection.remoteAddress
        }

        fun sendResponse(remoteAddress: String?) {
            val responseMessage = CoreMessage(random63BitValue(), 0).apply {
                putStringProperty(ipDetectResponseProperty, remoteAddress)
            }
            val routingContext = RoutingContextImpl(null)
            val queue = activeMQServer.locateQueue(SimpleString(queueName))
            queue.route(responseMessage, routingContext)
            activeMQServer.postOffice.processRoute(responseMessage, routingContext, true)
        }

        if (!queueName.startsWith(IP_REQUEST_PREFIX)) return
        val requestId = queueName.substringAfter(IP_REQUEST_PREFIX)
        val remoteAddress = getRemoteAddress(requestId)
        log.debug { "Detected remote address $remoteAddress for request $requestId" }
        sendResponse(remoteAddress)
    }
}

class VerifyingNettyConnectorFactory : NettyConnectorFactory() {
    override fun createConnector(configuration: MutableMap<String, Any>,
                                 handler: BufferHandler?,
                                 listener: ClientConnectionLifeCycleListener?,
                                 closeExecutor: Executor?,
                                 threadPool: Executor?,
                                 scheduledThreadPool: ScheduledExecutorService?,
                                 protocolManager: ClientProtocolManager?): Connector {
        return VerifyingNettyConnector(configuration, handler, listener, closeExecutor, threadPool, scheduledThreadPool,
                protocolManager)
    }
}

private class VerifyingNettyConnector(configuration: MutableMap<String, Any>,
                                      handler: BufferHandler?,
                                      listener: ClientConnectionLifeCycleListener?,
                                      closeExecutor: Executor?,
                                      threadPool: Executor?,
                                      scheduledThreadPool: ScheduledExecutorService?,
                                      protocolManager: ClientProtocolManager?) :
        NettyConnector(configuration, handler, listener, closeExecutor, threadPool, scheduledThreadPool, protocolManager) {
    private val server = configuration[ArtemisMessagingServer::class.java.name] as ArtemisMessagingServer
    private val sslEnabled = ConfigurationHelper.getBooleanProperty(TransportConstants.SSL_ENABLED_PROP_NAME, TransportConstants.DEFAULT_SSL_ENABLED, configuration)

    override fun createConnection(): Connection? {
        val connection = super.createConnection() as? NettyConnection
        if (sslEnabled && connection != null) {
            val expectedLegalName = configuration[ArtemisTcpTransport.VERIFY_PEER_LEGAL_NAME] as X500Name
            try {
                val session = connection.channel
                        .pipeline()
                        .get(SslHandler::class.java)
                        .engine()
                        .session
                // Checks the peer name is the one we are expecting.
                val peerLegalName = session.peerPrincipal.name.let(::X500Name)
                require(peerLegalName == expectedLegalName) {
                    "Peer has wrong CN - expected $expectedLegalName but got $peerLegalName. This is either a fatal " +
                            "misconfiguration by the remote peer or an SSL man-in-the-middle attack!"
                }
                // Make sure certificate has the same name.
                val peerCertificate = X509CertificateHolder(session.peerCertificateChain.first().encoded)
                require(peerCertificate.subject == expectedLegalName) {
                    "Peer has wrong subject name in the certificate - expected $expectedLegalName but got ${peerCertificate.subject}. This is either a fatal " +
                            "misconfiguration by the remote peer or an SSL man-in-the-middle attack!"
                }
                X509Utilities.validateCertificateChain(X509CertificateHolder(session.localCertificates.last().encoded), *session.peerCertificates)
                server.onTcpConnection(peerLegalName)
            } catch (e: IllegalArgumentException) {
                connection.close()
                server.hostVerificationFail(expectedLegalName, e.message)
                return null
            }
        }
        return connection
    }
}

sealed class CertificateChainCheckPolicy {

    @FunctionalInterface
    interface Check {
        fun checkCertificateChain(theirChain: Array<X509Certificate>)
    }

    abstract fun createCheck(keyStore: KeyStore, trustStore: KeyStore): Check

    object Any : CertificateChainCheckPolicy() {
        override fun createCheck(keyStore: KeyStore, trustStore: KeyStore): Check {
            return object : Check {
                override fun checkCertificateChain(theirChain: Array<X509Certificate>) {
                }
            }
        }
    }

    object RootMustMatch : CertificateChainCheckPolicy() {
        override fun createCheck(keyStore: KeyStore, trustStore: KeyStore): Check {
            val rootPublicKey = trustStore.getCertificate(CORDA_ROOT_CA).publicKey
            return object : Check {
                override fun checkCertificateChain(theirChain: Array<X509Certificate>) {
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
                override fun checkCertificateChain(theirChain: Array<X509Certificate>) {
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
                override fun checkCertificateChain(theirChain: Array<X509Certificate>) {
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

        val log = loggerFor<NodeLoginModule>()
    }

    private var loginSucceeded: Boolean = false
    private lateinit var subject: Subject
    private lateinit var callbackHandler: CallbackHandler
    private lateinit var userService: RPCUserService
    private lateinit var peerCertCheck: CertificateChainCheckPolicy.Check
    private lateinit var nodeCertCheck: CertificateChainCheckPolicy.Check
    private lateinit var verifierCertCheck: CertificateChainCheckPolicy.Check
    private val principals = ArrayList<Principal>()

    @Suppress("UNCHECKED_CAST")
    override fun initialize(subject: Subject, callbackHandler: CallbackHandler, sharedState: Map<String, *>, options: Map<String, *>) {
        this.subject = subject
        this.callbackHandler = callbackHandler
        userService = options[RPCUserService::class.java.name] as RPCUserService
        val certChainChecks = options[CERT_CHAIN_CHECKS_OPTION_NAME] as Map<String, CertificateChainCheckPolicy.Check>
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
                RPC_ROLE -> authenticateRpcUser(password, username)
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

    private fun authenticateNode(certificates: Array<X509Certificate>): String {
        nodeCertCheck.checkCertificateChain(certificates)
        principals += RolePrincipal(NODE_ROLE)
        return certificates.first().subjectDN.name
    }

    private fun authenticateVerifier(certificates: Array<X509Certificate>): String {
        verifierCertCheck.checkCertificateChain(certificates)
        principals += RolePrincipal(VERIFIER_ROLE)
        return certificates.first().subjectDN.name
    }

    private fun authenticatePeer(certificates: Array<X509Certificate>): String {
        peerCertCheck.checkCertificateChain(certificates)
        principals += RolePrincipal(PEER_ROLE)
        return certificates.first().subjectDN.name
    }

    private fun authenticateRpcUser(password: String, username: String): String {
        val rpcUser = userService.getUser(username) ?: throw FailedLoginException("User does not exist")
        if (password != rpcUser.password) {
            // TODO Switch to hashed passwords
            // TODO Retrieve client IP address to include in exception message
            throw FailedLoginException("Password for user $username does not match")
        }
        principals += RolePrincipal(RPC_ROLE)  // This enables the RPC client to send requests
        principals += RolePrincipal("${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.$username")  // This enables the RPC client to receive responses
        return username
    }

    private fun determineUserRole(certificates: Array<X509Certificate>?, username: String): String? {
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

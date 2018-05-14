package net.corda.node.services.messaging

import net.corda.core.internal.ThreadBox
import net.corda.core.internal.div
import net.corda.core.internal.noneOrSingle
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.internal.artemis.ArtemisBroker
import net.corda.node.internal.artemis.BrokerAddresses
import net.corda.node.internal.artemis.CertificateChainCheckPolicy
import net.corda.node.internal.artemis.SecureArtemisConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.NodeLoginModule.Companion.NODE_ROLE
import net.corda.node.services.messaging.NodeLoginModule.Companion.PEER_ROLE
import net.corda.node.services.messaging.NodeLoginModule.Companion.VERIFIER_ROLE
import net.corda.nodeapi.ArtemisTcpTransport
import net.corda.nodeapi.ConnectionDirection
import net.corda.nodeapi.VerifierApi
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.INTERNAL_PREFIX
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NODE_USER
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NOTIFICATIONS_ADDRESS
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2P_PREFIX
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.PEER_USER
import net.corda.nodeapi.internal.requireOnDefaultFileSystem
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl
import org.apache.activemq.artemis.core.config.Configuration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory
import org.apache.activemq.artemis.core.security.Role
import org.apache.activemq.artemis.core.server.ActiveMQServer
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import org.apache.activemq.artemis.spi.core.security.jaas.CertificateCallback
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal
import java.io.IOException
import java.security.KeyStoreException
import java.security.Principal
import java.util.*
import javax.annotation.concurrent.ThreadSafe
import javax.security.auth.Subject
import javax.security.auth.callback.CallbackHandler
import javax.security.auth.callback.NameCallback
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
class ArtemisMessagingServer(private val config: NodeConfiguration,
                             private val messagingServerAddress: NetworkHostAndPort,
                             val maxMessageSize: Int) : ArtemisBroker, SingletonSerializeAsToken() {
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
        running = false
    }

    override val addresses = config.p2pAddress.let { BrokerAddresses(it, it) }

    override val started: Boolean
        get() = activeMQServer.isStarted

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
            registerPostQueueCreationCallback { log.debug { "Queue Created: $it" } }
            registerPostQueueDeletionCallback { address, qName -> log.debug { "Queue deleted: $qName for $address" } }
        }
        // Config driven switch between legacy CORE bridges and the newer AMQP protocol bridges.
        activeMQServer.start()
        log.info("P2P messaging server listening on $messagingServerAddress")
    }

    private fun createArtemisConfig() = SecureArtemisConfiguration().apply {
        val artemisDir = config.baseDirectory / "artemis"
        bindingsDirectory = (artemisDir / "bindings").toString()
        journalDirectory = (artemisDir / "journal").toString()
        largeMessagesDirectory = (artemisDir / "large-messages").toString()
        val connectionDirection = ConnectionDirection.Inbound(
                acceptorFactoryClassName = NettyAcceptorFactory::class.java.name
        )
        val acceptors = mutableSetOf(createTcpTransport(connectionDirection, messagingServerAddress.host, messagingServerAddress.port))
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

        // JMX enablement
        if (config.jmxMonitoringHttpPort != null) {
            isJMXManagementEnabled = true
            isJMXUseBrokerName = true
        }

    }.configureAddressSecurity()

    /**
     * Authenticated clients connecting to us fall in one of the following groups:
     * 1. The node itself. It is given full access to all valid queues.
     * 2. Peers on the same network as us. These are only given permission to send to our P2P inbound queue.
     * 3. RPC users. These are only given sufficient access to perform RPC with us.
     * 4. Verifiers. These are given read access to the verification request queue and write access to the response queue.
     */
    private fun ConfigurationImpl.configureAddressSecurity(): Configuration {
        val nodeInternalRole = Role(NODE_ROLE, true, true, true, true, true, true, true, true, true, true)
        securityRoles["$INTERNAL_PREFIX#"] = setOf(nodeInternalRole)  // Do not add any other roles here as it's only for the node
        securityRoles["$P2P_PREFIX#"] = setOf(nodeInternalRole, restrictedRole(PEER_ROLE, send = true))
        securityRoles[VerifierApi.VERIFICATION_REQUESTS_QUEUE_NAME] = setOf(nodeInternalRole, restrictedRole(VERIFIER_ROLE, consume = true))
        securityRoles["${VerifierApi.VERIFICATION_RESPONSES_QUEUE_NAME_PREFIX}.#"] = setOf(nodeInternalRole, restrictedRole(VERIFIER_ROLE, send = true))
        return this
    }

    private fun restrictedRole(name: String, send: Boolean = false, consume: Boolean = false, createDurableQueue: Boolean = false,
                               deleteDurableQueue: Boolean = false, createNonDurableQueue: Boolean = false,
                               deleteNonDurableQueue: Boolean = false, manage: Boolean = false, browse: Boolean = false): Role {
        return Role(name, send, consume, createDurableQueue, deleteDurableQueue, createNonDurableQueue,
                deleteNonDurableQueue, manage, browse, createDurableQueue || createNonDurableQueue, deleteDurableQueue || deleteNonDurableQueue)
    }

    @Throws(IOException::class, KeyStoreException::class)
    private fun createArtemisSecurityManager(): ActiveMQJAASSecurityManager {
        val keyStore = config.loadSslKeyStore().internal
        val trustStore = config.loadTrustStore().internal

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
                val options = mapOf(NodeLoginModule.CERT_CHAIN_CHECKS_OPTION_NAME to certChecks)
                return arrayOf(AppConfigurationEntry(name, REQUIRED, options))
            }
        }
        return ActiveMQJAASSecurityManager(NodeLoginModule::class.java.name, securityConfig)
    }

    private fun createTcpTransport(connectionDirection: ConnectionDirection, host: String, port: Int, enableSSL: Boolean = true) =
            ArtemisTcpTransport.tcpTransport(connectionDirection, NetworkHostAndPort(host, port), config, enableSSL = enableSSL)
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
        const val VERIFIER_ROLE = "SystemRoles/Verifier"

        const val CERT_CHAIN_CHECKS_OPTION_NAME = "CertChainChecks"
        private val log = contextLogger()
    }

    private var loginSucceeded: Boolean = false
    private lateinit var subject: Subject
    private lateinit var callbackHandler: CallbackHandler
    private lateinit var peerCertCheck: CertificateChainCheckPolicy.Check
    private lateinit var nodeCertCheck: CertificateChainCheckPolicy.Check
    private lateinit var verifierCertCheck: CertificateChainCheckPolicy.Check
    private val principals = ArrayList<Principal>()

    override fun initialize(subject: Subject, callbackHandler: CallbackHandler, sharedState: Map<String, *>, options: Map<String, *>) {
        this.subject = subject
        this.callbackHandler = callbackHandler
        val certChainChecks: Map<String, CertificateChainCheckPolicy.Check> = uncheckedCast(options[CERT_CHAIN_CHECKS_OPTION_NAME])
        peerCertCheck = certChainChecks[PEER_ROLE]!!
        nodeCertCheck = certChainChecks[NODE_ROLE]!!
        verifierCertCheck = certChainChecks[VERIFIER_ROLE]!!
    }

    override fun login(): Boolean {
        val nameCallback = NameCallback("Username: ")
        val certificateCallback = CertificateCallback()
        try {
            callbackHandler.handle(arrayOf(nameCallback, certificateCallback))
        } catch (e: IOException) {
            throw LoginException(e.message)
        } catch (e: UnsupportedCallbackException) {
            throw LoginException("${e.message} not available to obtain information from user")
        }

        val username = nameCallback.name ?: throw FailedLoginException("Username not provided")
        val certificates = certificateCallback.certificates

        log.debug { "Processing login for $username" }

        try {
            val validatedUser = when (determineUserRole(certificates, username)) {
                PEER_ROLE -> authenticatePeer(certificates)
                NODE_ROLE -> authenticateNode(certificates)
                VERIFIER_ROLE -> authenticateVerifier(certificates)
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
            else -> null
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
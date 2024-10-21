package net.corda.node.internal.artemis

import net.corda.core.internal.uncheckedCast
import net.corda.core.utilities.contextLogger
import net.corda.node.internal.security.Password
import net.corda.node.internal.security.RPCSecurityManager
import net.corda.node.services.rpc.LoginListener
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfig
import org.apache.activemq.artemis.spi.core.security.jaas.CertificateCallback
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal
import java.io.IOException
import java.security.KeyStore
import java.security.Principal
import java.security.cert.X509Certificate
import java.util.*
import javax.security.auth.Subject
import javax.security.auth.callback.CallbackHandler
import javax.security.auth.callback.NameCallback
import javax.security.auth.callback.PasswordCallback
import javax.security.auth.callback.UnsupportedCallbackException
import javax.security.auth.login.FailedLoginException
import javax.security.auth.login.LoginException
import javax.security.auth.spi.LoginModule

/**
 *
 * The Participants in the system are "The current node", "Peer nodes", "The Artemis P2P broker", "RPC clients", "The Artemis RPC broker".
 *
 * These participants need to communicate and authenticate each other.
 *
 * Peer Nodes must use TLS when connecting to the current node's P2P broker.
 *
 * RPC Clients may use TLS, and need to provide a username/password.
 *
 * Note that the "login" method is called after the SSL handshake was successful.
 * Based on the provided username, we execute extra authentication logic based on the presented client certificates or the username/password:
 *
 * * If someone connects with [PEER_USER] then we confirm they belong on our P2P network by checking their root CA is
 * the same as our root CA. If that's the case, the only access they're given is the ability to send to our P2P address.
 * The messages these authenticated nodes send to us are tagged with their subject DN.
 *
 * * If someone connects with [NODE_P2P_USER] or [NODE_RPC_USER] then we confirm it's the current node by checking their TLS certificate
 * is the same as our one in our key store. Then they're given full access to all valid queues.
 *
 * * Otherwise, if the username is neither of the above, we assume it's an RPC user and authenticate against our list of valid RPC users.
 * RPC clients are given permission to perform RPC and nothing else.
 *
 *
 * Basically, our security policy is to use some hardcoded usernames as discriminators to determine what checks to run on the presented client certificates,
 * and after the checks pass, what roles to assign.
 *
 * The node starts one broker for RPC and one broker for P2P.
 *
 * The P2P broker has only 1 acceptor to which only clients that have a doorman signed certificate can connect.
 * If a Peer attempts to connect as the current node, it would fail because it can't present the node's certificate.
 *
 * The RPC broker has 2 acceptors:
 * * The Admin acceptor to which only the current node should connect. This port should be blocked to any outside connection.
 * To connect as the current Node, you need to present the nodes ssl certificate.
 * Theoretically, if the port is open, any Peer Node could connect to this endpoint, but to actually run commands it would have to know a valid rpc username/password.
 * If the connection was made as [NODE_RPC_USER], the client would have to present the owner node's certificate.
 * * The RPC client acceptor. This can be configured to use SSL. The authentication is based on username/password.
 *
 */
class BrokerJaasLoginModule : BaseBrokerJaasLoginModule() {
    companion object {
        const val PEER_ROLE = "SystemRoles/Peer"
        const val NODE_P2P_ROLE = "SystemRoles/NodeP2P"
        const val NODE_RPC_ROLE = "SystemRoles/NodeRPC"
        const val RPC_ROLE = "SystemRoles/RPC"

        internal val RPC_SECURITY_CONFIG = "RPC_SECURITY_CONFIG"
        internal val P2P_SECURITY_CONFIG = "P2P_SECURITY_CONFIG"
        internal val NODE_SECURITY_CONFIG = "NODE_SECURITY_CONFIG"

        private val log = contextLogger()
    }

    private lateinit var nodeJaasConfig: NodeJaasConfig
    private var rpcJaasConfig: RPCJaasConfig? = null
    private var p2pJaasConfig: P2PJaasConfig? = null

    override fun initialize(subject: Subject, callbackHandler: CallbackHandler, sharedState: Map<String, *>, options: Map<String, *>) {
        super.initialize(subject, callbackHandler, sharedState, options)

        nodeJaasConfig = uncheckedCast(options[NODE_SECURITY_CONFIG])
        p2pJaasConfig = uncheckedCast(options[P2P_SECURITY_CONFIG])
        rpcJaasConfig = uncheckedCast(options[RPC_SECURITY_CONFIG])
    }

    override fun login(): Boolean {
        try {
            val (username, password, certificates) = getUsernamePasswordAndCerts()
            log.debug("Processing login for $username")

            val userAndRoles = authenticateAndAuthorise(username, certificates, password)
            principals += UserPrincipal(userAndRoles.first)
            principals += userAndRoles.second

            log.debug("Login for $username succeeded")
            loginSucceeded = true
            return true
        } catch (e: Exception) {
            // This is a known problem, so we swallow this exception. A peer will attempt to connect without presenting client certificates during SASL
            if (e is IllegalArgumentException && e.stackTrace.any { it.className == "org.apache.activemq.artemis.protocol.amqp.sasl.PlainSASL" }) {
                log.trace("SASL Login failed.")
            } else {
                log.warn("Login failed: ${e.message}")
            }
            if (e is LoginException) {
                throw e
            } else {
                throw FailedLoginException(e.message)
            }
        }
    }

    // The Main authentication logic, responsible for running all the configured checks for each user type
    // and return the actual User and principals
    private fun authenticateAndAuthorise(username: String, certificates: Array<X509Certificate>, password: String): Pair<String, List<RolePrincipal>> {
        fun requireTls(certificates: Array<X509Certificate>?) = requireNotNull(certificates) { "No client certificates presented." }

        return when (username) {
            ArtemisMessagingComponent.NODE_P2P_USER -> {
                requireTls(certificates)
                CertificateChainCheckPolicy.LeafMustMatch.createCheck(nodeJaasConfig.keyStore, nodeJaasConfig.trustStore).checkCertificateChain(certificates)
                Pair(certificates.first().getSubjectX500Principal().name, listOf(RolePrincipal(NODE_P2P_ROLE)))
            }
            ArtemisMessagingComponent.NODE_RPC_USER -> {
                requireTls(certificates)
                CertificateChainCheckPolicy.LeafMustMatch.createCheck(nodeJaasConfig.keyStore, nodeJaasConfig.trustStore).checkCertificateChain(certificates)
                Pair(ArtemisMessagingComponent.NODE_RPC_USER, listOf(RolePrincipal(NODE_RPC_ROLE)))
            }
            ArtemisMessagingComponent.PEER_USER -> {
                val p2pJaasConfig = requireNotNull(p2pJaasConfig) { "Attempted to connect as a peer to the rpc broker." }
                requireTls(certificates)
                // This check is redundant as it was performed already during the SSL handshake
                CertificateChainCheckPolicy.RootMustMatch
                        .createCheck(p2pJaasConfig.keyStore, p2pJaasConfig.trustStore)
                        .checkCertificateChain(certificates)
                Pair(certificates.first().getSubjectX500Principal().name, listOf(RolePrincipal(PEER_ROLE)))
            }
            else -> {
                requireNotNull(rpcJaasConfig) { "Attempted to connect as an rpc user to the P2P broker." }
                rpcJaasConfig!!.run {
                    securityManager.authenticate(username, Password(password))
                    // This will assign the username the actual rights.
                    loginListener(username)
                    // This enables the RPC client to send requests and to receive responses.
                    Pair(username, listOf(RolePrincipal(RPC_ROLE), RolePrincipal("${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.$username")))
                }
            }
        }
    }

}

// Configs used for setting up the broker custom security module.
data class RPCJaasConfig(
        val securityManager: RPCSecurityManager, //used to authenticate users - implemented with Shiro
        val loginListener: LoginListener, //callback that dynamically assigns security roles to RPC users on their authentication
        val useSslForRPC: Boolean)

data class P2PJaasConfig(val keyStore: KeyStore, val trustStore: KeyStore, val revocationMode: RevocationConfig.Mode)

data class NodeJaasConfig(val keyStore: KeyStore, val trustStore: KeyStore)


// Boilerplate required for JAAS.
abstract class BaseBrokerJaasLoginModule : LoginModule {
    protected var loginSucceeded: Boolean = false
    protected lateinit var subject: Subject
    protected lateinit var callbackHandler: CallbackHandler
    protected val principals = ArrayList<Principal>()

    @Suppress("ThrowsCount")
    protected fun getUsernamePasswordAndCerts(): Triple<String, String, Array<X509Certificate>> {
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
        return Triple(username, password, certificates)
    }

    override fun initialize(subject: Subject, callbackHandler: CallbackHandler, sharedState: Map<String, *>, options: Map<String, *>) {
        this.subject = subject
        this.callbackHandler = callbackHandler
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
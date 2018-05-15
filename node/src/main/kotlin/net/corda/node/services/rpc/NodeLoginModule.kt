/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.rpc

import net.corda.core.internal.uncheckedCast
import net.corda.core.utilities.loggerFor
import net.corda.node.internal.security.Password
import net.corda.node.internal.security.RPCSecurityManager
import net.corda.node.internal.artemis.CertificateChainCheckPolicy
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NODE_USER
import org.apache.activemq.artemis.spi.core.security.jaas.CertificateCallback
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal
import java.io.IOException
import java.security.Principal
import java.util.*
import javax.security.auth.Subject
import javax.security.auth.callback.CallbackHandler
import javax.security.auth.callback.NameCallback
import javax.security.auth.callback.PasswordCallback
import javax.security.auth.callback.UnsupportedCallbackException
import javax.security.auth.login.FailedLoginException
import javax.security.auth.login.LoginException
import javax.security.auth.spi.LoginModule
import javax.security.cert.X509Certificate

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
internal class NodeLoginModule : LoginModule {
    companion object {
        internal const val NODE_ROLE = "SystemRoles/Node"
        internal const val RPC_ROLE = "SystemRoles/RPC"

        internal const val CERT_CHAIN_CHECKS_ARG = "CertChainChecks"
        internal const val USE_SSL_ARG = "useSsl"
        internal const val SECURITY_MANAGER_ARG = "RpcSecurityManager"
        internal const val LOGIN_LISTENER_ARG = "LoginListener"
        private val log = loggerFor<NodeLoginModule>()
    }

    private var loginSucceeded: Boolean = false
    private lateinit var subject: Subject
    private lateinit var callbackHandler: CallbackHandler
    private lateinit var securityManager: RPCSecurityManager
    private lateinit var loginListener: LoginListener
    private var useSsl: Boolean? = null
    private lateinit var nodeCertCheck: CertificateChainCheckPolicy.Check
    private lateinit var rpcCertCheck: CertificateChainCheckPolicy.Check
    private val principals = ArrayList<Principal>()

    override fun initialize(subject: Subject, callbackHandler: CallbackHandler, sharedState: Map<String, *>, options: Map<String, *>) {
        this.subject = subject
        this.callbackHandler = callbackHandler
        securityManager = uncheckedCast(options[SECURITY_MANAGER_ARG])
        loginListener = uncheckedCast(options[LOGIN_LISTENER_ARG])
        useSsl = options[USE_SSL_ARG] as Boolean
        val certChainChecks: Map<String, CertificateChainCheckPolicy.Check> = uncheckedCast(options[CERT_CHAIN_CHECKS_ARG])
        nodeCertCheck = certChainChecks[NODE_ROLE]!!
        rpcCertCheck = certChainChecks[RPC_ROLE]!!
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
        val certificates = certificateCallback.certificates ?: emptyArray()

        if (rpcCertCheck is CertificateChainCheckPolicy.UsernameMustMatchCommonNameCheck) {
            (rpcCertCheck as CertificateChainCheckPolicy.UsernameMustMatchCommonNameCheck).username = username
        }

        log.debug("Logging user in")

        try {
            val role = determineUserRole(certificates, username, useSsl!!)
            val validatedUser = when (role) {
                NodeLoginModule.NODE_ROLE -> {
                    authenticateNode(certificates)
                    NODE_USER
                }
                RPC_ROLE -> {
                    authenticateRpcUser(username, Password(password), certificates, useSsl!!)
                    username
                }
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

    private fun authenticateNode(certificates: Array<X509Certificate>) {
        nodeCertCheck.checkCertificateChain(certificates)
        principals += RolePrincipal(NodeLoginModule.NODE_ROLE)
    }

    private fun authenticateRpcUser(username: String, password: Password, certificates: Array<X509Certificate>, useSsl: Boolean) {
        if (useSsl) {
            rpcCertCheck.checkCertificateChain(certificates)
            // no point in matching username with CN because companies wouldn't want to provide a certificate for each user
        }
        securityManager.authenticate(username, password)
        loginListener(username)
        principals += RolePrincipal(RPC_ROLE)  // This enables the RPC client to send requests
        principals += RolePrincipal("${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.$username")  // This enables the RPC client to receive responses
    }

    private fun determineUserRole(certificates: Array<X509Certificate>, username: String, useSsl: Boolean): String? {
        return when (username) {
            ArtemisMessagingComponent.NODE_USER -> {
                require(certificates.isNotEmpty()) { "No TLS?" }
                NodeLoginModule.NODE_ROLE
            }
            else -> {
                if (useSsl) {
                    require(certificates.isNotEmpty()) { "No TLS?" }
                }
                return RPC_ROLE
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
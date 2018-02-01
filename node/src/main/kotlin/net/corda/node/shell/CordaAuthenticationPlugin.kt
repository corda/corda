package net.corda.node.shell

import net.corda.core.context.Actor
import net.corda.core.context.InvocationContext
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.node.internal.security.Password
import net.corda.node.internal.security.RPCSecurityManager
import net.corda.node.internal.security.tryAuthenticate
import org.crsh.auth.AuthInfo
import org.crsh.auth.AuthenticationPlugin
import org.crsh.plugin.CRaSHPlugin

class CordaAuthenticationPlugin(private val rpcOps: (username: String?, credential: String?) -> CordaRPCOps, private val securityManager: RPCSecurityManager, private val nodeLegalName: CordaX500Name) : CRaSHPlugin<AuthenticationPlugin<String>>(), AuthenticationPlugin<String> {

    override fun getImplementation(): AuthenticationPlugin<String> = this

    override fun getName(): String = "corda"

    override fun authenticate(username: String?, credential: String?): AuthInfo {

        if (username == null || credential == null) {
            return AuthInfo.UNSUCCESSFUL
        }
        val authorizingSubject = securityManager.tryAuthenticate(username, Password(credential))
        if (authorizingSubject != null) {
            val actor = Actor(Actor.Id(username), securityManager.id, nodeLegalName)
            return CordaSSHAuthInfo(true, makeRPCOpsWithContext(rpcOps, InvocationContext.rpc(actor), authorizingSubject, username, credential))
        }
        return AuthInfo.UNSUCCESSFUL
    }

    override fun getCredentialType(): Class<String> = String::class.java
}
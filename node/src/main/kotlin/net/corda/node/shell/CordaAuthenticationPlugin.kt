package net.corda.node.shell

import net.corda.core.context.Actor
import net.corda.core.context.InvocationContext
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.node.services.RPCUserService
import org.crsh.auth.AuthInfo
import org.crsh.auth.AuthenticationPlugin
import org.crsh.plugin.CRaSHPlugin

class CordaAuthenticationPlugin(val rpcOps:CordaRPCOps, val userService:RPCUserService, val nodeLegalName:CordaX500Name) : CRaSHPlugin<AuthenticationPlugin<String>>(), AuthenticationPlugin<String> {

    override fun getImplementation(): AuthenticationPlugin<String> = this

    override fun getName(): String = "corda"

    override fun authenticate(username: String?, credential: String?): AuthInfo {
        if (username == null || credential == null) {
            return AuthInfo.UNSUCCESSFUL
        }

        val authorizinguSubject = userService.tryAuthenticate(username, credential.toCharArray())
        if (authorizinguSubject != null) {
        val actor = Actor(Actor.Id(username), userService.id, nodeLegalName)
            return CordaSSHAuthInfo(true, RPCOpsWithContext(rpcOps, InvocationContext.rpc(actor), authorizinguSubject))
        }

        return AuthInfo.UNSUCCESSFUL;
    }

    override fun getCredentialType(): Class<String> = String::class.java
}
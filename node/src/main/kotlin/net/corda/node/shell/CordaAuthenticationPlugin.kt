package net.corda.node.shell

import net.corda.core.messaging.CordaRPCOps
import net.corda.node.services.RPCUserService
import org.crsh.auth.AuthInfo
import org.crsh.auth.AuthenticationPlugin
import org.crsh.plugin.CRaSHPlugin

class CordaAuthenticationPlugin(val rpcOps:CordaRPCOps, val userService:RPCUserService) : CRaSHPlugin<AuthenticationPlugin<String>>(), AuthenticationPlugin<String> {

    override fun getImplementation(): AuthenticationPlugin<String> = this

    override fun getName(): String = "corda"

    override fun authenticate(username: String?, credential: String?): AuthInfo {
        if (username == null || credential == null) {
            return AuthInfo.UNSUCCESSFUL
        }

        val user = userService.getUser(username)

        if (user != null && user.password == credential) {
            return CordaSSHAuthInfo(true, RPCOpsWithContext(rpcOps, user))
        }

        return AuthInfo.UNSUCCESSFUL;
    }

    override fun getCredentialType(): Class<String> = String::class.java
}
package net.corda.shell

import net.corda.core.messaging.CordaRPCOps
import org.crsh.auth.AuthInfo
import org.crsh.auth.AuthenticationPlugin
import org.crsh.plugin.CRaSHPlugin

class CordaRemoteAuthenticationPlugin(private val rpcOps: (username: String?, credential: String?) -> CordaRPCOps):
        CRaSHPlugin<AuthenticationPlugin<String>>(), AuthenticationPlugin<String> {

    override fun getImplementation(): AuthenticationPlugin<String> = this

    override fun getName(): String = "corda"

    override fun authenticate(username: String?, credential: String?): AuthInfo {

        if (username == null || credential == null) {
            return AuthInfo.UNSUCCESSFUL
        }
        try {
            val ops = rpcOps(username, credential)
            return CordaSSHAuthInfo(true, ops)
        } catch(e: Exception) {

        }
        return AuthInfo.UNSUCCESSFUL
    }

    override fun getCredentialType(): Class<String> = String::class.java
}

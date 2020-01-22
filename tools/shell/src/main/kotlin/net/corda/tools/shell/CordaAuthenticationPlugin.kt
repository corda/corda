package net.corda.tools.shell

import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.internal.messaging.InternalCordaRPCOps
import net.corda.core.utilities.loggerFor
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.crsh.auth.AuthInfo
import org.crsh.auth.AuthenticationPlugin
import org.crsh.plugin.CRaSHPlugin

class CordaAuthenticationPlugin(private val makeRPCConnection: (username: String, credential: String) -> CordaRPCConnection) : CRaSHPlugin<AuthenticationPlugin<String>>(), AuthenticationPlugin<String> {

    companion object {
        private val logger = loggerFor<CordaAuthenticationPlugin>()
    }

    override fun getImplementation(): AuthenticationPlugin<String> = this

    override fun getName(): String = "corda"

    override fun authenticate(username: String?, credential: String?): AuthInfo {

        if (username == null || credential == null) {
            return AuthInfo.UNSUCCESSFUL
        }
        try {
            val connection = makeRPCConnection(username, credential)
            val ops = connection.proxy as InternalCordaRPCOps
            return CordaSSHAuthInfo(true, ops, isSsh = true, rpcConn = connection)
        } catch (e: ActiveMQSecurityException) {
            logger.warn(e.message)
        } catch (e: Exception) {
            logger.warn(e.message, e)
        }
        return AuthInfo.UNSUCCESSFUL
    }

    override fun getCredentialType(): Class<String> = String::class.java
}
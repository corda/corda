package net.corda.tools.shell

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.loggerFor
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.crsh.auth.AuthInfo
import org.crsh.auth.AuthenticationPlugin
import org.crsh.plugin.CRaSHPlugin

internal class CordaAuthenticationPlugin(private val rpcOpsProducer: RPCOpsProducer) : CRaSHPlugin<AuthenticationPlugin<String>>(), AuthenticationPlugin<String> {

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
            val cordaSSHAuthInfo = CordaSSHAuthInfo(rpcOpsProducer, username, credential, isSsh = true)
            // We cannot guarantee authentication happened successfully till `RCPClient` session been established, hence doing a dummy call
            cordaSSHAuthInfo.getOrCreateRpcOps(CordaRPCOps::class.java).protocolVersion
            return cordaSSHAuthInfo
        } catch (e: ActiveMQSecurityException) {
            logger.warn(e.message)
        } catch (e: Exception) {
            logger.warn(e.message, e)
        }
        return AuthInfo.UNSUCCESSFUL
    }

    override fun getCredentialType(): Class<String> = String::class.java
}
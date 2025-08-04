package net.corda.node.services.messaging

import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NODE_P2P_USER
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NODE_RPC_USER
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.PEER_USER
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import javax.security.auth.Subject

class InterceptingActiveMQJAASSecurityManager(configurationName: String, configuration: SecurityConfiguration, private val p2pPort: Int, private val rpcPort: Int, private val rpcAdminPort: Int) : ActiveMQJAASSecurityManager(configurationName, configuration) {
    companion object {
        private val log = loggerFor<InterceptingActiveMQJAASSecurityManager>()

        enum class BrokerType {
            RPC, P2P
        }

        fun RemotingConnection?.categorise(p2pPort: String, rpcPort: String, rpcAdminPort: String): BrokerType {
            return if (this?.transportLocalAddress?.endsWith(":$p2pPort") ?: false) {
                BrokerType.P2P
            } else if (this?.transportLocalAddress?.endsWith(":$rpcPort") ?: false) {
                BrokerType.RPC
            } else if (this?.transportLocalAddress?.endsWith(":$rpcAdminPort") ?: false) {
                BrokerType.RPC
            } else throw IllegalStateException("Neither RPC port ($rpcPort), RPC admin port ($rpcAdminPort), nor P2P port ($p2pPort) for local connection ${this?.transportLocalAddress}")
        }

        fun categoriseUser(user: String?): BrokerType {
            return if (user == null) {
                BrokerType.RPC
            } else if (user == PEER_USER || user == NODE_P2P_USER) {
                BrokerType.P2P
            } else if (user == NODE_RPC_USER) {
                BrokerType.RPC
            } else {
                BrokerType.RPC
            }
        }
    }

    override fun authenticate(user: String?, password: String?, remotingConnection: RemotingConnection?, securityDomain: String?): Subject? {
        val userCategory = categoriseUser(user)
        val connectionCategory = remotingConnection.categorise(p2pPort.toString(), rpcPort.toString(), rpcAdminPort.toString())
        if (userCategory != connectionCategory) {
            log.warn("Authenticate attempt user=$user, remotingConnection=$remotingConnection, securityDomain=$securityDomain connectionCategory=$connectionCategory userCategory=$userCategory")
            return null
        }
        return super.authenticate(user, password, remotingConnection, securityDomain)
    }
}
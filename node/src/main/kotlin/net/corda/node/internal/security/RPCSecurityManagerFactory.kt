package net.corda.node.internal.security

import net.corda.core.context.AuthServiceId
import net.corda.node.services.config.NodeConfiguration

/**
 * Factory instanting [RPCSecurityManager] according to node settings
 */
class RPCSecurityManagerFactory {

    fun build(config : NodeConfiguration) : RPCSecurityManager {
        return RPCSecurityManagerInMemory(
                id = AuthServiceId("NODE_CONFIG_USERS"),
                users = config.rpcUsers
        )
    }

}

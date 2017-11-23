package net.corda.node.internal.security

import net.corda.core.context.AuthServiceId
import net.corda.node.internal.security.RPCUserServiceInMemory
import net.corda.node.services.RPCUserService
import net.corda.node.services.config.NodeConfiguration

/**
 * Factory instanting [RPCUserService] according to node settings
 */
class RPCUserServiceFactory {

    fun build(config : NodeConfiguration) : RPCUserService {
        // TODO: Externalised plugin
        return RPCUserServiceInMemory(
                id = AuthServiceId("NODE_CONFIG_USERS"),
                users = config.rpcUsers
        )
    }

}

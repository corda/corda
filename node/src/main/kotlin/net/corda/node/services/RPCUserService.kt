package net.corda.node.services

import net.corda.core.context.AuthServiceId
import net.corda.nodeapi.internal.config.User

/**
 * Service for retrieving [User] objects representing RPC users who are authorised to use the RPC system. A [User]
 * contains their login username and password along with a set of permissions for RPC services they are allowed access
 * to. These permissions are represented as [String]s to allow RPC implementations to add their own permissioning.
 */
interface RPCUserService {

    fun getUser(username: String): User?
    val users: List<User>

    val id: AuthServiceId
}

// TODO Store passwords as salted hashes
// TODO Or ditch this and consider something like Apache Shiro
// TODO Need access to permission checks from inside flows and at other point during audit checking.
class RPCUserServiceImpl(override val users: List<User>) : RPCUserService {

    override val id: AuthServiceId = AuthServiceId("NODE_FILE_CONFIGURATION")

    init {
        users.forEach {
            require(it.username.matches("\\w+".toRegex())) { "Username ${it.username} contains invalid characters" }
        }
    }

    override fun getUser(username: String): User? = users.find { it.username == username }
}

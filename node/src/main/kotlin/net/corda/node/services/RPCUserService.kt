package net.corda.node.services

import net.corda.core.flows.FlowLogic
import net.corda.node.services.config.NodeConfiguration

/**
 * Service for retrieving [User] objects representing RPC users who are authorised to use the RPC system. A [User]
 * contains their login username and password along with a set of permissions for RPC services they are allowed access
 * to. These permissions are represented as [String]s to allow RPC implementations to add their own permissioning.
 */
interface RPCUserService {
    fun getUser(username: String): User?
    val users: List<User>
}

// TODO Store passwords as salted hashes
// TODO Or ditch this and consider something like Apache Shiro
class RPCUserServiceImpl(config: NodeConfiguration) : RPCUserService {

    private val _users = config.rpcUsers.associateBy(User::username)

    override fun getUser(username: String): User? = _users[username]

    override val users: List<User> get() = _users.values.toList()
}

data class User(val username: String, val password: String, val permissions: Set<String>) {
    override fun toString(): String = "${javaClass.simpleName}($username, permissions=$permissions)"
}

fun startFlowPermission(className: String) = "StartFlow.$className"
fun <P : FlowLogic<*>> startFlowPermission(clazz: Class<P>) = startFlowPermission(clazz.name)
inline fun <reified P : FlowLogic<*>> startFlowPermission(): String = startFlowPermission(P::class.java)

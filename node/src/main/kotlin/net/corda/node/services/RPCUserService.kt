package net.corda.node.services

import com.typesafe.config.Config
import net.corda.core.protocols.ProtocolLogic
import net.corda.node.services.config.getListOrElse

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
class RPCUserServiceImpl(config: Config) : RPCUserService {

    private val _users: Map<String, User>

    init {
        _users = config.getListOrElse<Config>("rpcUsers") { emptyList() }
                .map {
                    val username = it.getString("user")
                    require(username.matches("\\w+".toRegex())) { "Username $username contains invalid characters" }
                    val password = it.getString("password")
                    val permissions = it.getListOrElse<String>("permissions") { emptyList() }.toSet()
                    User(username, password, permissions)
                }
                .associateBy(User::username)
    }

    override fun getUser(username: String): User? = _users[username]

    override val users: List<User> get() = _users.values.toList()
}

data class User(val username: String, val password: String, val permissions: Set<String>) {
    override fun toString(): String = "${javaClass.simpleName}($username, permissions=$permissions)"
}

inline fun <reified P : ProtocolLogic<*>> startProtocolPermission(): String = P::class.java.name

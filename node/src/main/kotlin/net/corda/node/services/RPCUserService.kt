package net.corda.node.services

import net.corda.core.exists
import net.corda.core.read
import java.nio.file.Path
import java.util.*

/**
 * Service for retrieving [User] objects representing RPC users who are authorised to use the RPC system. A [User]
 * contains their login username and password along with a set of permissions for RPC services they are allowed access
 * to. These permissions are represented as [String]s to allow RPC implementations to add their own permissioning.
 */
interface RPCUserService {
    fun getUser(usename: String): User?
    val users: List<User>
}

// TODO If this sticks around then change it to use HOCON ...
// TODO  ... and also store passwords as salted hashes.
// TODO Otherwise consider something like Apache Shiro
class PropertiesFileRPCUserService(file: Path) : RPCUserService {

    private val _users: Map<String, User>

    init {
        _users = if (file.exists()) {
            val properties = Properties()
            file.read {
                properties.load(it)
            }
            properties.map {
                val parts = it.value.toString().split(delimiters = ",")
                val username = it.key.toString()
                require(!username.contains("""\.|\*|#""".toRegex())) { """Usernames cannot have the following characters: * . # """ }
                val password = parts[0]
                val permissions = parts.drop(1).map(String::toUpperCase).toSet()
                User(username, password, permissions)
            }.associateBy(User::username)
        } else {
            emptyMap()
        }
    }

    override fun getUser(usename: String): User? = _users[usename]

    override val users: List<User> get() = _users.values.toList()
}

data class User(val username: String, val password: String, val permissions: Set<String>) {
    override fun toString(): String = "${javaClass.simpleName}($username, permissions=$permissions)"
}

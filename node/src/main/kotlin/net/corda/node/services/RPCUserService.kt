package net.corda.node.services

import net.corda.core.flows.type.FlowLogic
import net.corda.nodeapi.User

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
// TODO Need access to permission checks from inside flows and at other point during audit checking.
class RPCUserServiceImpl(override val users: List<User>) : RPCUserService {
    override fun getUser(username: String): User? = users.find { it.username == username }
}

/**
 * Helper class for creating flow class permissions.
 */
class FlowPermissions {
    companion object {

        /**
         * Creates the flow permission string of the format "StartFlow.{ClassName}".
         *
         * @param className a flow class name for which permission is created.
         */
        @JvmStatic
        fun startFlowPermission(className: String) = "StartFlow.$className"

        /**
         * An overload for the [startFlowPermission]
         *
         * @param clazz a class for which permission is created.
         *
         */
        @JvmStatic
        fun <P : FlowLogic<*>> startFlowPermission(clazz: Class<P>) = startFlowPermission(clazz.name)

        /**
         * An overload for the [startFlowPermission].
         *
         * @param P a class for which permission is created.
         */
        @JvmStatic
        inline fun <reified P : FlowLogic<*>> startFlowPermission(): String = startFlowPermission(P::class.java)
    }
}

package net.corda.node.services.security

import kotlin.reflect.KFunction
import kotlin.reflect.KVisibility
import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.CordaRPCOps
import org.apache.shiro.authz.permission.DomainPermission
import kotlin.reflect.full.declaredMemberFunctions

/**
 * Encapsulate a user permission to perform a collection of RPC calls
 *
 * TODO: extend doc
 */
class RPCPermission : DomainPermission {

    /**
     * Main constructor from string representation.
     *
     * @param representation The string representation of the permission
     */
    constructor (representation : String) {

        this.domain = RPC_PERMISSION_DOMAIN
        /*
         * Parse action and targets from string representation
         */
        val action = representation.substringBefore(SEPARATOR).toLowerCase()
        when(action) {
            ACTION_INVOKE_RPC.toLowerCase() -> {
                /*
                 * Permission to call a given RPC with any input
                 */
                val rpcCall = representation.substringAfter(SEPARATOR)
                require(representation.count { it == SEPARATOR } == 1) {
                    "Malformed permission string" }
                this.actions = setOf(rpcCall)
            }
            ACTION_START_FLOW.toLowerCase() -> {
                /*
                 * Permission to start a given Flow via RPC
                 */
                val targetFlow = representation.substringAfter(SEPARATOR)
                require(targetFlow.isNotEmpty()) {
                    "Missing target flow after StartFlow" }
                this.actions = FLOW_RPC_CALLS
                this.targets = setOf(targetFlow)
            }
            ACTION_ALL.toLowerCase() -> {
                // Leaving empty set of targets and actions to match everything
            }
            else -> throw IllegalArgumentException("Unkwnow permission action specifier: $action")
        }
    }

    /**
     * Helper constructor directly setting actions and target field
     */
    internal constructor(actions: Set<String>,
                         targets: Set<String>? = null)
    {
        require(ALL_RPC_CALLS.containsAll(actions) ||
                FLOW_RPC_CALLS.containsAll(actions)) {
            "Function name not registered in RPC client interface"
        }

        this.domain = RPC_PERMISSION_DOMAIN
        this.actions = actions

        if (targets != null) {
            this.targets = targets
        }
    }

    /**
     * Helper function to specify target object type for this permission (e.g.: a Flow),
     * returning a copy of this instance
     *
     * @param T The target object class
     */
    inline fun <reified T> onTarget() = this.onTarget(T::class.java)

    /**
     * Overload of onTarget() to specify target type at runtime
     */
    fun onTarget(target : Class<*>) : RPCPermission {
        require (this.targets == null) {"attempt resetting target"}

        return RPCPermission(this.actions, setOf(target.name))
    }

    /**
     * TODO: extend documentations
     */
    fun toConfigString() : String {
        if (actions == null || actions.isEmpty()) {
            return ACTION_ALL
        }
        else if (actions.size == 1) {
            require(targets == null || targets.isEmpty()) {
                "Permission not representable in legacy config format"
            }
            return "$ACTION_INVOKE_RPC.${actions.first()}"
        } else {
            require(actions == FLOW_RPC_CALLS && targets.size == 1) {
                "Permission not representable in legacy config format"
            }
            val flow = targets.first()
            return "$ACTION_START_FLOW.$flow"
        }
    }

    companion object {

        val SEPARATOR          = '.'
        val ACTION_START_FLOW  = "StartFlow"
        val ACTION_INVOKE_RPC  = "InvokeRpc"
        val ACTION_ALL         = "ALL"

        /**
         * Global admin permissions.
         */
        @JvmStatic
        val all = RPCPermission(ACTION_ALL)

        /**
         * Creates a flow permission object
         *
         * @param className a flow class name for which permission is created.
         */
        @JvmStatic
        fun startFlow(className: String) = RPCPermission(FLOW_RPC_CALLS, setOf(className))

        /**
         * An overload for [startFlow]
         *
         * @param clazz a class for which permission is created.
         */
        @JvmStatic
        fun <T : FlowLogic<*>> startFlow(clazz: Class<T>) = startFlow(clazz.name)

        /**
         * An overload for [startFlow].
         *
         * @param T a class for which permission is created.
         */
        @JvmStatic
        inline fun <reified T : FlowLogic<*>> startFlow() = startFlow(T::class.java)

        /**
         * Creates an RPC permission.
         *
         * @param methodName a RPC method name for which permission is created.
         */
        @JvmStatic
        fun invokeRpc(methodName: String) = RPCPermission(setOf(methodName))

        /**
         * Creates a permission string with format "InvokeRpc.{method.name}".
         *
         * @param method a RPC [KFunction] for which permission is created.
         */
        @JvmStatic
        fun invokeRpc(method: KFunction<*>) = invokeRpc(method.name)

        /*
         * Internal tag identifying RPC permission domains (for shiro.DomainPermission)
         */
        private val RPC_PERMISSION_DOMAIN = "RPC"

        /*
         * List of RPC calls granted by a StartFlow permission
         */
        private val FLOW_RPC_CALLS = setOf("startFlowDynamic",
                                           "startTrackedFlowDynamic")
        /*
         * List of all RPC calls from wrapped interface
         */
        private val ALL_RPC_CALLS = CordaRPCOps::class.declaredMemberFunctions
                .filter { it.visibility == KVisibility.PUBLIC }
                .map { it.name }
                .toSet()

    }
}
package net.corda.node.services.security

import kotlin.reflect.KFunction
import kotlin.reflect.KVisibility
import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.CordaRPCOps
import org.apache.shiro.authz.permission.DomainPermission
import kotlin.reflect.full.declaredMemberFunctions

/**
 * Provide a representation of RPC permissions based on Apache Shiro permissions framework.
 * A permission represents a set of actions: for example, the set of all RPC invocations, or the set
 * of RPC invocations acting on a given class of Flows in input. A permission `implies` another one if
 * its set of actions contains the set of actions in the other one. In Apache Shiro, permissions are
 * represented by instances of the [Permission] interface which offers a single method: [implies], to
 * test if the 'x implies y' binary predicate is satisfied.
 */
class RPCPermission : DomainPermission {

    /**
     * Main constructor taking in input an RPC string representation conforming to the syntax used
     * in a Node configuration. Currently valid permission strings have one of the forms:
     *
     *   - `ALL`: allowing all type of RPC calls
     *
     *   - `InvokeRpc.$RPCMethodName`: allowing to call a given RPC method without restrictions on its arguments.
     *
     *   - `StartFlow.$FlowClassName`: allowing to call a `startFlow*` RPC method targeting a Flow instance
     *     of a given class
     *
     * @param representation The input string representation
     */
    constructor (representation : String) {

        domain = RPC_PERMISSION_DOMAIN
        /*
         * Parse action and targets from string representation
         */
        val action = representation.substringBefore(SEPARATOR).toLowerCase()
        when(action) {
            ACTION_INVOKE_RPC.toLowerCase() -> {
                /*
                 * Permission to call a given RPC on any input
                 */
                val rpcCall = representation.substringAfter(SEPARATOR)
                require(representation.count { it == SEPARATOR } == 1) {
                    "Malformed permission string" }
                actions = setOf(rpcCall)
            }
            ACTION_START_FLOW.toLowerCase() -> {
                /*
                 * Permission to start a given Flow via RPC
                 */
                val targetFlow = representation.substringAfter(SEPARATOR)
                require(targetFlow.isNotEmpty()) {
                    "Missing target flow after StartFlow" }
                actions = FLOW_RPC_CALLS
                targets = setOf(targetFlow)
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
                         target: String? = null)
        : super(actions, if (target != null) setOf(target) else null)
    {
        require(ALL_RPC_CALLS.containsAll(actions)  ||
                FLOW_RPC_CALLS.containsAll(actions) ||
                actions == setOf(ACTION_ALL)) {
            "Function name not registered in RPC client interface"
        }
        domain = RPC_PERMISSION_DOMAIN
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
    fun onTarget(target : Class<*>) : DomainPermission {
        require (this.targets == null) {"attempt resetting target"}
        return RPCPermission(this.actions, target.name)
    }

    /**
     * Produce string representation of this instance conforming to the syntax used in
     * Node configuration
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

    /*
     * Collection of static factory functions and private string constants
     */
    companion object {

        private val SEPARATOR          = '.'
        private val ACTION_START_FLOW  = "StartFlow"
        private val ACTION_INVOKE_RPC  = "InvokeRpc"
        private val ACTION_ALL         = "ALL"

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

        /**
         * Global admin permissions.
         */
        @JvmStatic
        val all = RPCPermission(ACTION_ALL)

        /**
         * Creates a flow permission instance.
         *
         * @param className a flow class name for which permission is created.
         */
        @JvmStatic
        fun startFlow(className: String) = RPCPermission(FLOW_RPC_CALLS, className)

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
         * Creates an RPC invocation permission.
         *
         * @param methodName a RPC method name for which permission is created.
         */
        @JvmStatic
        fun invokeRpc(methodName: String) = RPCPermission(setOf(methodName))

        /**
         * An overload for [invokeRpc]
         *
         * @param method a RPC [KFunction] for which permission is created.
         */
        @JvmStatic
        fun invokeRpc(method: KFunction<*>) = invokeRpc(method.name)
    }
}
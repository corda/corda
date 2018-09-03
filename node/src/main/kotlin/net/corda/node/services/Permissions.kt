package net.corda.node.services

import net.corda.core.flows.FlowLogic
import kotlin.reflect.KFunction

/**
 * Helper class for creating permissions.
 */
class Permissions {

    companion object {

        /**
         * Global admin permissions.
         */
        @JvmStatic
        fun all() = "ALL"

        /**
         * Creates the flow permission string of the format "StartFlow.{ClassName}".
         *
         * @param className a flow class name for which permission is created.
         */
        @JvmStatic
        fun startFlow(className: String) = "StartFlow.$className"

        /**
         * An overload for the [startFlow]
         *
         * @param clazz a class for which permission is created.
         */
        @JvmStatic
        fun <P : FlowLogic<*>> startFlow(clazz: Class<P>) = startFlow(clazz.name)

        /**
         * An overload for the [startFlow].
         *
         * @param P a class for which permission is created.
         */
        @JvmStatic
        inline fun <reified P : FlowLogic<*>> startFlow(): String = startFlow(P::class.java)

        /**
         * Creates a permission string with format "InvokeRpc.{MethodName}".
         *
         * @param methodName a RPC method name for which permission is created.
         */
        @JvmStatic
        fun invokeRpc(methodName: String) = "InvokeRpc.$methodName"

        /**
         * Creates a permission string with format "InvokeRpc.{method.name}".
         *
         * @param method a RPC [KFunction] for which permission is created.
         */
        @JvmStatic
        fun invokeRpc(method: KFunction<*>) = invokeRpc(method.name)
    }
}
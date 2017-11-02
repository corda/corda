package net.corda.node.internal

import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.CordaRPCOps
import net.corda.node.services.Permissions.Companion.all
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.node.services.Permissions.Companion.invokeRpc
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberFunctions

object DefaultCordaRpcPermissions {

    private val invokePermissions = CordaRPCOps::class.declaredMemberFunctions.filter { it.visibility == KVisibility.PUBLIC }.associate { it.name to setOf(invokeRpc(it), all()) }
    private val startFlowPermissions = setOf("startFlow", "startFlowDynamic", "startTrackedFlow", "startTrackedFlowDynamic").associate { it to this::startFlowPermission }

    fun permissionsAllowing(methodName: String, args: List<Any?>): Set<String> {

        val invoke = invokePermissions[methodName] ?: emptySet()
        val start = startFlowPermissions[methodName]?.invoke(args)
        return if (start != null) invoke + start else invoke
    }

    @Suppress("UNCHECKED_CAST")
    private fun startFlowPermission(args: List<Any?>): String = if (args[0] is Class<*>) startFlow(args[0] as Class<FlowLogic<*>>) else startFlow(args[0] as String)
}
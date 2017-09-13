package net.corda.node.internal.cordapp

import net.corda.core.flows.FlowLogic
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.SerializeAsToken
import java.net.URL

/**
 * Defines a CorDapp
 *
 * @property contractClassNames List of contracts
 * @property initiatedFlows List of initiatable flow classes
 * @property rpcFlows List of RPC initiable flows classes
 * @property servies List of RPC services
 * @property plugins List of Corda plugin registries
 * @property jarPath The path to the JAR for this CorDapp
 */
data class Cordapp(
        val contractClassNames: List<String>,
        val initiatedFlows: List<Class<out FlowLogic<*>>>,
        val rpcFlows: List<Class<out FlowLogic<*>>>,
        val services: List<Class<out SerializeAsToken>>,
        val plugins: List<CordaPluginRegistry>,
        val customSchemas: Set<MappedSchema>,
        val jarPath: URL)
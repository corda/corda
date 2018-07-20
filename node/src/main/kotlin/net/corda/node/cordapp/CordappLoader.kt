package net.corda.node.cordapp

import net.corda.core.cordapp.Cordapp
import net.corda.core.flows.FlowLogic
import net.corda.core.schemas.MappedSchema

/**
 * Handles CorDapp loading
 */
interface CordappLoader {

    val cordapps: List<Cordapp>

    val appClassLoader: ClassLoader

    val flowCordappMap: Map<Class<out FlowLogic<*>>, Cordapp>

    val cordappSchemas: Set<MappedSchema>
}

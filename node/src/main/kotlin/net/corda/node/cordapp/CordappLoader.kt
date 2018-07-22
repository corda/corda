package net.corda.node.cordapp

import net.corda.core.cordapp.Cordapp
import net.corda.core.flows.FlowLogic
import net.corda.core.schemas.MappedSchema

/**
 * Handles loading [Cordapp]s.
 */
interface CordappLoader {

    /**
     * Returns all [Cordapp]s found.
     */
    val cordapps: List<Cordapp>

    /**
     * Returns a [ClassLoader] containing all types from all [Cordapp]s.
     */
    val appClassLoader: ClassLoader

    /**
     * Returns a map between flow class and owning [Cordapp].
     * The mappings are unique, and the node will not start otherwise.
     */
    val flowCordappMap: Map<Class<out FlowLogic<*>>, Cordapp>

    /**
     * Returns all [MappedSchema] found inside the [Cordapp]s.
     */
    val cordappSchemas: Set<MappedSchema>
}
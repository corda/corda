package net.corda.core.internal.cordapp

import net.corda.core.cordapp.Cordapp
import net.corda.core.flows.FlowLogic
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.SerializeAsToken
import java.io.File
import java.net.URL

data class CordappImpl(
        override val contractClassNames: List<String>,
        override val initiatedFlows: List<Class<out FlowLogic<*>>>,
        override val rpcFlows: List<Class<out FlowLogic<*>>>,
        override val schedulableFlows: List<Class<out FlowLogic<*>>>,
        override val services: List<Class<out SerializeAsToken>>,
        override val serializationWhitelists: List<SerializationWhitelist>,
        override val customSchemas: Set<MappedSchema>,
        override val jarPath: URL) : Cordapp {
    override val name: String = File(jarPath.toURI()).name.removeSuffix(".jar")

    /**
     * An exhaustive list of all classes relevant to the node within this CorDapp
     *
     * TODO: Also add [SchedulableFlow] as a Cordapp class
     */
    override val cordappClasses = ((rpcFlows + initiatedFlows + services + serializationWhitelists.map { javaClass }).map { it.name } + contractClassNames)
}

package net.corda.core.internal.cordapp

import net.corda.core.NonDeterministic
import net.corda.core.cordapp.Cordapp
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.toPath
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.SerializeAsToken
import java.net.URL

@NonDeterministic
data class CordappImpl(
        override val contractClassNames: List<String>,
        override val initiatedFlows: List<Class<out FlowLogic<*>>>,
        override val rpcFlows: List<Class<out FlowLogic<*>>>,
        override val serviceFlows: List<Class<out FlowLogic<*>>>,
        override val schedulableFlows: List<Class<out FlowLogic<*>>>,
        override val services: List<Class<out SerializeAsToken>>,
        override val serializationWhitelists: List<SerializationWhitelist>,
        override val serializationCustomSerializers: List<SerializationCustomSerializer<*, *>>,
        override val customSchemas: Set<MappedSchema>,
        override val allFlows: List<Class<out FlowLogic<*>>>,
        override val jarPath: URL,
        override val jarHash: SecureHash.SHA256) : Cordapp {
    override val name: String = jarPath.toPath().fileName.toString().removeSuffix(".jar")

    /**
     * An exhaustive list of all classes relevant to the node within this CorDapp
     *
     * TODO: Also add [SchedulableFlow] as a Cordapp class
     */
    override val cordappClasses = ((rpcFlows + initiatedFlows + services + serializationWhitelists.map { javaClass }).map { it.name } + contractClassNames)
}

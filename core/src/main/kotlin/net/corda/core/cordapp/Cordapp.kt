package net.corda.core.cordapp

import net.corda.core.DeleteForDJVM
import net.corda.core.DoNotImplement
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.SerializeAsToken
import java.net.URL

/**
 * Represents a cordapp by registering the JAR that contains it and all important classes for Corda.
 * Instances of this class are generated automatically at startup of a node and can get retrieved from
 * [CordappProvider.getAppContext] from the [CordappContext] it returns.
 *
 * This will only need to be constructed manually for certain kinds of tests.
 *
 * @property name Cordapp name - derived from the base name of the Cordapp JAR (therefore may not be unique)
 * @property contractClassNames List of contracts
 * @property initiatedFlows List of initiatable flow classes
 * @property rpcFlows List of RPC initiable flows classes
 * @property serviceFlows List of [net.corda.core.node.services.CordaService] initiable flows classes
 * @property schedulableFlows List of flows startable by the scheduler
 * @property services List of RPC services
 * @property serializationWhitelists List of Corda plugin registries
 * @property serializationCustomSerializers List of serializers
 * @property customSchemas List of custom schemas
 * @property allFlows List of all flow classes
 * @property jarPath The path to the JAR for this CorDapp
 * @property jarHash Hash of the jar
 */
@DoNotImplement
@DeleteForDJVM
interface Cordapp {
    val name: String
    val contractClassNames: List<String>
    val initiatedFlows: List<Class<out FlowLogic<*>>>
    val rpcFlows: List<Class<out FlowLogic<*>>>
    val serviceFlows: List<Class<out FlowLogic<*>>>
    val schedulableFlows: List<Class<out FlowLogic<*>>>
    val services: List<Class<out SerializeAsToken>>
    val serializationWhitelists: List<SerializationWhitelist>
    val serializationCustomSerializers: List<SerializationCustomSerializer<*, *>>
    val customSchemas: Set<MappedSchema>
    val allFlows: List<Class<out FlowLogic<*>>>
    val jarPath: URL
    val cordappClasses: List<String>
    val jarHash: SecureHash.SHA256
}
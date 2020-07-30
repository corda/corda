package net.corda.core.cordapp

import net.corda.core.DeleteForDJVM
import net.corda.core.DoNotImplement
import net.corda.core.cordapp.Cordapp.Info.*
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.cordapp.CordappImpl.Companion.UNKNOWN_VALUE
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CheckpointCustomSerializer
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
 * @property name Cordapp name - derived from the base name of the Cordapp JAR (therefore may not be unique). This is different to
 * [Cordapp.Info.shortName] which should be used instead.
 * @property contractClassNames List of contracts
 * @property initiatedFlows List of initiatable flow classes
 * @property rpcFlows List of RPC initiable flows classes
 * @property serviceFlows List of [net.corda.core.node.services.CordaService] initiable flows classes
 * @property schedulableFlows List of flows startable by the scheduler
 * @property services List of RPC services
 * @property serializationWhitelists List of Corda plugin registries
 * @property serializationCustomSerializers List of serializers
 * @property checkpointCustomSerializers List of serializers for checkpoints
 * @property customSchemas List of custom schemas
 * @property allFlows List of all flow classes
 * @property jarPath The path to the JAR for this CorDapp
 * @property cordappClasses An exhaustive list of all classes relevant to the node within this CorDapp
 * @property jarHash Hash of the jar
 * @property minimumPlatformVersion The minimum platform version this CorDapp will run on.
 * @property targetPlatformVersion The target platform version this CorDapp was designed and tested on.
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
    val checkpointCustomSerializers: List<CheckpointCustomSerializer<*, *>>
    val customSchemas: Set<MappedSchema>
    val allFlows: List<Class<out FlowLogic<*>>>
    val jarPath: URL
    val cordappClasses: List<String>
    val info: Info
    val jarHash: SecureHash.SHA256
    val minimumPlatformVersion: Int
    val targetPlatformVersion: Int

    /**
     * Further information about the CorDapp extracted from its MANIFEST. The sub-types [Default], [Contract] and [Workflow] determine what
     * type of CorDapp this is.
     *
     * @property shortName Cordapp's name
     * @property vendor Cordapp's vendor
     * @property version Cordapp's version
     */
    @DoNotImplement
    interface Info {
        val shortName: String
        val vendor: String
        val version: String
        val licence: String

        fun hasUnknownFields(): Boolean

        /**
         * CorDapps created on V3 which bundle contracts and flows into the same jar. This is effectively
         * [ContractAndWorkflow] but without the integer versioning.
         */
        data class Default(override val shortName: String, override val vendor: String, override val version: String, override val licence: String) : Info {
            override fun hasUnknownFields(): Boolean = arrayOf(shortName, vendor, version).any { it == UNKNOWN_VALUE }
            override fun toString() = "CorDapp $shortName version $version by $vendor with licence $licence"
        }

        /**
         * A contract CorDapp contains contract definitions (state, commands) and verification logic
         * @property versionId integer version of the CorDapp. [version] is the toString of this.
         */
        data class Contract(override val shortName: String, override val vendor: String, val versionId: Int, override val licence: String) : Info {
            override val version: String
                get() = versionId.toString()
            override fun toString() = "Contract CorDapp: $shortName version $versionId by vendor $vendor with licence $licence"
            override fun hasUnknownFields(): Boolean = arrayOf(shortName, vendor, licence).any { it == UNKNOWN_VALUE }
        }

        /**
         * A workflow CorDapp contains flows and services used to implement business transactions using contracts and states persisted to the
         * immutable ledger
         * @property versionId integer version of the CorDapp. [version] is the toString of this.
         */
        data class Workflow(override val shortName: String, override val vendor: String, val versionId: Int, override val licence: String) : Info {
            override val version: String
                get() = versionId.toString()
            override fun toString() = "Workflow CorDapp: $shortName version $versionId by vendor $vendor with licence $licence"
            override fun hasUnknownFields(): Boolean = arrayOf(shortName, vendor, licence).any { it == UNKNOWN_VALUE }
        }

        /** A CorDapp that includes both contract and workflow classes, and defined in terms of [Contract] and [Workflow]. */
        // TODO: future work in Gradle cordapp plugins to enforce separation of Contract and Workflow classes into separate jars
        data class ContractAndWorkflow(val contract: Contract, val workflow: Workflow) : Info {
            override val shortName: String
                get() = "Contract: ${contract.shortName}, Workflow: ${workflow.shortName}"
            override val vendor: String
                get() = "Contract: ${contract.vendor}, Workflow: ${workflow.vendor}"
            override val licence: String
                get() = "Contract: ${contract.licence}, Workflow: ${workflow.licence}"
            override val version: String
                get() = "Contract: ${contract.version}, Workflow: ${workflow.version}"
            override fun toString() = "Combined CorDapp: $contract, $workflow"
            override fun hasUnknownFields(): Boolean = contract.hasUnknownFields() || workflow.hasUnknownFields()
        }
    }
}

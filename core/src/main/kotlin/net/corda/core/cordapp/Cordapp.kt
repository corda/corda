package net.corda.core.cordapp

import net.corda.core.DeleteForDJVM
import net.corda.core.DoNotImplement
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.cordapp.CordappImpl.Companion.CORDAPP_CONTRACT_VERSION
import net.corda.core.internal.cordapp.CordappImpl.Companion.CORDAPP_WORKFLOW_VERSION
import net.corda.core.internal.cordapp.CordappImpl.Companion.UNKNOWN_VALUE
import net.corda.core.internal.cordapp.CordappImpl.Companion.parseVersion
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
    val info: Info
    val jarHash: SecureHash.SHA256

    /**
     * CorDapp's information, including vendor and version.
     *
     * @property shortName Cordapp's shortName
     * @property vendor Cordapp's vendor
     * @property version Cordapp's version
     */
    @DoNotImplement
    interface Info {
        val shortName: String
        val vendor: String
        val version: String
        val licence: String
        val minimumPlatformVersion: Int
        val targetPlatformVersion: Int

        fun hasUnknownFields(): Boolean
    }
}

/** original (to Corda 3) */
data class Default(override val shortName: String, override val vendor: String, override val version: String, override val minimumPlatformVersion: Int, override val targetPlatformVersion: Int, override val licence: String = UNKNOWN_VALUE)
    : Cordapp.Info {
    companion object {
        val UNKNOWN = Default(UNKNOWN_VALUE, UNKNOWN_VALUE, UNKNOWN_VALUE,1, 1)
    }
    override fun hasUnknownFields(): Boolean = arrayOf(shortName, vendor, version).any { it == UNKNOWN_VALUE }
    override fun toString() = "CorDapp $shortName version $version by $vendor with licence $licence"
}

/** a Contract Cordapp contains contract definitions (state, commands) and verification logic */
data class Contract(override val shortName: String, override val vendor: String, override val version: String, override val licence: String, override val minimumPlatformVersion: Int, override val targetPlatformVersion: Int)
    : Cordapp.Info {
    val versionId: Int
        get() = parseVersion(version, CORDAPP_CONTRACT_VERSION)
    override fun toString() = "Contract CorDapp: $shortName version $version by vendor $vendor with licence $licence"
    override fun hasUnknownFields(): Boolean = arrayOf(shortName, vendor, licence).any { it == UNKNOWN_VALUE }
}

/** a Workflow Cordapp contains flows and services used to implement business transactions using contracts and states persisted to the immutable ledger */
data class Workflow(override val shortName: String, override val vendor: String, override val version: String, override val licence: String, override val minimumPlatformVersion: Int, override val targetPlatformVersion: Int)
    : Cordapp.Info {
    val versionId: Int
        get() = parseVersion(version, CORDAPP_WORKFLOW_VERSION)
    override fun toString() = "Workflow CorDapp: $shortName version $version by vendor $vendor with licence $licence"
    override fun hasUnknownFields(): Boolean = arrayOf(shortName, vendor, licence).any { it == UNKNOWN_VALUE }
}

/** a Workflow Cordapp contains flows and services used to implement business transactions using contracts and states persisted to the immutable ledger */
data class ContractAndWorkflow(val contract: Contract, val workflow: Workflow,
                               override val shortName: String = "${contract.shortName}, ${workflow.shortName}",
                               override val vendor: String = "${contract.vendor}, ${workflow.vendor}",
                               val versionId: Int,
                               override val licence: String = "${contract.licence}, ${workflow.licence}",
                               override val minimumPlatformVersion: Int, override val targetPlatformVersion: Int)
    : Cordapp.Info {
    override val version: String
        get() = "${contract.versionId}, ${workflow.versionId}"
    override fun toString() = "Combined CorDapp: $contract, $workflow"
    override fun hasUnknownFields(): Boolean = arrayOf(contract.shortName, contract.vendor, contract.licence, workflow.shortName, workflow.vendor, workflow.licence).any { it == UNKNOWN_VALUE }
}

package net.corda.core.internal.cordapp

import net.corda.core.DeleteForDJVM
import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappInvalidVersionException
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.notary.NotaryService
import net.corda.core.internal.toPath
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.SerializeAsToken
import java.net.URL

@DeleteForDJVM
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
        override val info: Cordapp.Info,
        override val jarHash: SecureHash.SHA256,
        val notaryService: Class<out NotaryService>?,
        /** Indicates whether the CorDapp is loaded from external sources, or generated on node startup (virtual). */
        val isLoaded: Boolean = true) : Cordapp {
    override val name: String = jarName(jarPath)

    companion object {
        fun jarName(url: URL): String = url.toPath().fileName.toString().removeSuffix(".jar")

        /** CorDapp manifest entries */
        const val CORDAPP_CONTRACT_NAME = "Cordapp-Contract-Name"
        const val CORDAPP_CONTRACT_VERSION = "Cordapp-Contract-Version"
        const val CORDAPP_CONTRACT_VENDOR = "Cordapp-Contract-Vendor"
        const val CORDAPP_CONTRACT_LICENCE = "Cordapp-Contract-Licence"

        const val CORDAPP_WORKFLOW_NAME = "Cordapp-Workflow-Name"
        const val CORDAPP_WORKFLOW_VERSION = "Cordapp-Workflow-Version"
        const val CORDAPP_WORKFLOW_VENDOR = "Cordapp-Workflow-Vendor"
        const val CORDAPP_WORKFLOW_LICENCE = "Cordapp-Workflow-Licence"

        const val TARGET_PLATFORM_VERSION = "Target-Platform-Version"
        const val MIN_PLATFORM_VERSION = "Min-Platform-Version"

        const val UNKNOWN_VALUE = "Unknown"
        const val DEFAULT_CORDAPP_VERSION = 1

        /** used for CorDapps that do not explicitly define attributes */
        val UNKNOWN = Cordapp.Info.Default(UNKNOWN_VALUE, UNKNOWN_VALUE, UNKNOWN_VALUE,1, 1)

        /** Helper method for version identifier parsing */
        fun parseVersion(versionStr: String?, attributeName: String): Int {
            if (versionStr == null)
                throw CordappInvalidVersionException("Target versionId attribute $attributeName not specified. Please specify a whole number starting from 1.")
            return try {
                val version = versionStr.toInt()
                if (version < 1) {
                    throw CordappInvalidVersionException("Target versionId ($versionStr) for attribute $attributeName must not be smaller than 1.")
                }
                return version
            } catch (e: NumberFormatException) {
                throw CordappInvalidVersionException("Version identifier ($versionStr) for attribute $attributeName must be a whole number starting from 1.")
            }
        }
    }
    /**
     * An exhaustive list of all classes relevant to the node within this CorDapp
     *
     * TODO: Also add [SchedulableFlow] as a Cordapp class
     */
    override val cordappClasses: List<String> = run {
        val classList = rpcFlows + initiatedFlows + services + serializationWhitelists.map { javaClass } + notaryService
        classList.mapNotNull { it?.name } + contractClassNames
    }
}
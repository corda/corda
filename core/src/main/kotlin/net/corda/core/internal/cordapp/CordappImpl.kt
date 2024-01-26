package net.corda.core.internal.cordapp

import net.corda.core.cordapp.Cordapp
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.notary.NotaryService
import net.corda.core.internal.telemetry.TelemetryComponent
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CheckpointCustomSerializer
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.SerializeAsToken
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name

data class CordappImpl(
        val jarFile: Path,
        override val contractClassNames: List<String>,
        override val initiatedFlows: List<Class<out FlowLogic<*>>>,
        override val rpcFlows: List<Class<out FlowLogic<*>>>,
        override val serviceFlows: List<Class<out FlowLogic<*>>>,
        override val schedulableFlows: List<Class<out FlowLogic<*>>>,
        override val services: List<Class<out SerializeAsToken>>,
        override val telemetryComponents: List<Class<out TelemetryComponent>>,
        override val serializationWhitelists: List<SerializationWhitelist>,
        override val serializationCustomSerializers: List<SerializationCustomSerializer<*, *>>,
        override val checkpointCustomSerializers: List<CheckpointCustomSerializer<*, *>>,
        override val customSchemas: Set<MappedSchema>,
        override val allFlows: List<Class<out FlowLogic<*>>>,
        override val info: Cordapp.Info,
        override val jarHash: SecureHash.SHA256,
        override val minimumPlatformVersion: Int,
        override val targetPlatformVersion: Int,
        val notaryService: Class<out NotaryService>? = null,
        /** Indicates whether the CorDapp is loaded from external sources, or generated on node startup (virtual). */
        val isLoaded: Boolean = true,
        private val explicitCordappClasses: List<String> = emptyList(),
        val isVirtual: Boolean = false
) : Cordapp {
    override val jarPath: URL
        get() = jarFile.toUri().toURL()

    override val name: String
        get() = jarName(jarFile)

    // TODO: Also add [SchedulableFlow] as a Cordapp class
    override val cordappClasses: List<String> = run {
        val classList = rpcFlows + initiatedFlows + services + serializationWhitelists.flatMap { it.whitelist } + notaryService
        classList.mapNotNull { it?.name } + contractClassNames + explicitCordappClasses
    }

    companion object {
        fun jarName(url: Path): String = url.name.removeSuffix(".jar")

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
        val UNKNOWN_INFO = Cordapp.Info.Default(UNKNOWN_VALUE, UNKNOWN_VALUE, UNKNOWN_VALUE, UNKNOWN_VALUE)

        @VisibleForTesting
        val TEST_INSTANCE = CordappImpl(
                jarFile = Paths.get(""),
                contractClassNames = emptyList(),
                initiatedFlows = emptyList(),
                rpcFlows = emptyList(),
                serviceFlows = emptyList(),
                schedulableFlows = emptyList(),
                services = emptyList(),
                telemetryComponents = emptyList(),
                serializationWhitelists = emptyList(),
                serializationCustomSerializers = emptyList(),
                checkpointCustomSerializers = emptyList(),
                customSchemas = emptySet(),
                info = UNKNOWN_INFO,
                allFlows = emptyList(),
                jarHash = SecureHash.allOnesHash,
                minimumPlatformVersion = 1,
                targetPlatformVersion = PLATFORM_VERSION,
                notaryService = null
        )
    }
}

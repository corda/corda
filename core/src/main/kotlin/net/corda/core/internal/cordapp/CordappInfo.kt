package net.corda.core.internal.cordapp

import net.corda.core.cordapp.CordappInvalidVersionException

/** abstract class used to maintain backwards compatibility */
abstract class CordappInfo(open val name: String, open val vendor: String, open val version: String, open val minimumPlatformVersion: Int, open val targetPlatformVersion: Int) {
    companion object {
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
    abstract fun description(): String
    abstract fun hasUnknownFields(): Boolean
}

/** new identifiers (from Corda 4) */

/** a Contract Cordapp contains contract definitions (state, commands) and verification logic */
data class Contract(override val name: String, override val vendor: String, val versionId: Int, val licence: String, override val minimumPlatformVersion: Int, override val targetPlatformVersion: Int)
    : CordappInfo(name, vendor, versionId.toString(), minimumPlatformVersion, targetPlatformVersion) {
    override fun description() = "Contract CorDapp: $name version $version by vendor $vendor with licence $licence"
    override fun hasUnknownFields(): Boolean = arrayOf(name, vendor, licence).any { it == UNKNOWN_VALUE }
}

/** a Workflow Cordapp contains flows and services used to implement business transactions using contracts and states persisted to the immutable ledger */
data class Workflow(override val name: String, override val vendor: String, val versionId: Int, val licence: String, override val minimumPlatformVersion: Int, override val targetPlatformVersion: Int)
    : CordappInfo(name, vendor, versionId.toString(), minimumPlatformVersion, targetPlatformVersion) {
    override fun description() = "Workflow CorDapp: $name version $version by vendor $vendor with licence $licence"
    override fun hasUnknownFields(): Boolean = arrayOf(name, vendor, licence).any { it == UNKNOWN_VALUE }
}

/** a Workflow Cordapp contains flows and services used to implement business transactions using contracts and states persisted to the immutable ledger */
data class ContractAndWorkflow(val contract: Contract, val workflow: Workflow, override val name: String, override val vendor: String, val versionId: Int, val licence: String, override val minimumPlatformVersion: Int, override val targetPlatformVersion: Int)
    : CordappInfo("", "", "", minimumPlatformVersion, targetPlatformVersion) {
    override fun description() = "Combined CorDapp: ${contract.description()}, ${workflow.description()}"
    override fun hasUnknownFields(): Boolean = arrayOf(contract.name, contract.vendor, contract.licence, workflow.name, workflow.vendor, workflow.licence).any { it == UNKNOWN_VALUE }
}
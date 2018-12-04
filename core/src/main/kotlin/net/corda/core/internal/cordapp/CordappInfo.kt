package net.corda.core.internal.cordapp

/** abstract class used to maintain backwards compatibility */
abstract class CordappInfo(open val name: String, open val vendor: String, open val version: String, open val minimumPlatformVersion: Int, open val targetPlatformVersion: Int) {
    abstract fun description(): String
    abstract fun hasUnknownFields(): Boolean
}

/** CorDapp manifest entries */
const val CORDAPP_CONTRACT_NAME = "Cordapp-Contract-Name"
const val CORDAPP_CONTRACT_VERSION = "Cordapp-Contract-Version"
const val CORDAPP_CONTRACT_VENDOR = "Cordapp-Contract-Vendor"
const val CORDAPP_CONTRACT_LICENCE = "Cordapp-Contract-Licence"
const val CORDAPP_WORKFLOW_NAME = "Cordapp-Workflow-Name"
const val CORDAPP_WORKFLOW_VERSION = "Cordapp-Workflow-Version"
const val CORDAPP_WORKFLOW_VENDOR = "Cordapp-Workflow-Vendor"
const val CORDAPP_WORKFLOW_LICENCE = "Cordapp-Workflow-Licence"

const val UNKNOWN_VALUE = "Unknown"
const val DEFAULT_CORDAPP_VERSION = 1

/** new identifiers (from Corda 4) */

/** a Contract Cordapp contains contract definitions (state, commands) and verification logic */
data class Contract(override val name: String, override val vendor: String, val versionId: Int, val licence: String, override val minimumPlatformVersion: Int, override val targetPlatformVersion: Int)
    : CordappInfo(name, vendor, versionId.toString(), minimumPlatformVersion, targetPlatformVersion) {
    override fun description() = "Contract CorDapp: $name version $version by $vendor with licence $licence"
    override fun hasUnknownFields(): Boolean = arrayOf(name, vendor, licence).any { it == UNKNOWN_VALUE }
}

/** a Workflow Cordapp contains flows and services used to implement business transactions using contracts and states persisted to the immutable ledger */
data class Workflow(override val name: String, override val vendor: String, val versionId: Int, val licence: String, override val minimumPlatformVersion: Int, override val targetPlatformVersion: Int)
    : CordappInfo(name, vendor, versionId.toString(), minimumPlatformVersion, targetPlatformVersion) {
    override fun description() = "Workflow CorDapp: $name version $version by $vendor with licence $licence"
    override fun hasUnknownFields(): Boolean = arrayOf(name, vendor, licence).any { it == UNKNOWN_VALUE }
}
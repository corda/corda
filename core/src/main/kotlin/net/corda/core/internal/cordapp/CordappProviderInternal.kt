package net.corda.core.internal.cordapp

import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractClassName
import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappProvider
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.verification.AttachmentFixups
import net.corda.core.internal.verification.ContractGeneration

interface CordappProviderInternal : CordappProvider {
    val appClassLoader: ClassLoader
    val attachmentFixups: AttachmentFixups
    val cordapps: List<CordappImpl>

    /**
     * The contract CorDapps of each legacy (i.e. not current) [ContractGeneration].
     */
    val legacyContractCordapps: Map<ContractGeneration, List<CordappImpl>>
    fun getCordappForFlow(flowLogic: FlowLogic<*>): Cordapp?

    /**
     * Similar to [getContractAttachmentID] except it returns the [ContractAttachment] object and also returns the attachments representing
     * the legacy versions of the contract, if they exist. Only the legacy versions required for the given minimum platform version are
     * returned (see [ContractGeneration.isRequiredFor]).
     */
    fun getContractAttachments(contractClassName: ContractClassName, minimumPlatformVersion: Int): ContractAttachmentWithLegacy?
}

/**
 * The [currentAttachment] of a contract along with its [legacyAttachments] for each legacy [ContractGeneration].
 */
data class ContractAttachmentWithLegacy(
        val currentAttachment: ContractAttachment,
        val legacyAttachments: Map<ContractGeneration, ContractAttachment> = emptyMap()
) {
    init {
        require(ContractGeneration.CURRENT !in legacyAttachments) { "The current generation is not a legacy one" }
    }

    /** The 4.11 (or earlier) version of the contract, if there is one. */
    val legacyAttachment: ContractAttachment?
        get() = legacyAttachments[ContractGeneration.CORDA_4_11]
}

package net.corda.core.internal.cordapp

import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractClassName
import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappProvider
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.verification.AttachmentFixups

interface CordappProviderInternal : CordappProvider {
    val appClassLoader: ClassLoader
    val attachmentFixups: AttachmentFixups
    val cordapps: List<CordappImpl>
    fun getCordappForFlow(flowLogic: FlowLogic<*>): Cordapp?

    /**
     * Similar to [getContractAttachmentID] except it returns the [ContractAttachment] object and also returns an optional second attachment
     * representing the legacy version (4.11 or earlier) of the contract, if one exists.
     */
    fun getContractAttachments(contractClassName: ContractClassName): ContractAttachmentWithLegacy?
}

data class ContractAttachmentWithLegacy(val currentAttachment: ContractAttachment, val legacyAttachment: ContractAttachment? = null)

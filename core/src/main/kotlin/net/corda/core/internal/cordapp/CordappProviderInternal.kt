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
     * Similar to [getContractAttachmentID] except it returns the [ContractAttachment] object.
     */
    fun getContractAttachment(contractClassName: ContractClassName): ContractAttachment?
}

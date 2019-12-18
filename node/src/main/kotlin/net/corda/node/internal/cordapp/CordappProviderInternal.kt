package net.corda.node.internal.cordapp

import net.corda.core.contracts.Attachment
import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappProvider
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.cordapp.CordappImpl

interface CordappProviderInternal : CordappProvider {
    val cordapps: List<CordappImpl>
    fun getCordappForFlow(flowLogic: FlowLogic<*>): Cordapp?
    fun fixupAttachments(attachments: Collection<Attachment>): Collection<Attachment>
}

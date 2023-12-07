package net.corda.core.internal.cordapp

import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappProvider
import net.corda.core.flows.FlowLogic
import net.corda.core.node.services.AttachmentId

interface CordappProviderInternal : CordappProvider {
    val appClassLoader: ClassLoader
    val cordapps: List<CordappImpl>
    fun getCordappForFlow(flowLogic: FlowLogic<*>): Cordapp?
    fun fixupAttachmentIds(attachmentIds: Collection<AttachmentId>): Set<AttachmentId>
}

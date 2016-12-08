package net.corda.attachmentdemo.plugin

import net.corda.attachmentdemo.api.AttachmentDemoApi
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.FinalityFlow

class AttachmentDemoPlugin : CordaPluginRegistry() {
    // A list of classes that expose web APIs.
    override val webApis: List<Class<*>> = listOf(AttachmentDemoApi::class.java)
    // A list of flows that are required for this cordapp
    override val requiredFlows: Map<String, Set<String>> = mapOf(
            FinalityFlow::class.java.name to setOf(SignedTransaction::class.java.name, setOf(Unit).javaClass.name, setOf(Unit).javaClass.name)
    )
    override val servicePlugins: List<Class<*>> = listOf()
}

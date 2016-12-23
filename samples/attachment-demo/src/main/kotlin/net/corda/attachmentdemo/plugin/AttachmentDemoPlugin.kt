package net.corda.attachmentdemo.plugin

import net.corda.attachmentdemo.api.AttachmentDemoApi
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.FinalityFlow
import net.corda.core.node.CordaPluginRegistry
import java.util.function.Function

class AttachmentDemoPlugin : CordaPluginRegistry() {
    // A list of classes that expose web APIs.
    override val webApis = listOf(Function(::AttachmentDemoApi))
    // A list of Flows that are required for this cordapp
    override val requiredFlows: Map<String, Set<String>> = mapOf(
            FinalityFlow::class.java.name to setOf(SignedTransaction::class.java.name, setOf(Unit).javaClass.name, setOf(Unit).javaClass.name)
    )
}

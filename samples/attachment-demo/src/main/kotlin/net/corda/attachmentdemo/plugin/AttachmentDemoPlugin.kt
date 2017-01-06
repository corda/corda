package net.corda.attachmentdemo.plugin

import net.corda.core.node.CordaPluginRegistry
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.FinalityFlow

class AttachmentDemoPlugin : CordaPluginRegistry() {
    // A list of Flows that are required for this cordapp
    override val requiredFlows: Map<String, Set<String>> = mapOf(
            FinalityFlow::class.java.name to setOf(SignedTransaction::class.java.name, setOf(Unit).javaClass.name, setOf(Unit).javaClass.name)
    )
}

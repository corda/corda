package net.corda.notarydemo.plugin

import net.corda.core.crypto.Party
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.NotaryFlow
import net.corda.notarydemo.flows.DummyIssueAndMove
import java.util.function.Function

class NotaryDemoPlugin : CordaPluginRegistry() {
    // A list of protocols that are required for this cordapp
    override val requiredFlows = mapOf(
            NotaryFlow.Client::class.java.name to setOf(SignedTransaction::class.java.name, setOf(Unit).javaClass.name),
            DummyIssueAndMove::class.java.name to setOf(Party::class.java.name)
    )
}

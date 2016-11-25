package net.corda.notarydemo.plugin

import net.corda.core.node.CordaPluginRegistry
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.NotaryFlow
import net.corda.notarydemo.api.NotaryDemoApi

class NotaryDemoPlugin : CordaPluginRegistry() {
    // A list of classes that expose web APIs.
    override val webApis: List<Class<*>> = listOf(NotaryDemoApi::class.java)
    // A list of protocols that are required for this cordapp
    override val requiredFlows: Map<String, Set<String>> = mapOf(
            NotaryFlow.Client::class.java.name to setOf(SignedTransaction::class.java.name, setOf(Unit).javaClass.name)
    )
    override val servicePlugins: List<Class<*>> = listOf()
}

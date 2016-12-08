package net.corda.traderdemo.plugin

import net.corda.core.contracts.Amount
import net.corda.core.crypto.Party
import net.corda.core.node.CordaPluginRegistry
import net.corda.traderdemo.api.TraderDemoApi
import net.corda.traderdemo.flow.BuyerFlow
import net.corda.traderdemo.flow.SellerFlow

class TraderDemoPlugin : CordaPluginRegistry() {
    // A list of classes that expose web APIs.
    override val webApis: List<Class<*>> = listOf(TraderDemoApi::class.java)
    // A list of flows that are required for this cordapp
    override val requiredFlows: Map<String, Set<String>> = mapOf(
            SellerFlow::class.java.name to setOf(Party::class.java.name, Amount::class.java.name)
    )
    override val servicePlugins: List<Class<*>> = listOf(BuyerFlow.Service::class.java)
}

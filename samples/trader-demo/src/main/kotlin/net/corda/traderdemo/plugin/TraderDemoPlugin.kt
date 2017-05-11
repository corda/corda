package net.corda.traderdemo.plugin

import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.node.CordaPluginRegistry
import net.corda.traderdemo.flow.BuyerFlow
import net.corda.traderdemo.flow.SellerFlow
import java.util.function.Function

class TraderDemoPlugin : CordaPluginRegistry() {
    // A list of Flows that are required for this cordapp
    override val requiredFlows: Map<String, Set<String>> = mapOf(
            SellerFlow::class.java.name to setOf(Party::class.java.name, Amount::class.java.name)
    )
    override val servicePlugins = listOf(Function(BuyerFlow::Service))
}

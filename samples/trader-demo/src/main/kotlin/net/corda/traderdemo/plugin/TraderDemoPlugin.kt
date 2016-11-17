package net.corda.traderdemo.plugin

import net.corda.core.contracts.Amount
import net.corda.core.crypto.Party
import net.corda.core.node.CordaPluginRegistry
import net.corda.traderdemo.api.TraderDemoApi
import net.corda.traderdemo.protocol.BuyerProtocol
import net.corda.traderdemo.protocol.SellerProtocol

class TraderDemoPlugin : CordaPluginRegistry() {
    // A list of classes that expose web APIs.
    override val webApis: List<Class<*>> = listOf(TraderDemoApi::class.java)
    // A list of protocols that are required for this cordapp
    override val requiredProtocols: Map<String, Set<String>> = mapOf(
            SellerProtocol::class.java.name to setOf(Party::class.java.name, Amount::class.java.name)
    )
    override val servicePlugins: List<Class<*>> = listOf(BuyerProtocol.Service::class.java)
}

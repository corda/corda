package net.corda.traderdemo.plugin

import net.corda.core.contracts.Amount
import net.corda.core.crypto.Party
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.node.PluginServiceHub
import net.corda.traderdemo.flow.BuyerFlow
import net.corda.traderdemo.flow.SellerFlow

class TraderDemoPlugin : CordaPluginRegistry() {
    // A list of Flows that are required for this cordapp
    override val requiredFlows: Map<String, Set<String>> = mapOf(
            SellerFlow::class.java.name to setOf(Party::class.java.name, Amount::class.java.name)
    )

    override fun initialise(serviceHub: PluginServiceHub) {
        // Buyer will fetch the attachment from the seller automatically when it resolves the transaction.
        // For demo purposes just extract attachment jars when saved to disk, so the user can explore them.
        val attachmentsPath = (serviceHub.storageService.attachments).let {
            it.automaticallyExtractAttachments = true
            it.storePath
        }
        serviceHub.registerFlowInitiator(SellerFlow::class.java) { BuyerFlow(it, attachmentsPath) }
    }
}

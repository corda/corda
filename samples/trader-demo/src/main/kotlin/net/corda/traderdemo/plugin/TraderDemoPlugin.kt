package net.corda.traderdemo.plugin

import net.corda.core.identity.Party
import net.corda.core.node.CordaPluginRegistry
import net.corda.traderdemo.flow.BuyerFlow
import java.util.function.Function

class TraderDemoPlugin : CordaPluginRegistry() {
    override val servicePlugins = listOf(Function(BuyerFlow::Service))
}

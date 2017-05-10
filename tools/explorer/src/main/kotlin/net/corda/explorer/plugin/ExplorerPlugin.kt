package net.corda.explorer.plugin

import net.corda.core.identity.Party
import net.corda.core.node.CordaPluginRegistry
import net.corda.flows.IssuerFlow
import java.util.function.Function

class ExplorerPlugin : CordaPluginRegistry() {
    override val servicePlugins = listOf(Function(IssuerFlow.Issuer::Service))
}

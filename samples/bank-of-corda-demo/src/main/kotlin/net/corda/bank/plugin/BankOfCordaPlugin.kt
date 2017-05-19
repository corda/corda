package net.corda.bank.plugin

import net.corda.bank.api.BankOfCordaWebApi
import net.corda.core.node.CordaPluginRegistry
import java.util.function.Function

class BankOfCordaPlugin : CordaPluginRegistry() {
    // A list of classes that expose web APIs.
    override val webApis = listOf(Function(::BankOfCordaWebApi))
}

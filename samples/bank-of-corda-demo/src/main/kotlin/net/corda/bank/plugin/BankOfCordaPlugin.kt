package net.corda.bank.plugin

import net.corda.bank.api.BankOfCordaWebApi
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

class BankOfCordaPlugin : WebServerPluginRegistry {
    // A list of classes that expose web APIs.
    override val webApis = listOf(Function(::BankOfCordaWebApi))
}

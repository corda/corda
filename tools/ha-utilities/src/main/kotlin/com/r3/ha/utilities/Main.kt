package com.r3.ha.utilities

import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import net.corda.cliutils.start

fun main(args: Array<String>) {
    HAUtilities().start(args)
}

class HAUtilities : CordaCliWrapper("ha-utilities", "HA utilities contains tools to help setting up corda firewall services.") {
    override fun additionalSubCommands() = setOf(RegistrationTool(), BridgeSSLKeyTool(), InternalArtemisKeystoreGenerator(), InternalTunnelKeystoreGenerator(), ArtemisConfigurationTool())

    override fun runProgram(): Int {
        printHelp()
        return ExitCodes.FAILURE
    }
}
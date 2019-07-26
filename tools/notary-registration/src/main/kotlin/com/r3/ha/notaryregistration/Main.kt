package com.r3.ha.notaryregistration

import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import net.corda.cliutils.start

fun main(args: Array<String>) {
    NotaryRegistrationCli().start(args)
}

class NotaryRegistrationCli : CordaCliWrapper("notary-registration", "Tool registering a HA notary service identity.") {
    override fun additionalSubCommands() = setOf(NotaryRegistrationTool())

    override fun runProgram(): Int {
        printHelp()
        return ExitCodes.FAILURE
    }
}
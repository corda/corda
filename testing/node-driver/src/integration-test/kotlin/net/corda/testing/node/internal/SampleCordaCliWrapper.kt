package net.corda.testing.node.internal

import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import net.corda.cliutils.start
import picocli.CommandLine

class SampleCordaCliWrapperException(message: String) : Exception(message)
class SampleCordaCliWrapper: CordaCliWrapper("sampleCliWrapper", "Sample corda cliWrapper app") {


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SampleCordaCliWrapper().start(args)
        }
    }

    @CommandLine.Option(names = ["--sample-command"],
            description = [ "Sample command. Prints a message to the console."])
    var sampleCommand: Boolean? =  null

    @CommandLine.Option(names = ["--throw-exception"], description = ["Specify this to throw an exception"])
    var throwException: Boolean? = null

    override fun runProgram(): Int {


        if (throwException!=null) {
            throw  SampleCordaCliWrapperException("net.corda.testing.node.internal.SampleCordaCliWrapper test exception")
        }
        if (sampleCommand!=null) {
            System.out.println("Sample command invoked.")
        }
        return ExitCodes.SUCCESS
    }

}
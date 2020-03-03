package net.corda.cliutils

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
            description = [ "Root directory containing the node configuration files and CorDapp JARs that will form the test network.",
                "It may also contain existing node directories."])
    var sampleCommand: Boolean? =  null

    @CommandLine.Option(names = ["--throw-exception"], description = ["Specify this to throw an exception"])
    var throwException: Boolean? = null

    override fun runProgram(): Int {


        if (throwException!=null) {
            throw  SampleCordaCliWrapperException("net.corda.cliutils.SampleCordaCliWrapper test exception")
        }
        if (sampleCommand!=null) {
            System.out.println("Sample command invoked.")
        }
        return ExitCodes.SUCCESS
    }

}
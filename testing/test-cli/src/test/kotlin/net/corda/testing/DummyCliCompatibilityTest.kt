package net.corda.testing

import picocli.CommandLine
import java.net.InetAddress

@CommandLine.Command(subcommands = [StatusCommand::class])
open class Dummy {
    @CommandLine.Option(names = arrayOf("-d", "--directory"), description = arrayOf("the directory to run in"))
    var baseDirectory: String? = null

    @CommandLine.Parameters(index = "0")
    var host: InetAddress? = null
    @CommandLine.Parameters(index = "1")
    var port: Int = 0
}

@CommandLine.Command(name = "status")
open class StatusCommand {
    @CommandLine.Option(names = ["-p", "--pattern"], description = ["the regex patterns to use"])
    var patterns: Array<String>? = null
}

@CommandLine.Command(name = "ls")
open class ListCommand {
    @CommandLine.Option(names = arrayOf("-d", "--depth"), description = arrayOf("the max level of recursion"))
    var depth: Int? = null

    @CommandLine.Parameters(index = "0")
    var machine: String? = null
    @CommandLine.Parameters(index = "1")
    var listStyle: Int = 0
}

class DummyCliCompatibilityTest : CliBackwardsCompatibleTest() {
    @org.junit.Test
    fun `should be compatible with old version`() {
        checkBackwardsCompatibility(Dummy::class.java)
    }
}
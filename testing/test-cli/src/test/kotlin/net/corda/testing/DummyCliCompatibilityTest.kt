package net.corda.testing

import junit.framework.AssertionFailedError
import org.junit.Test
import picocli.CommandLine
import java.net.InetAddress

@CommandLine.Command(subcommands = [ListCommand::class, StatusCommand::class])
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
    @CommandLine.Option(names = ["--pattern"], description = ["the regex patterns to use"])
    var patterns: Array<String>? = null
    @CommandLine.Option(names = arrayOf("-s", "--style"), description = arrayOf("the style of search"))
    var style: DummyEnum? = null
}

@CommandLine.Command(name = "ls")
open class ListCommand {
    @CommandLine.Option(names = arrayOf("-d", "--depth"), description = arrayOf("the max level of recursion"))
    var depth: Int? = null

    @CommandLine.Parameters(index = "0")
    var machine: String? = null
}

enum class DummyEnum {
    FULL, DIR, FILE
}

class DummyCliCompatibilityTest : CliBackwardsCompatibleTest() {
    @Test
    fun `should be compatible with old version`() {
        try {
            checkBackwardsCompatibility(Dummy::class.java)
        } catch (e: AssertionFailedError) {
            System.err.println(e.message)
        }

    }
}
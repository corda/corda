package net.corda.testing

import org.hamcrest.CoreMatchers.*
import org.junit.Assert
import org.junit.Test
import picocli.CommandLine
import java.util.regex.Pattern

class CommandLineCompatibilityCheckerTest {

    enum class AllOptions {
        YES, NO, MAYBZ
    }

    enum class BinaryOptions {
        YES, NO
    }


    @Test
    fun `should detect missing parameter`() {
        val value1 = object {
            @CommandLine.Option(names = arrayOf("-d", "--directory"), description = arrayOf("the directory to run in"))
            var baseDirectory: String? = null
        }
        val value2 = object {
            @CommandLine.Option(names = arrayOf("--directory"), description = arrayOf("the directory to run in"))
            var baseDirectory: String? = null
        }
        val breaks = CommandLineCompatibilityChecker().checkBackwardsCompatibility(CommandLine(value1), CommandLine(value2))
        Assert.assertThat(breaks.size, `is`(1))
        Assert.assertThat(breaks.first(), `is`(instanceOf(OptionsChangedError::class.java)))
    }


    @Test
    fun `should detect changes in positional parameters`() {
        val value1 = object {
            @CommandLine.Parameters(index = "0")
            var baseDirectory: String? = null
            @CommandLine.Parameters(index = "1")
            var depth: Pattern? = null
        }
        val value2 = object {
            @CommandLine.Parameters(index = "1")
            var baseDirectory: String? = null
            @CommandLine.Parameters(index = "0")
            var depth: Int? = null
        }
        val breaks = CommandLineCompatibilityChecker().checkBackwardsCompatibility(CommandLine(value1), CommandLine(value2))
        Assert.assertThat(breaks.size, `is`(2))
        Assert.assertThat(breaks.first(), `is`(instanceOf(PositionalArgumentsChangedError::class.java)))
    }

    @Test
    fun `should detect removal of a subcommand`() {
        @CommandLine.Command(subcommands = [ListCommand::class, StatusCommand::class])
        class Dummy

        @CommandLine.Command(subcommands = [ListCommand::class])
        class Dummy2

        val breaks = CommandLineCompatibilityChecker().checkBackwardsCompatibility(CommandLine(Dummy()), CommandLine(Dummy2()))
        Assert.assertThat(breaks.size, `is`(1))
        Assert.assertThat(breaks.first(), `is`(instanceOf(CommandsChangedError::class.java)))
    }

    @Test
    fun `should detect change of parameter type`() {
        val value1 = object {
            @CommandLine.Option(names = ["--directory"], description = ["the directory to run in"])
            var baseDirectory: String? = null
        }
        val value2 = object {
            @CommandLine.Option(names = ["--directory"], description = ["the directory to run in"])
            var baseDirectory: Pattern? = null
        }

        val breaks = CommandLineCompatibilityChecker().checkBackwardsCompatibility(CommandLine(value1), CommandLine(value2))
        Assert.assertThat(breaks.size, `is`(1))
        Assert.assertThat(breaks.first(), `is`(instanceOf(TypesChangedError::class.java)))
    }

    @Test
    fun `should detect change of enum options`() {
        val value1 = object {
            @CommandLine.Option(names = ["--directory"], description = ["the directory to run in"])
            var baseDirectory: AllOptions? = null
        }
        val value2 = object {
            @CommandLine.Option(names = ["--directory"], description = ["the directory to run in"])
            var baseDirectory: BinaryOptions? = null
        }

        val breaks = CommandLineCompatibilityChecker().checkBackwardsCompatibility(CommandLine(value1), CommandLine(value2))
        Assert.assertThat(breaks.filter { it is EnumOptionsChangedError }.size, `is`(1))
        Assert.assertThat(breaks.first { it is EnumOptionsChangedError }.message, containsString(AllOptions.MAYBZ.name))
    }

    @CommandLine.Command(name = "status")
    class StatusCommand

    @CommandLine.Command(name = "ls")
    class ListCommand

}
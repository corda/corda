package net.corda.node

import joptsimple.OptionException
import net.corda.core.div
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test
import java.nio.file.Paths

class ArgsParserTest {
    private val parser = ArgsParser()
    private val workingDirectory = Paths.get(".").normalize().toAbsolutePath()

    @Test
    fun `no command line arguments`() {
        assertThat(parser.parse()).isEqualTo(CmdLineOptions(
                baseDirectory = workingDirectory,
                configFile = workingDirectory / "node.conf",
                help = false,
                logToConsole = false))
    }

    @Test
    fun `just base-directory with relative path`() {
        val expectedBaseDir = Paths.get("tmp").normalize().toAbsolutePath()
        val cmdLineOptions = parser.parse("--base-directory", "tmp")
        assertThat(cmdLineOptions).isEqualTo(CmdLineOptions(
                baseDirectory = expectedBaseDir,
                configFile = expectedBaseDir / "node.conf",
                help = false,
                logToConsole = false))
    }

    @Test
    fun `just base-directory with absolute path`() {
        val baseDirectory = Paths.get("tmp").normalize().toAbsolutePath()
        val cmdLineOptions = parser.parse("--base-directory", baseDirectory.toString())
        assertThat(cmdLineOptions).isEqualTo(CmdLineOptions(
                baseDirectory = baseDirectory,
                configFile = baseDirectory / "node.conf",
                help = false,
                logToConsole = false))
    }

    @Test
    fun `just config-file with relative path`() {
        val cmdLineOptions = parser.parse("--config-file", "different.conf")
        assertThat(cmdLineOptions).isEqualTo(CmdLineOptions(
                baseDirectory = workingDirectory,
                configFile = workingDirectory / "different.conf",
                help = false,
                logToConsole = false))
    }

    @Test
    fun `just config-file with absolute path`() {
        val configFile = Paths.get("tmp", "a.conf").normalize().toAbsolutePath()
        val cmdLineOptions = parser.parse("--config-file", configFile.toString())
        assertThat(cmdLineOptions).isEqualTo(CmdLineOptions(
                baseDirectory = workingDirectory,
                configFile = configFile,
                help = false,
                logToConsole = false))
    }

    @Test
    fun `both base-directory and config-file`() {
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            parser.parse("--base-directory", "base", "--config-file", "conf")
        }.withMessageContaining("base-directory").withMessageContaining("config-file")
    }

    @Test
    fun `base-directory without argument`() {
        assertThatExceptionOfType(OptionException::class.java).isThrownBy {
            parser.parse("--base-directory")
        }.withMessageContaining("base-directory")
    }

    @Test
    fun `config-file without argument`() {
        assertThatExceptionOfType(OptionException::class.java).isThrownBy {
            parser.parse("--config-file")
        }.withMessageContaining("config-file")
    }
}
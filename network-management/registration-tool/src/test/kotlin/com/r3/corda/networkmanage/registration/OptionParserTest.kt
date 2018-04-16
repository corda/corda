package com.r3.corda.networkmanage.registration

import joptsimple.OptionException
import net.corda.core.internal.div
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class OptionParserTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()
    lateinit var tempDir: Path

    @Before
    fun setup() {
        tempDir = tempFolder.root.toPath()
        Files.createFile(tempDir / "test.file")
        Files.createFile(tempDir / "source.jks")
        Files.createFile(tempDir / "target.jks")
    }

    @Test
    fun `parse registration args correctly`() {
        val options = parseOptions("--config-file", "${tempDir / "test.file"}") as ToolOption.RegistrationOption
        assertThat(options.configFile).isEqualTo(tempDir / "test.file")
    }

    @Test
    fun `registration args should be unavailable in key copy mode`() {
        val keyCopyArgs = arrayOf(
                "--importkeystore",
                "--srckeystore", "${tempDir / "source.jks"}",
                "--srcstorepass", "password1",
                "--destkeystore", "${tempDir / "target.jks"}",
                "--deststorepass", "password2",
                "--srcalias", "testalias")
        assertThatThrownBy { parseOptions(*keyCopyArgs, "--config-file", "test.file") }
                .isInstanceOf(OptionException::class.java)
                .hasMessageContaining("Option(s) [config-file] are unavailable given other options on the command line")
    }

    @Test
    fun `key copy args should be unavailable in registration mode`() {
        assertThatThrownBy {
            parseOptions("--config-file", "${tempDir / "test.file"}", "--srckeystore", "${tempDir / "source.jks"}")
        }.isInstanceOf(OptionException::class.java)
                .hasMessageContaining("Option(s) [srckeystore] are unavailable given other options on the command line")
    }

    @Test
    fun `all import keystore options`() {
        val keyCopyArgs = arrayOf(
                "--importkeystore",
                "--srckeystore", "${tempDir / "source.jks"}",
                "--srcstorepass", "password1",
                "--destkeystore", "${tempDir / "target.jks"}",
                "--deststorepass", "password2",
                "--srcalias", "testalias",
                "--destalias", "testalias2")
        assertThat(parseOptions(*keyCopyArgs)).isEqualTo(ToolOption.KeyCopierOption(
                sourceFile = tempDir / "source.jks",
                destinationFile = tempDir / "target.jks",
                sourcePassword = "password1",
                destinationPassword = "password2",
                sourceAlias = "testalias",
                destinationAlias = "testalias2"
        ))
    }

    @Test
    fun `minimum import keystore options`() {
        val keyCopyArgs = arrayOf(
                "--importkeystore",
                "--srckeystore", "${tempDir / "source.jks"}",
                "--destkeystore", "${tempDir / "target.jks"}",
                "--srcalias", "testalias")
        assertThat(parseOptions(*keyCopyArgs)).isEqualTo(ToolOption.KeyCopierOption(
                sourceFile = tempDir / "source.jks",
                destinationFile = tempDir / "target.jks",
                sourcePassword = null,
                destinationPassword = null,
                sourceAlias = "testalias",
                destinationAlias = null
        ))
    }
}

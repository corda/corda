package com.r3.corda.networkmanage.registration

import joptsimple.OptionException
import junit.framework.Assert.assertEquals
import net.corda.core.internal.div
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
        requireNotNull(parseOptions("--config-file", "${tempDir / "test.file"}") as? ToolOption.RegistrationOption).apply {
            assertEquals(tempDir / "test.file", configFilePath)
        }
    }

    @Test
    fun `registration args should be unavailable in key copy mode`() {
        assertThatThrownBy {
            val keyCopyArgs = arrayOf("--importkeystore", "--srckeystore", "${tempDir / "source.jks"}", "--srcstorepass", "password1", "--destkeystore", "${tempDir / "target.jks"}", "--deststorepass", "password2", "-srcalias", "testalias")
            parseOptions(*keyCopyArgs, "--config-file", "test.file")
        }.isInstanceOf(OptionException::class.java)
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
    fun `parse key copy option correctly`() {
        val keyCopyArgs = arrayOf("--importkeystore", "--srckeystore", "${tempDir / "source.jks"}", "--srcstorepass", "password1", "--destkeystore", "${tempDir / "target.jks"}", "--deststorepass", "password2", "-srcalias", "testalias")
        requireNotNull(parseOptions(*keyCopyArgs) as? ToolOption.KeyCopierOption).apply {
            assertEquals(tempDir / "source.jks", srcPath)
            assertEquals(tempDir / "target.jks", destPath)
            assertEquals("password1", srcPass)
            assertEquals("password2", destPass)
            assertEquals("testalias", srcAlias)
        }
    }
}

package net.corda.tools.shell

import net.corda.core.internal.toPath
import net.corda.core.utilities.NetworkHostAndPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.slf4j.event.Level
import java.nio.file.Paths
import kotlin.test.assertEquals

class StandaloneShellArgsParserTest {
    private val CONFIG_FILE = StandaloneShellArgsParserTest::class.java.getResource("/config.conf").toPath()

    @Test
    fun args_to_cmd_options() {
        val args = arrayOf("--config-file", "/x/y/z/config.conf",
                "--commands-directory", "/x/y/commands",
                "--cordpass-directory", "/x/y/cordapps",
                "--host", "alocalhost",
                "--port", "1234",
                "--user", "demo",
                "--password", "abcd1234",
                "--logging-level", "DEBUG",
                "--sshd-port", "2223",
                "--sshd-hostkey-directory", "/x/y/ssh",
                "--help",
                "--keystore-password", "pass1",
                "--truststore-password", "pass2",
                "--keystore-file", "/x/y/keystore.jks",
                "--truststore-file", "/x/y/truststore.jks",
                "--truststore-type", "dummy",
                "--keystore-type", "JKS")

        val expectedOptions = CommandLineOptions(
                configFile = "/x/y/z/config.conf",
                commandsDirectory = Paths.get("/x/y/commands").normalize().toAbsolutePath(),
                cordappsDirectory = Paths.get("/x/y/cordapps").normalize().toAbsolutePath(),
                host = "alocalhost",
                port = "1234",
                user = "demo",
                password = "abcd1234",
                help = true,
                loggingLevel = Level.DEBUG,
                sshdPort = "2223",
                sshdHostKeyDirectory = Paths.get("/x/y/ssh").normalize().toAbsolutePath(),
                keyStorePassword = "pass1",
                trustStorePassword = "pass2",
                keyStoreFile = Paths.get("/x/y/keystore.jks").normalize().toAbsolutePath(),
                trustStoreFile = Paths.get("/x/y/truststore.jks").normalize().toAbsolutePath(),
                trustStoreType = "dummy",
                keyStoreType = "JKS")

        val options = CommandLineOptionParser().parse(*args)

        assertThat(options).isEqualTo(expectedOptions)
    }

    @Test
    fun empty_args_to_cmd_options() {
        val args = emptyArray<String>()

        val expectedOptions = CommandLineOptions(configFile = null,
                commandsDirectory = null,
                cordappsDirectory = null,
                host = null,
                port = null,
                user = null,
                password = null,
                help = false,
                loggingLevel = Level.INFO,
                sshdPort = null,
                sshdHostKeyDirectory = null,
                keyStorePassword = null,
                trustStorePassword = null,
                keyStoreFile = null,
                trustStoreFile = null,
                trustStoreType = null,
                keyStoreType = null)

        val options = CommandLineOptionParser().parse(*args)

        assertEquals(expectedOptions, options)
    }

    @Test
    fun args_to_config() {

        val options = CommandLineOptions(configFile = null,
                commandsDirectory = Paths.get("/x/y/commands"),
                cordappsDirectory = Paths.get("/x/y/cordapps"),
                host = "alocalhost",
                port = "1234",
                user = "demo",
                password = "abcd1234",
                help = true,
                loggingLevel = Level.DEBUG,
                sshdPort = "2223",
                sshdHostKeyDirectory = Paths.get("/x/y/ssh"),
                keyStorePassword = "pass1",
                trustStorePassword = "pass2",
                keyStoreFile = Paths.get("/x/y/keystore.jks"),
                trustStoreFile = Paths.get("/x/y/truststore.jks"),
                keyStoreType = "dummy",
                trustStoreType = "dummy"
                )

        val expectedSsl = ShellSslOptions(sslKeystore = Paths.get("/x/y/keystore.jks"),
                keyStorePassword = "pass1",
                trustStoreFile = Paths.get("/x/y/truststore.jks"),
                trustStorePassword = "pass2")
        val expectedConfig = ShellConfiguration(
                commandsDirectory = Paths.get("/x/y/commands"),
                cordappsDirectory = Paths.get("/x/y/cordapps"),
                user = "demo",
                password = "abcd1234",
                hostAndPort = NetworkHostAndPort("alocalhost", 1234),
                ssl = expectedSsl,
                sshdPort = 2223,
                sshHostKeyDirectory = Paths.get("/x/y/ssh"),
                noLocalShell = false)

        val config = options.toConfig()

        assertEquals(expectedConfig, config)
    }

    @Test
    fun cmd_options_to_config_from_file() {

        val options = CommandLineOptions(configFile = CONFIG_FILE.toString(),
                commandsDirectory = null,
                cordappsDirectory = null,
                host = null,
                port = null,
                user = null,
                password = null,
                help = false,
                loggingLevel = Level.DEBUG,
                sshdPort = null,
                sshdHostKeyDirectory = null,
                keyStorePassword = null,
                trustStorePassword = null,
                keyStoreFile = null,
                trustStoreFile = null,
                keyStoreType = null,
                trustStoreType = null)

        val expectedSsl = ShellSslOptions(sslKeystore = Paths.get("/x/y/keystore.jks"),
                keyStorePassword = "pass1",
                trustStoreFile = Paths.get("/x/y/truststore.jks"),
                trustStorePassword = "pass2")
        val expectedConfig = ShellConfiguration(
                commandsDirectory = Paths.get("/x/y/commands"),
                cordappsDirectory = Paths.get("/x/y/cordapps"),
                user = "demo",
                password = "abcd1234",
                hostAndPort = NetworkHostAndPort("alocalhost", 1234),
                ssl = expectedSsl,
                sshdPort = 2223)

        val config = options.toConfig()

        assertEquals(expectedConfig, config)
    }

    @Test
    fun cmd_options_override_config_from_file() {

        val options = CommandLineOptions(configFile = CONFIG_FILE.toString(),
                commandsDirectory = null,
                cordappsDirectory = null,
                host = null,
                port = null,
                user = null,
                password = "blabla",
                help = false,
                loggingLevel = Level.DEBUG,
                sshdPort = null,
                sshdHostKeyDirectory = null,
                keyStorePassword = null,
                trustStorePassword = null,
                keyStoreFile = Paths.get("/x/y/cmd.jks"),
                trustStoreFile = null,
                keyStoreType = null,
                trustStoreType = null)

        val expectedSsl = ShellSslOptions(sslKeystore = Paths.get("/x/y/cmd.jks"),
                keyStorePassword = "pass1",
                trustStoreFile = Paths.get("/x/y/truststore.jks"),
                trustStorePassword = "pass2")
        val expectedConfig = ShellConfiguration(
                commandsDirectory = Paths.get("/x/y/commands"),
                cordappsDirectory = Paths.get("/x/y/cordapps"),
                user = "demo",
                password = "blabla",
                hostAndPort = NetworkHostAndPort("alocalhost", 1234),
                ssl = expectedSsl,
                sshdPort = 2223)

        val config = options.toConfig()

        assertEquals(expectedConfig, config)
    }
}
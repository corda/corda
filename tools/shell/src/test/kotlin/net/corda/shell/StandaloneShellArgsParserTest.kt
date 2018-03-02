package net.corda.shell

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.Permissions
import net.corda.nodeapi.internal.config.User
import org.junit.Test
import org.slf4j.event.Level
import java.nio.file.Paths
import kotlin.test.assertEquals
import java.io.File


class StandaloneShellArgsParserTest {

    private val CONFIG_FILE = File(javaClass.classLoader.getResource("config.conf")!!.file)

    @Test
    fun args_to_cmd_options() {

        val args = arrayOf("-config-file", "/x/y/z/config.conf",
                "-shell-directory", "/x/y/shell",
                "-cordpass-directory", "/x/y/cordapps",
                "-host", "alocalhost",
                "-port", "1234",
                "-user", "demo",
                "-password", "abcd1234",
                "-logging-level", "DEBUG",
                "-sshd-port", "2223",
                "-help",
                "-keystore-password", "pass1",
                "-truststore-password", "pass2",
                "-keystore-file", "/x/y/keystore.jks",
                "-truststore-file", "/x/y/truststore.jks")

        val expectedOptions = CommandLineOptions(configFile = "/x/y/z/config.conf",
                shellDirectory = Paths.get("/x/y/shell"),
                cordappsDirectory = Paths.get("/x/y/cordapps"),
                host = "alocalhost",
                port = "1234",
                user = "demo",
                password = "abcd1234",
                help = true,
                loggingLevel = Level.DEBUG,
                sshdPort = "2223",
                keyStorePassword = "pass1",
                trustStorePassword = "pass2",
                keyStoreFile = Paths.get("/x/y/keystore.jks"),
                trustStoreFile = Paths.get("/x/y/truststore.jks"))

        val options = CommandLineOptionParser().parse(*args)

        assertEquals(expectedOptions, options)
    }

    @Test
    fun empty_args_to_cmd_options() {
        val args = emptyArray<String>()

        val expectedOptions = CommandLineOptions(configFile = null,
                shellDirectory = Paths.get(".").normalize().toAbsolutePath(),
                cordappsDirectory = null,
                host = null,
                port = null,
                user = null,
                password = null,
                help = false,
                loggingLevel = Level.INFO,
                sshdPort = null,
                keyStorePassword = null,
                trustStorePassword = null,
                keyStoreFile = null,
                trustStoreFile = null)

        val options = CommandLineOptionParser().parse(*args)

        assertEquals(expectedOptions, options)
    }

    @Test
    fun args_to_config() {

        val options = CommandLineOptions(configFile = null,
                shellDirectory = Paths.get("/x/y/shell"),
                cordappsDirectory = Paths.get("/x/y/cordapps"),
                host = "alocalhost",
                port = "1234",
                user = "demo",
                password = "abcd1234",
                help = true,
                loggingLevel = Level.DEBUG,
                sshdPort = "2223",
                keyStorePassword = "pass1",
                trustStorePassword = "pass2",
                keyStoreFile = Paths.get("/x/y/keystore.jks"),
                trustStoreFile = Paths.get("/x/y/truststore.jks"))

        val expectedSsl = ShellSslOptions(sslKeystore = Paths.get("/x/y/keystore.jks"),
                keyStorePassword = "pass1",
                trustStoreFile = Paths.get("/x/y/truststore.jks"),
                trustStorePassword = "pass2")
        val expectedConfig = ShellConfiguration(
                shellDirectory = Paths.get("/x/y/shell"),
                cordappsDirectory = Paths.get("/x/y/cordapps"),
                user = "demo",
                password = "abcd1234",
                hostAndPort = NetworkHostAndPort("alocalhost", 1234),
                ssl = expectedSsl,
                sshdPort = 2223,
                noLocalShell = false)

        val config = options.toConfig()

        assertEquals(expectedConfig, config)
    }

    @Test
    fun acmd_options_to_config_from_file() {

        val options = CommandLineOptions(configFile = CONFIG_FILE.absolutePath,
                shellDirectory = null,
                cordappsDirectory = null,
                host = null,
                port = null,
                user = null,
                password = null,
                help = false,
                loggingLevel = Level.DEBUG,
                sshdPort = null,
                keyStorePassword = null,
                trustStorePassword = null,
                keyStoreFile = null,
                trustStoreFile = null)

        val expectedSsl = ShellSslOptions(sslKeystore = Paths.get("/x/y/keystore.jks"),
                keyStorePassword = "pass1",
                trustStoreFile = Paths.get("/x/y/truststore.jks"),
                trustStorePassword = "pass2")
        val expectedConfig = ShellConfiguration(
                shellDirectory = Paths.get("/x/y/shell"),
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

        val options = CommandLineOptions(configFile = CONFIG_FILE.absolutePath,
                shellDirectory = null,
                cordappsDirectory = null,
                host = null,
                port = null,
                user = null,
                password = "blabla",
                help = false,
                loggingLevel = Level.DEBUG,
                sshdPort = null,
                keyStorePassword = null,
                trustStorePassword = null,
                keyStoreFile = Paths.get("/x/y/cmd.jks"),
                trustStoreFile = null)

        val expectedSsl = ShellSslOptions(sslKeystore = Paths.get("/x/y/cmd.jks"),
                keyStorePassword = "pass1",
                trustStoreFile = Paths.get("/x/y/truststore.jks"),
                trustStorePassword = "pass2")
        val expectedConfig = ShellConfiguration(
                shellDirectory = Paths.get("/x/y/shell"),
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
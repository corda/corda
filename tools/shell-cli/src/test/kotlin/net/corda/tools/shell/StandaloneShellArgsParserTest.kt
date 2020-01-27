package net.corda.tools.shell

import net.corda.core.internal.toPath
import net.corda.core.messaging.ClientRpcSslOptions
import net.corda.core.utilities.NetworkHostAndPort
import org.junit.Test
import java.nio.file.Paths
import kotlin.test.assertEquals

class StandaloneShellArgsParserTest {
    private val CONFIG_FILE = StandaloneShellArgsParserTest::class.java.getResource("/config.conf").toPath()

    @Test(timeout=300_000)
	fun empty_args_to_cmd_options() {
        val expectedOptions = ShellCmdLineOptions()

        assertEquals(expectedOptions.configFile, null)
        assertEquals(expectedOptions.cordappDirectory, null)
        assertEquals(expectedOptions.commandsDirectory, null)
        assertEquals(expectedOptions.host, null)
        assertEquals(expectedOptions.port, null)
        assertEquals(expectedOptions.user, null)
        assertEquals(expectedOptions.password, null)
    }

    @Test(timeout=300_000)
	fun args_to_config() {
        val options = ShellCmdLineOptions()
        options.configFile = null
        options.commandsDirectory = Paths.get("/x/y/commands")
        options.cordappDirectory = Paths.get("/x/y/cordapps")
        options.host = "alocalhost"
        options.port = "1234"
        options.user = "demo"
        options.password = "abcd1234"
        options.trustStorePassword = "pass2"
        options.trustStoreFile = Paths.get("/x/y/truststore.jks")
        options.trustStoreType = "dummy"

        val expectedSsl = ClientRpcSslOptions(
                trustStorePath = Paths.get("/x/y/truststore.jks"),
                trustStorePassword = "pass2")
        val expectedConfig = ShellConfiguration(
                commandsDirectory = Paths.get("/x/y/commands"),
                cordappsDirectory = Paths.get("/x/y/cordapps"),
                user = "demo",
                password = "abcd1234",
                hostAndPort = NetworkHostAndPort("alocalhost", 1234),
                ssl = expectedSsl,
                sshdPort = null,
                sshHostKeyDirectory = null,
                noLocalShell = false)

        val config = options.toConfig()

        assertEquals(expectedConfig, config)
    }

    @Test(timeout=300_000)
	fun cmd_options_to_config_from_file() {
        val options = ShellCmdLineOptions()
        options.configFile = CONFIG_FILE
        options.commandsDirectory = null
        options.cordappDirectory = null
        options.host = null
        options.port = null
        options.user = null
        options.password = null
        options.trustStorePassword = null
        options.trustStoreFile = null
        options.trustStoreType = null

        val expectedConfig = ShellConfiguration(
                commandsDirectory = Paths.get("/x/y/commands"),
                cordappsDirectory = Paths.get("/x/y/cordapps"),
                user = "demo",
                password = "abcd1234",
                hostAndPort = NetworkHostAndPort("alocalhost", 1234),
                ssl = ClientRpcSslOptions(
                        trustStorePath = Paths.get("/x/y/truststore.jks"),
                        trustStorePassword = "pass2"),
                sshdPort = null)

        val config = options.toConfig()

        assertEquals(expectedConfig, config)
    }

    @Test(timeout=300_000)
	fun cmd_options_override_config_from_file() {
        val options = ShellCmdLineOptions()
        options.configFile = CONFIG_FILE
        options.commandsDirectory = null
        options.host = null
        options.port = null
        options.user = null
        options.password = "blabla"
        options.trustStorePassword = null
        options.trustStoreFile = null
        options.trustStoreType = null

        val expectedSsl = ClientRpcSslOptions(
                trustStorePath = Paths.get("/x/y/truststore.jks"),
                trustStorePassword = "pass2")
        val expectedConfig = ShellConfiguration(
                commandsDirectory = Paths.get("/x/y/commands"),
                cordappsDirectory = Paths.get("/x/y/cordapps"),
                user = "demo",
                password = "blabla",
                hostAndPort = NetworkHostAndPort("alocalhost", 1234),
                ssl = expectedSsl,
                sshdPort = null)

        val config = options.toConfig()

        assertEquals(expectedConfig, config)
    }
}
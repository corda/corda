package net.corda.docs

import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.parseAsNodeConfiguration
import net.corda.verifier.Verifier
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.reflect.full.declaredMemberProperties

class ExampleConfigTest {

    private fun <A : Any> readAndCheckConfigurations(vararg configFilenames: String, loadConfig: (Path) -> A) {
        configFilenames.forEach {
            println("Checking $it")
            val configFileResource = ExampleConfigTest::class.java.classLoader.getResource(it)
            val config = loadConfig(Paths.get(configFileResource.toURI()))
            // Force the config fields as they are resolved lazily
            config.javaClass.kotlin.declaredMemberProperties.forEach { member ->
                member.get(config)
            }
        }
    }

    @Test
    fun `example node_confs parses fine`() {
        readAndCheckConfigurations(
                "example-node.conf",
                "example-out-of-process-verifier-node.conf",
                "example-network-map-node.conf"
        ) {
            val baseDirectory = Paths.get("some-example-base-dir")
            ConfigHelper.loadConfig(
                    baseDirectory = baseDirectory,
                    configFile = it
            ).parseAsNodeConfiguration()
        }
    }

    @Test
    fun `example verifier_conf parses fine`() {
        readAndCheckConfigurations(
                "example-verifier.conf"
        ) {
            val baseDirectory = Paths.get("some-example-base-dir")
            Verifier.loadConfiguration(baseDirectory, it)
        }
    }
}
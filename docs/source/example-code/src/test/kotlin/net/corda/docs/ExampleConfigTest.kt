package net.corda.docs

import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.parseAsNodeConfiguration
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberProperties

class ExampleConfigTest {

    private fun <A : Any> readAndCheckConfigurations(vararg configFilenames: String, loadConfig: (Path) -> A) {
        configFilenames.forEach {
            println("Checking $it")
            val configFileResource = ExampleConfigTest::class.java.classLoader.getResource(it)
            val config = loadConfig(Paths.get(configFileResource.toURI()))
            // Force the config fields as they are resolved lazily
            config.javaClass.kotlin.declaredMemberProperties.forEach { member ->
                if (member.visibility == KVisibility.PUBLIC) {
                    member.get(config)
                }
            }
        }
    }

    @Test
    fun `example node_confs parses fine`() {
        readAndCheckConfigurations<NodeConfiguration>(
                "example-node.conf",
                "example-out-of-process-verifier-node.conf"
        ) {
            val baseDirectory = Paths.get("some-example-base-dir")
            ConfigHelper.loadConfig(
                    baseDirectory = baseDirectory,
                    configFile = it
            ).parseAsNodeConfiguration()
        }
    }
}
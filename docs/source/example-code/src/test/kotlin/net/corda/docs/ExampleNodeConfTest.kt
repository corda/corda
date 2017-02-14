package net.corda.docs

import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.FullNodeConfiguration
import org.junit.Test
import java.nio.file.Paths
import kotlin.reflect.declaredMemberProperties

class ExampleNodeConfTest {
    @Test
    fun exampleNodeConfParsesFine() {
        val exampleNodeConfFilenames = arrayOf(
                "example-node.conf",
                "example-network-map-node.conf"
        )

        exampleNodeConfFilenames.forEach {
            println("Checking $it")
            val configResource = ExampleNodeConfTest::class.java.classLoader.getResource(it)
            val baseDirectory = Paths.get("some-example-base-dir")
            val nodeConfig = FullNodeConfiguration(
                    baseDirectory,
                    ConfigHelper.loadConfig(
                            baseDirectory = baseDirectory,
                            configFile = Paths.get(configResource.toURI())
                    )
            )
            // Force the config fields as they are resolved lazily
            nodeConfig.javaClass.kotlin.declaredMemberProperties.forEach { member ->
                member.get(nodeConfig)
            }
        }
    }
}
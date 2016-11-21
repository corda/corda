package net.corda.docs

import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.FullNodeConfiguration
import org.junit.Test
import java.nio.file.Paths
import kotlin.reflect.declaredMemberProperties

class ExampleNodeConfTest {
    @Test
    fun exampleNodeConfParsesFine() {
        val configResource = ExampleNodeConfTest::class.java.classLoader.getResource("example-node.conf")
        val nodeConfig = FullNodeConfiguration(
                ConfigHelper.loadConfig(
                        baseDirectoryPath = Paths.get("some-example-base-dir"),
                        configFileOverride = Paths.get(configResource.toURI())
                )
        )
        nodeConfig.javaClass.kotlin.declaredMemberProperties.forEach { member ->
            member.get(nodeConfig)
        }
    }
}
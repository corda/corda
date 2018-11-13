@file:JvmName("ConfigExporterMain")

package net.corda.core

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.validation.internal.Validated
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.parseAsNodeConfiguration
import net.corda.nodeapi.internal.config.toConfig
import java.io.File

class ConfigExporter {
    fun combineTestNetWithOurConfig(testNetConf: String, ourConf: String, outputFile: String) {
        var ourParsedConfig = ConfigFactory.parseFile(File(ourConf))
        println(ourParsedConfig.root().render(ConfigRenderOptions.concise().setFormatted(true).setJson(false)))
        val testNetParsedConfig = ConfigFactory.parseFile(File(testNetConf))
        ourParsedConfig = ourParsedConfig.withValue("keyStorePassword", testNetParsedConfig.getValue("keyStorePassword"))
        ourParsedConfig = ourParsedConfig.withValue("myLegalName", testNetParsedConfig.getValue("myLegalName"))
        ourParsedConfig = ourParsedConfig.withValue("trustStorePassword", testNetParsedConfig.getValue("trustStorePassword"))
        File(outputFile).writer().use {
            it.write(ourParsedConfig.parseAsNodeConfigWithFallback().orThrow().toConfig().root().render(ConfigRenderOptions.concise().setFormatted(true).setJson(false)))
            it.flush()
        }
    }
}

fun Config.parseAsNodeConfigWithFallback(): Validated<NodeConfiguration, Configuration.Validation.Error> {
    val referenceConfig = ConfigFactory.parseResources("reference.conf")
    val nodeConfig = this
            .withValue("baseDirectory", ConfigValueFactory.fromAnyRef("/opt/corda"))
            .withFallback(referenceConfig)
            .withValue(NodeConfiguration::dataSourceProperties.name, referenceConfig.getValue(NodeConfiguration::dataSourceProperties.name))
            .resolve()
    return nodeConfig.parseAsNodeConfiguration()
}

fun main(args: Array<String>) {
    val configExporter = ConfigExporter()

    val command = args[0]

    when (command) {
        "TEST-NET-COMBINE" -> {
            val testNetConf = args[1]
            val ourConf = args[2]
            val outputFile = args[3]
            configExporter.combineTestNetWithOurConfig(testNetConf, ourConf, outputFile)
        }
        else -> {
            throw IllegalArgumentException("Unknown command: $command")
        }
    }
}
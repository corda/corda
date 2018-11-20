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
import net.corda.nodeapi.internal.config.toConfigValue
import java.io.File

class ConfigExporter {
    fun combineTestNetWithOurConfig(testNetConf: String, ourConf: String, outputFile: String) {
        var ourParsedConfig = ConfigFactory.parseFile(File(ourConf))
        val testNetParsedConfig = ConfigFactory.parseFile(File(testNetConf))
        ourParsedConfig = ourParsedConfig.withValue("keyStorePassword", testNetParsedConfig.getValue("keyStorePassword"))
        ourParsedConfig = ourParsedConfig.withValue("myLegalName", testNetParsedConfig.getValue("myLegalName"))
        ourParsedConfig = ourParsedConfig.withValue("trustStorePassword", testNetParsedConfig.getValue("trustStorePassword"))
        ourParsedConfig = ourParsedConfig.withValue("emailAddress", testNetParsedConfig.getValue("emailAddress"))
        File(outputFile).writer().use { fileWriter ->
            val finalConfig = ourParsedConfig.parseAsNodeConfigWithFallback().value().toConfig()
            var configToWrite = ConfigFactory.empty()
            ourParsedConfig.entrySet().sortedBy { it.key }.forEach { configEntry ->
                //use all keys present in "ourConfig" but get values from "finalConfig"
                val keyWithoutQuotes = configEntry.key.replace("\"", "")
                println("creating config key: $keyWithoutQuotes with value: ${finalConfig.getValue(keyWithoutQuotes)}")
                configToWrite = configToWrite.withValue(keyWithoutQuotes, finalConfig.getValue(keyWithoutQuotes))
            }
            fileWriter.write(configToWrite.root().render(ConfigRenderOptions.concise().setFormatted(true).setJson(false)))
        }
    }

    fun buildGenericCZConfig(ourConf: String, outputFile: String){
        val ourParsedConfig = ConfigFactory.parseFile(File(ourConf))
        File(outputFile).writer().use { fileWriter ->
            val finalConfig = ourParsedConfig.parseAsNodeConfigWithFallback().value().toConfig()
            var configToWrite = ConfigFactory.empty()
            ourParsedConfig.entrySet().sortedBy { it.key }.forEach { configEntry ->
                //use all keys present in "ourConfig" but get values from "finalConfig"
                val keyWithoutQuotes = configEntry.key.replace("\"", "")
                println("creating config key: $keyWithoutQuotes with value: ${finalConfig.getValue(keyWithoutQuotes)}")
                configToWrite = configToWrite.withValue(keyWithoutQuotes, finalConfig.getValue(keyWithoutQuotes))
            }
            fileWriter.write(configToWrite.root().render(ConfigRenderOptions.concise().setFormatted(true).setJson(false)))
        }
    }
}

fun Config.parseAsNodeConfigWithFallback(): Validated<NodeConfiguration, Configuration.Validation.Error> {
    val referenceConfig = ConfigFactory.parseResources("reference.conf")
    val nodeConfig = this
            .withValue("baseDirectory", ConfigValueFactory.fromAnyRef("/opt/corda"))
            .withFallback(referenceConfig)
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
        "GENERIC-CZ" -> {
            val ourConf = args[1]
            val outputFile = args[2]
            configExporter.buildGenericCZConfig(ourConf, outputFile)
        }
        else -> {
            throw IllegalArgumentException("Unknown command: $command")
        }
    }
}
package com.r3corda.node

import com.r3corda.node.services.config.FullNodeConfiguration
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import joptsimple.OptionParser
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

val log = LoggerFactory.getLogger("Main")

object ParamsSpec {
    val parser = OptionParser()

    // The intent of allowing a command line configurable directory and config path is to allow deployment flexibility.
    // Other general configuration should live inside the config file unless we regularly need temporary overrides on the command line
    val baseDirectoryArg =
            parser.accepts("base-directory", "The directory to put all files under")
                    .withOptionalArg()
    val configFileArg =
            parser.accepts("config-file", "The path to the config file")
                    .withOptionalArg()
}

fun main(args: Array<String>) {
    log.info("Starting Corda Node")
    val cmdlineOptions = try {
        ParamsSpec.parser.parse(*args)
    } catch (ex: Exception) {
        log.error("Unable to parse args", ex)
        System.exit(1)
        return
    }

    val baseDirectoryPath = if (cmdlineOptions.has(ParamsSpec.baseDirectoryArg)) Paths.get(cmdlineOptions.valueOf(ParamsSpec.baseDirectoryArg)) else Paths.get(".").normalize()

    val defaultConfig = ConfigFactory.parseResources("reference.conf")

    val configFile = if (cmdlineOptions.has(ParamsSpec.configFileArg)) {
        File(cmdlineOptions.valueOf(ParamsSpec.configFileArg))
    } else {
        baseDirectoryPath.resolve("node.conf").normalize().toFile()
    }
    val appConfig = ConfigFactory.parseFile(configFile)

    val cmdlineOverrideMap = HashMap<String, Any?>() // If we do require a few other command line overrides eg for a nicer development experience they would go inside this map.
    if (cmdlineOptions.has(ParamsSpec.baseDirectoryArg)) {
        cmdlineOverrideMap.put("basedir", baseDirectoryPath.toString())
    }
    val overrideConfig = ConfigFactory.parseMap(cmdlineOverrideMap)

    val mergedAndResolvedConfig = overrideConfig.withFallback(appConfig).withFallback(defaultConfig).resolve()

    log.info("config:\n ${mergedAndResolvedConfig.root().render(ConfigRenderOptions.defaults())}")
    val conf = FullNodeConfiguration(mergedAndResolvedConfig)
    val dir = conf.basedir.toAbsolutePath().normalize()
    logInfo(args, dir)

    try {
        val dirFile = dir.toFile()
        if (!dirFile.exists()) {
            dirFile.mkdirs()
        }

        val node = conf.createNode()
        node.start()
        try {
            // TODO create a proper daemon and/or provide some console handler to give interactive commands
            while (true) Thread.sleep(Long.MAX_VALUE)
        } catch(e: InterruptedException) {
            node.stop()
        }
    } catch (e: Exception) {
        log.error("Exception during node startup", e)
        System.exit(1)
    }
    System.exit(0)
}

private fun logInfo(args: Array<String>, dir: Path?) {
    log.info("Main class: ${FullNodeConfiguration::class.java.protectionDomain.codeSource.location.toURI().getPath()}")
    val info = ManagementFactory.getRuntimeMXBean()
    log.info("CommandLine Args: ${info.getInputArguments().joinToString(" ")}")
    log.info("Application Args: ${args.joinToString(" ")}")
    log.info("bootclasspath: ${info.bootClassPath}")
    log.info("classpath: ${info.classPath}")
    log.info("VM ${info.vmName} ${info.vmVendor} ${info.vmVersion}")
    log.info("Machine: ${InetAddress.getLocalHost().hostName}")
    log.info("Working Directory: ${dir}")
}


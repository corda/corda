/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

@file:JvmName("WebServer")

package net.corda.webserver

import com.typesafe.config.ConfigException
import net.corda.core.internal.div
import net.corda.core.internal.rootCause
import net.corda.webserver.internal.NodeWebServer
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.net.InetAddress
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val startTime = System.currentTimeMillis()
    val argsParser = ArgsParser()

    val cmdlineOptions = try {
        argsParser.parse(*args)
    } catch (ex: Exception) {
        println("Unknown command line arguments: ${ex.message}")
        exitProcess(1)
    }

    // Maybe render command line help.
    if (cmdlineOptions.help) {
        argsParser.printHelp(System.out)
        exitProcess(0)
    }

    // Set up logging.
    if (cmdlineOptions.logToConsole) {
        // This property is referenced from the XML config file.
        System.setProperty("consoleLogLevel", "info")
    }

    System.setProperty("log-path", (cmdlineOptions.baseDirectory / "logs/web").toString())
    val log = LoggerFactory.getLogger("Main")
    println("This Corda-specific web server is deprecated and will be removed in future.")
    println("Please switch to a regular web framework like Spring, J2EE or Play Framework.")
    println()
    println("Logs can be found in ${System.getProperty("log-path")}")

    val conf = try {
        WebServerConfig(cmdlineOptions.baseDirectory, cmdlineOptions.loadConfig())
    } catch (e: ConfigException) {
        println("Unable to load the configuration file: ${e.rootCause.message}")
        exitProcess(2)
    }

    log.info("Main class: ${WebServerConfig::class.java.protectionDomain.codeSource.location.toURI().path}")
    val info = ManagementFactory.getRuntimeMXBean()
    log.info("CommandLine Args: ${info.inputArguments.joinToString(" ")}")
    log.info("Application Args: ${args.joinToString(" ")}")
    log.info("bootclasspath: ${info.bootClassPath}")
    log.info("classpath: ${info.classPath}")
    log.info("VM ${info.vmName} ${info.vmVendor} ${info.vmVersion}")
    log.info("Machine: ${InetAddress.getLocalHost().hostName}")
    log.info("Working Directory: ${cmdlineOptions.baseDirectory}")
    log.info("Starting as webserver on ${conf.webAddress}")

    try {
        val server = NodeWebServer(conf)
        server.start()
        val elapsed = (System.currentTimeMillis() - startTime) / 10 / 100.0
        println("Webserver started up in $elapsed sec")
        server.run()
    } catch (e: Exception) {
        log.error("Exception during node startup", e)
        exitProcess(1)
    }
}
/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.jmeter

import net.corda.core.internal.div
import org.apache.jmeter.JMeter
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence

/**
 * A wrapper around JMeter to make it run without having a JMeter download installed locally.  One mode is used for
 * running on a remote cluster using an all-in-one bundle JAR using Capsule. The other is just used to run based on current
 * classpath, but with optional SSH tunnelling logic automatically invoked.
 */
class Launcher {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val logger = LoggerFactory.getLogger(this::class.java)
            logger.info("Launcher called with ${args.toList()}")
            val jmeter = JMeter()
            val capsuleDir = System.getProperty("capsule.dir")
            if (capsuleDir != null) {
                // We are running under Capsule, so assume we want a JMeter slave server to be controlled from
                // elsewhere.
                logger.info("Starting JMeter in server mode from $capsuleDir")
                val capsuleDirPath = Paths.get(capsuleDir)
                // Add all JMeter and Corda jars onto the JMeter search_paths
                val searchPath = Files.list(capsuleDirPath).asSequence().filter {
                    val filename = it.fileName.toString()
                    filename.endsWith(".jar") && (filename.contains("corda") || filename.contains("jmeter", true))
                }.joinToString(";")
                logger.info("search_paths = $searchPath")
                System.setProperty("search_paths", searchPath)
                // Set the JMeter home as a property rather than command line arg, due to inconsistent code in JMeter.
                System.setProperty("jmeter.home", capsuleDir)
                // Create two dirs that JMeter expects, if they don't already exist.
                Files.createDirectories(capsuleDirPath / "lib" / "ext")
                Files.createDirectories(capsuleDirPath / "lib" / "junit")
                // Now see if we have a hostname specific property file, and if so, add it.
                val hostName = InetAddress.getLocalHost().hostName
                val hostSpecificConfigFile = capsuleDirPath / "$hostName.properties"
                logger.info("Attempting to use host-specific properties file $hostSpecificConfigFile")
                val extraArgs = if (Files.exists(hostSpecificConfigFile)) {
                    logger.info("Found host-specific properties file")
                    arrayOf("-q", hostSpecificConfigFile.toString())
                } else {
                    emptyArray()
                }
                jmeter.start(arrayOf("-s", "-p", (capsuleDirPath / "jmeter.properties").toString()) + extraArgs + args)
            } else {
                val searchPath = Files.readAllLines(Paths.get(System.getProperty("search_paths_file"))).first()
                logger.info("search_paths = $searchPath")
                System.setProperty("search_paths", searchPath)
                jmeter.start(maybeOpenSshTunnels(args))
            }
        }

        fun maybeOpenSshTunnels(args: Array<String>): Array<String> {
            // We trim the args at the point "-Xssh" appears in the array of args.  Anything after that is a host to
            // SSH tunnel to. Also get and remove the "-XsshUser" argument if it appears.
            var index = 0
            var userName = System.getProperty("user.name")
            val returnArgs = mutableListOf<String>()
            while (index < args.size) {
                if (args[index] == "-XsshUser") {
                    ++index
                    if (index == args.size || args[index].startsWith("-")) {
                        throw IllegalArgumentException(args.toList().toString())
                    }
                    userName = args[index]
                } else if (args[index] == "-Xssh") {
                    // start ssh
                    Ssh.createSshTunnels(args.copyOfRange(index + 1, args.size), userName, false)
                    return returnArgs.toTypedArray()
                } else {
                    returnArgs.add(args[index])
                }
                index++
            }
            return returnArgs.toTypedArray()
        }
    }
}
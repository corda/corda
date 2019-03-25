package com.r3.corda.jmeter

import com.google.common.net.HostAndPort
import net.corda.core.internal.div
import org.apache.commons.io.FileUtils
import org.apache.jmeter.NewDriver
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.lang.Exception
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.asSequence

/**
 * A wrapper around JMeter to make it run without having a JMeter download installed locally.  One mode is used for
 * running on a remote cluster using an all-in-one bundle JAR using Capsule. The other is just used to run based on current
 * classpath, but with optional SSH tunnelling logic automatically invoked.
 */
class Launcher {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
        @JvmStatic
        fun main(args: Array<String>) {
            logger.info("Launcher called with ${args.toList()}")
            val cmdLine = LauncherCommandLine()
            CommandLine.populateCommand(cmdLine, *args)

            // If help has been requested, print our and jmeter command line help.
            if (cmdLine.helpRequested) {
                printHelp(cmdLine)
                return
            }

            try {
                val capsuleDir = System.getProperty("capsule.dir")
                val searchPath = if (capsuleDir != null) prepareJMeterPropsCapsule(cmdLine, capsuleDir) else prepareJMeterPropsGradle(cmdLine)
                val (jMeterArgs, jMeterPropertiesFile, serverRmiMappings) = prepareJMeterArguments(cmdLine)
                if (!cmdLine.sshHosts.isEmpty()) {
                    Ssh.createSshTunnels(cmdLine.sshHosts.toTypedArray(), jMeterPropertiesFile.toString(), serverRmiMappings, cmdLine.sshUser)
                }
                logger.info("search_paths = $searchPath")
                System.setProperty("search_paths", searchPath)
                NewDriver.main(jMeterArgs.toTypedArray())
            } catch (e: Throwable) {
                println(e.message)
                printHelp(cmdLine)
            }
        }

        internal data class JMeterArgsPlus(val jmeterArgs: Collection<String>, val jmeterPropertiesFile: Path, val serverRmiMappings: Map<String, Int>)

        internal fun prepareJMeterPropsCapsule(cmdLine: LauncherCommandLine, capsuleDir: String): String {
            logger.info("Starting JMeter in capsule mode from $capsuleDir")
            val capsuleDirPath = Paths.get(capsuleDir)
            // Add all JMeter and Corda jars onto the JMeter search_paths, adding any search paths given by the user
            val searchPath = Files.list(capsuleDirPath).asSequence().sorted().filter {
                val filename = it.fileName.toString()
                filename.endsWith(".jar") && (filename.contains("corda") || filename.contains("jmeter", true))
            }.plus(listOf(cmdLine.additionalSearchPaths).filter(String::isNotBlank)).joinToString(";")
            logger.info("Generated search_paths = $searchPath")

            // Set the JMeter home as a property rather than command line arg, due to inconsistent code in JMeter.
            System.setProperty("jmeter.home", capsuleDir)

            // Create two dirs that JMeter expects, if they don't already exist.
            Files.createDirectories(capsuleDirPath / "lib" / "ext")
            Files.createDirectories(capsuleDirPath / "lib" / "junit")
            return searchPath
        }

        internal fun prepareJMeterPropsGradle(cmdLine: LauncherCommandLine): String {
            // check that a search_paths_file has been provided as a system property and throw meaningful error otherwise
            val searchPathsFilename = System.getProperty("search_paths_file")
            if (searchPathsFilename.isNullOrBlank()) {
                throw LauncherException("System property search_paths_file must be set when running without capsule")
            }
            if (System.getProperty("jmeter.home").isNullOrBlank()) {
                throw LauncherException("System property jmeter.home must be set when running without capsule")
            }
            val searchPath = Files.readAllLines(Paths.get(searchPathsFilename)).first() + if (cmdLine.additionalSearchPaths.isBlank()) "" else ";${cmdLine.additionalSearchPaths}"
            logger.info("search_paths read from $searchPathsFilename: $searchPath")
            return searchPath
        }

        internal fun prepareJMeterArguments(cmdLine: LauncherCommandLine): JMeterArgsPlus {
            val jMeterHome = File(System.getProperty("jmeter.home")).toPath()
            val jMeterPropertiesFile = if (cmdLine.jMeterProperties.isBlank()) {
                (jMeterHome / "jmeter.properties").toAbsolutePath()
            } else {
                File(cmdLine.jMeterProperties).toPath()
            }
            val serverRmiMappings = if (!cmdLine.serverRmiMappings.isBlank()) {
                readHostAndPortMap(cmdLine.serverRmiMappings)
            } else {
                if (!cmdLine.sshHosts.isEmpty()) {
                    throw LauncherException("Enabling ssh tunneling requires providing rmi mappings via -XserverRmiMappings")
                }
                emptyMap()
            }

            // we want to add the jMeter properties file here - it must not be part of the user specified jMeter
            // arguments
            val jMeterArgs = cmdLine.jMeterArguments.toMutableList()
            if ("-p" in jMeterArgs) {
                throw LauncherException("To choose jmeter.properties, use the -XjmeterProperties flag, not -p for JMeter arguments")
            }
            jMeterArgs.addAll(listOf("-p", jMeterPropertiesFile.toString()))

            // if running as a server, add a server.rmi.port configuration file if the port is configured
            // if it isn't just carry on regardless (the user might not want to use ssh tunnels, we can't
            // know on the server side)
            if ("-s" in jMeterArgs) {
                val hostName = InetAddress.getLocalHost().hostName
                val port = serverRmiMappings[hostName]
                if (port != null) {
                    val rmiPropsFile = (jMeterHome / "server-rmi.properties").toFile()
                    rmiPropsFile.writeText("server.rmi.localport=$port")
                    jMeterArgs.addAll(listOf("-q", rmiPropsFile.toString()))
                    logger.info("Starting jmeter server using mapped server.rmi.localport=$port")
                } else {
                    logger.info("No rmi server mapping found, using default server.rmi.localport - assuming no ssh tunnelling in effect")
                }

            }
            return JMeterArgsPlus(jMeterArgs, jMeterPropertiesFile, serverRmiMappings)
        }

        private fun printHelp(cmdLine: LauncherCommandLine) {
            // for JMeter to actually print the help, we need to provide it a jmeter.properties file - it can be empty,
            // but has to be present or we never see the help message. 🤦
            CommandLine.usage(cmdLine, System.out)
            val tmpDirPath = File(System.getProperty("java.io.tmpdir")).toPath() / "jmeter-emergency"
            val tmpDir = tmpDirPath.toFile()

            try {
                if (tmpDir.exists()) {
                    FileUtils.deleteDirectory(tmpDir)
                }
                // Create two dirs that JMeter expects, if they don't already exist.
                Files.createDirectories(tmpDirPath / "lib" / "ext")
                Files.createDirectories(tmpDirPath / "lib" / "junit")

                // Set the JMeter home as a property rather than command line arg, due to inconsistent code in JMeter.
                System.setProperty("jmeter.home", tmpDirPath.toString())
                val tmpPropertyFile = (tmpDirPath / "jmeter.properties").toFile()
                tmpPropertyFile.writeText("")
                NewDriver.main(arrayOf("--?", "-p", tmpPropertyFile.absolutePath))
            } finally {
                FileUtils.deleteDirectory(tmpDir)
            }
        }

        internal fun readHostAndPortMap(filename: String): Map<String, Int> {
            return File(filename).readLines().filter { !it.isBlank() && !it.startsWith("#") }.map {
                val hostAndPort = HostAndPort.fromString(it)
                hostAndPort.host to hostAndPort.port
            }.toMap()
        }

        /**
         * Exception class for error handling while running our JMeter launcher code.
         */
        class LauncherException(message: String) : Exception(message)
    }
}

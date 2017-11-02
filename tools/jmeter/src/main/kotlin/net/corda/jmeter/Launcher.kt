package net.corda.jmeter

import net.corda.core.internal.div
import org.apache.jmeter.JMeter
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence

class Launcher {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val logger = LoggerFactory.getLogger(this::class.java)
            val jmeter = JMeter()
            val capsuleDir = System.getProperty("capsule.dir")
            if (capsuleDir != null) {
                // We are running under Capsule, so assume we want a JMeter slave server to be controlled from
                // elsewhere.
                logger.info("Starting JMeter in server mode from $capsuleDir")
                // Add all JMeter and Corda jars onto the JMeter search_paths
                val searchPath = Files.list(Paths.get(capsuleDir)).asSequence().filter {
                    val filename = it.fileName.toString()
                    filename.endsWith(".jar") && (filename.contains("corda") || filename.contains("jmeter", true))
                }.joinToString(";")
                logger.info("search_paths = $searchPath")
                System.setProperty("search_paths", searchPath)
                // Set the JMeter home as a property rather than command line arg, due to inconsistent code in JMeter.
                System.setProperty("jmeter.home", capsuleDir)
                // Create two dirs that JMeter expects, if they don't already exist.
                Files.createDirectories(Paths.get(capsuleDir) / "lib" / "ext")
                Files.createDirectories(Paths.get(capsuleDir) / "lib" / "junit")
                jmeter.start(arrayOf("-s", "-p", "$capsuleDir/jmeter.properties") + args)
            } else {
                jmeter.start(args)
            }
        }
    }
}
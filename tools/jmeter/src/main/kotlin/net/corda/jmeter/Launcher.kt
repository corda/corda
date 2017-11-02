package net.corda.jmeter

import org.apache.jmeter.JMeter
import org.slf4j.LoggerFactory

class Launcher {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val logger = LoggerFactory.getLogger(this::class.java)
            val jmeter = JMeter()
            val capsuleDir = System.getProperty("capsule.dir")
            if (capsuleDir != null) {
                // We are running under Capsule, so assume we want a JMeter distributed server to be controlled from
                // elsewhere.
                logger.info("Starting JMeter in server mode from $capsuleDir")
                jmeter.start(arrayOf("-s", "-d", capsuleDir, "-p", "$capsuleDir/jmeter.properties") + args)
            } else {
                jmeter.start(args)
            }
        }
    }
}
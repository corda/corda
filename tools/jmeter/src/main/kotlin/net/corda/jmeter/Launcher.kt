package net.corda.jmeter

import org.apache.jmeter.JMeter

class Launcher {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val jmeter = JMeter()
            jmeter.start(args)
        }
    }
}
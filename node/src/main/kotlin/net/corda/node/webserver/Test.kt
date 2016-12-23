package net.corda.node.webserver

import net.corda.node.driver.driver
import net.corda.node.services.config.FullNodeConfiguration

fun main(args: Array<String>) {
    // TODO: Print basic webserver info
    System.setProperty("consoleLogLevel", "info")
    driver {
        val node = startNode().get()
        val server = WebServer(node.configuration).start()
        waitForAllNodesToFinish()
    }
}
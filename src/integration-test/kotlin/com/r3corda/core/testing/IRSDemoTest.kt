package com.r3corda.core.testing

import com.r3corda.demos.DemoConfig
import com.r3corda.demos.runIRSDemo
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class IRSDemoTest {
    @Test fun `runs IRS demo`() {
        val dirA = Paths.get("./nodeA")
        val dirB = Paths.get("./nodeB")
        try {
            setupNode(dirA, "NodeA")
            setupNode(dirB, "NodeB")
            val threadA = startNode(dirA, "NodeA")
            val threadB = startNode(dirB, "NodeB")
            runTrade()
            runDateChange()
            stopNode(threadA)
            stopNode(threadB)
        } finally {
            cleanup(dirA)
            cleanup(dirB)
        }
    }
}

private fun setupNode(dir: Path, nodeType: String) {
    runIRSDemo(arrayOf("--role", "Setup" + nodeType, "--dir", dir.toString()))
}

private fun startNode(dir: Path, nodeType: String): Thread {
    val config = DemoConfig(true)
    val nodeThread = thread(true, false, null, nodeType, -1, {
        try {
            runIRSDemo(arrayOf("--role", nodeType, "--dir", dir.toString()), config)
        } finally {
            // Will only reach here during error or after node is stopped, so ensure lock is unlocked.
            config.nodeReady.countDown()
        }
    })
    config.nodeReady.await()
    return nodeThread
}

private fun runTrade() {
    assertEquals(runIRSDemo(arrayOf("--role", "Trade", "trade1")), 0)
}

private fun runDateChange() {
    assertEquals(runIRSDemo(arrayOf("--role", "Date", "2017-01-02")), 0)
}

private fun stopNode(nodeThread: Thread) {
    // The demo is designed to exit on interrupt
    nodeThread.interrupt()
}

private fun cleanup(dir: Path) {
    println("Erasing: " + dir.toString())
    dir.toFile().deleteRecursively()
}
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
            startNode(dirA, "NodeA")
            startNode(dirB, "NodeB")
            runTrade()
            runDateChange()
        } finally {
            cleanup(dirA)
            cleanup(dirB)
        }
    }
}

private fun setupNode(dir: Path, nodeType: String) {
    runIRSDemo(arrayOf("--role", "Setup" + nodeType, "--dir", dir.toString()))
}

private fun startNode(dir: Path, nodeType: String) {
    val config = DemoConfig(true)
    thread(true, false, null, nodeType, -1, {
        try {
            runIRSDemo(arrayOf("--role", nodeType, "--dir", dir.toString()), config)
        } finally {
            // Will only reach here during error or after node is stopped, so ensure lock is unlocked.
            config.nodeReady.countDown()
        }
    })
    config.nodeReady.await()
}

private fun runTrade() {
    assertEquals(runIRSDemo(arrayOf("--role", "Trade", "trade1")), 0)
}

private fun runDateChange() {
    assertEquals(runIRSDemo(arrayOf("--role", "Date", "2017-01-02")), 0)
}

private fun cleanup(dir: Path) {
    println("Erasing: " + dir.toString())
    dir.toFile().deleteRecursively()
}
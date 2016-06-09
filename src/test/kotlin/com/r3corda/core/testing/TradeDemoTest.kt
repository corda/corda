package com.r3corda.core.testing

import com.r3corda.demos.DemoConfig
import com.r3corda.demos.runTraderDemo
import org.junit.Test
import java.nio.file.Paths
import kotlin.concurrent.thread
import kotlin.test.assertEquals

class TraderDemoTest {
    @Test fun `runs trader demo`() {
        try {
            runBuyer()
            runSeller()
        } finally {
            cleanup()
        }
    }
}

private fun runBuyer() {
    val config = DemoConfig(true)
    thread(true, false, null, "Buyer", -1, {
        try {
            runTraderDemo(arrayOf("--role", "BUYER"), config)
        } finally {
            // Will only reach here during error or after node is stopped, so ensure lock is unlocked.
            config.nodeReady.countDown()
        }
    })
    config.nodeReady.await()
}

private fun runSeller() {
    val config = DemoConfig(true)
    assertEquals(runTraderDemo(arrayOf("--role", "SELLER"), config), 0)
}

private fun cleanup() {
    val dir = Paths.get("trader-demo")
    println("Erasing " + dir)
    dir.toFile().deleteRecursively()
}
package com.r3corda.core.testing

import com.r3corda.demos.runTraderDemo
import org.junit.Test
import java.nio.file.Path
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
    thread(true, false, null, "Buyer", -1, { runTraderDemo(arrayOf("--role", "BUYER"), true) })
    Thread.sleep(5000)
}

private fun runSeller() {
    assertEquals(runTraderDemo(arrayOf("--role", "SELLER"), true), 0)
}

private fun cleanup() {
    val dir = Paths.get("trader-demo")
    println("Erasing " + dir)
    dir.toFile().deleteRecursively()
}
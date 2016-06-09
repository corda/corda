package com.r3corda.core.testing

import org.junit.Test
import java.nio.file.Paths
import kotlin.test.assertEquals

class TraderDemoTest {
    @Test fun `runs trader demo`() {
        var nodeProc: Process? = null
        try {
            nodeProc = runBuyer()
            runSeller()
        } finally {
            nodeProc?.destroy()
            cleanup()
        }
    }
}

private fun runBuyer(): Process {
    val args = listOf("--role", "BUYER")
    val proc = spawn("com.r3corda.demos.TraderDemoKt", args)
    Thread.sleep(15000)
    return proc
}

private fun runSeller() {
    val args = listOf("--role", "SELLER")
    val proc = spawn("com.r3corda.demos.TraderDemoKt", args)
    proc.waitFor();
    assertEquals(proc.exitValue(), 0)
}

private fun cleanup() {
    val dir = Paths.get("trader-demo")
    println("Erasing " + dir)
    dir.toFile().deleteRecursively()
}
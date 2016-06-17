package com.r3corda.core.testing

import com.r3corda.core.testing.utilities.spawn
import com.r3corda.core.testing.utilities.waitForNodeStartup
import org.junit.Test
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
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
    waitForNodeStartup("http://localhost:31338")
    return proc
}

private fun runSeller() {
    val args = listOf("--role", "SELLER")
    val proc = spawn("com.r3corda.demos.TraderDemoKt", args)
    assertEquals(proc.waitFor(30, TimeUnit.SECONDS), true);
    assertEquals(proc.exitValue(), 0)
}

private fun cleanup() {
    val dir = Paths.get("trader-demo")
    println("Erasing " + dir)
    dir.toFile().deleteRecursively()
}
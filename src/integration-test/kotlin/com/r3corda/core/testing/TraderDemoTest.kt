package com.r3corda.core.testing

import com.r3corda.core.testing.utilities.assertExitOrKill
import com.r3corda.core.testing.utilities.ensureNodeStartsOrKill
import com.r3corda.core.testing.utilities.spawn
import org.junit.Test
import java.nio.file.Paths
import kotlin.test.assertEquals

class TraderDemoTest {
    @Test fun `runs trader demo`() {
        var nodeProc: Process? = null
        try {
            cleanupFiles()
            nodeProc = runBuyer()
            runSeller()
        } finally {
            nodeProc?.destroy()
            cleanupFiles()
        }
    }
}

private fun runBuyer(): Process {
    println("Running Buyer")
    val args = listOf("--role", "BUYER")
    val proc = spawn("com.r3corda.demos.TraderDemoKt", args)
    ensureNodeStartsOrKill(proc, freeLocalHostAndPort())
    return proc
}

private fun runSeller() {
    println("Running Seller")
    val args = listOf("--role", "SELLER")
    val proc = spawn("com.r3corda.demos.TraderDemoKt", args)
    assertExitOrKill(proc);
    assertEquals(proc.exitValue(), 0)
}

private fun cleanupFiles() {
    println("Cleaning up TraderDemoTest files")
    val dir = Paths.get("trader-demo")
    println("Erasing " + dir)
    dir.toFile().deleteRecursively()
}
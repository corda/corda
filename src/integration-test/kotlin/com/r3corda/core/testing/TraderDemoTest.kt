package com.r3corda.core.testing

import com.google.common.net.HostAndPort
import com.r3corda.core.testing.utilities.assertExitOrKill
import com.r3corda.core.testing.utilities.ensureNodeStartsOrKill
import com.r3corda.core.testing.utilities.spawn
import org.junit.Test
import java.nio.file.Paths
import kotlin.test.assertEquals

class TraderDemoTest {
    @Test fun `runs trader demo`() {
        val buyerAddr = freeLocalHostAndPort()
        var nodeProc: Process? = null
        try {
            cleanupFiles()
            nodeProc = runBuyer(buyerAddr)
            runSeller(buyerAddr)
        } finally {
            nodeProc?.destroy()
            cleanupFiles()
        }
    }
}

private fun runBuyer(buyerAddr: HostAndPort): Process {
    println("Running Buyer")
    val args = listOf("--role", "BUYER", "--network-address", buyerAddr.toString())
    val proc = spawn("com.r3corda.demos.TraderDemoKt", args, "TradeDemoBuyer")
    ensureNodeStartsOrKill(proc, buyerAddr)
    return proc
}

private fun runSeller(buyerAddr: HostAndPort) {
    println("Running Seller")
    val sellerAddr = freeLocalHostAndPort()
    val args = listOf(
            "--role", "SELLER",
            "--network-address", sellerAddr.toString(),
            "--other-network-address", buyerAddr.toString())
    val proc = spawn("com.r3corda.demos.TraderDemoKt", args, "TradeDemoSeller")
    assertExitOrKill(proc);
    assertEquals(proc.exitValue(), 0)
}

private fun cleanupFiles() {
    println("Cleaning up TraderDemoTest files")
    val dir = Paths.get("trader-demo")
    println("Erasing " + dir)
    dir.toFile().deleteRecursively()
}
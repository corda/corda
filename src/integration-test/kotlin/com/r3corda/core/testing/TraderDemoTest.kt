package com.r3corda.core.testing

import com.google.common.net.HostAndPort
import com.r3corda.core.testing.utilities.NodeApi
import com.r3corda.core.testing.utilities.TestTimestamp
import com.r3corda.core.testing.utilities.assertExitOrKill
import com.r3corda.core.testing.utilities.spawn
import org.junit.Test
import kotlin.test.assertEquals

class TraderDemoTest {
    @Test fun `runs trader demo`() {
        val buyerAddr = freeLocalHostAndPort()
        val buyerApiAddr = freeLocalHostAndPort()
        val directory = "./build/integration-test/${TestTimestamp.timestamp}/trader-demo"
        var nodeProc: Process? = null
        try {
            nodeProc = runBuyer(directory, buyerAddr, buyerApiAddr)
            runSeller(directory, buyerAddr)
        } finally {
            nodeProc?.destroy()
        }
    }

    companion object {
        private fun runBuyer(baseDirectory: String, buyerAddr: HostAndPort, buyerApiAddr: HostAndPort): Process {
            println("Running Buyer")
            val args = listOf(
                    "--role", "BUYER",
                    "--network-address", buyerAddr.toString(),
                    "--api-address", buyerApiAddr.toString(),
                    "--base-directory", baseDirectory
            )
            val proc = spawn("com.r3corda.demos.TraderDemoKt", args, "TradeDemoBuyer")
            NodeApi.ensureNodeStartsOrKill(proc, buyerApiAddr)
            return proc
        }

        private fun runSeller(baseDirectory: String, buyerAddr: HostAndPort) {
            println("Running Seller")
            val sellerAddr = freeLocalHostAndPort()
            val sellerApiAddr = freeLocalHostAndPort()
            val args = listOf(
                    "--role", "SELLER",
                    "--network-address", sellerAddr.toString(),
                    "--api-address", sellerApiAddr.toString(),
                    "--other-network-address", buyerAddr.toString(),
                    "--base-directory", baseDirectory
            )
            val proc = spawn("com.r3corda.demos.TraderDemoKt", args, "TradeDemoSeller")
            assertExitOrKill(proc)
            assertEquals(proc.exitValue(), 0)
        }

    }
}

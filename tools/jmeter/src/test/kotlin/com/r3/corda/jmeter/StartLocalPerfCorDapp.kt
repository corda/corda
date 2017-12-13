package com.r3.corda.jmeter

import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.nodeapi.internal.config.User
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.node.NotarySpec
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader

class StartLocalPerfCorDapp {
    companion object {
        val log = LoggerFactory.getLogger(this::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            // Typically the RPC port of Bank A is 10004.
            val demoUser = User("perf", "perf", setOf(Permissions.all()))
            net.corda.testing.driver.driver(startNodesInProcess = false,
                    waitForAllNodesToFinish = true,
                    //isDebug = true,
                    notarySpecs = listOf(NotarySpec(DUMMY_NOTARY.name, validating = false)),
                    extraCordappPackagesToScan = listOf("com.r3.corda.enterprise.perftestcordapp")) {
                val (nodeA, nodeB) = listOf(
                        startNode(providedName = net.corda.testing.DUMMY_BANK_A.name, rpcUsers = listOf(demoUser), maximumHeapSize = "1G"),
                        startNode(providedName = net.corda.testing.DUMMY_BANK_B.name, rpcUsers = listOf(demoUser), maximumHeapSize = "1G")
                ).map { it.getOrThrow() }
                log.info("Nodes started!")
                val input = BufferedReader(InputStreamReader(System.`in`))
                do {
                    log.info("Type 'quit' to exit cleanly.")
                } while (input.readLine() != "quit")
                log.info("Quitting... (this sometimes takes a while)")
                nodeA.stop()
                nodeB.stop()
                defaultNotaryHandle.nodeHandles.getOrThrow().map { it.stop() }
            }
        }
    }
}
/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.jmeter

import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
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
            driver(DriverParameters(
                    startNodesInProcess = false,
                    waitForAllNodesToFinish = true,
                    //isDebug = true,
                    notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = false)),
                    extraCordappPackagesToScan = listOf("com.r3.corda.enterprise.perftestcordapp")
            )) {
                val (nodeA, nodeB) = listOf(
                        startNode(providedName = DUMMY_BANK_A_NAME, rpcUsers = listOf(demoUser), maximumHeapSize = "1G"),
                        startNode(providedName = DUMMY_BANK_B_NAME, rpcUsers = listOf(demoUser), maximumHeapSize = "1G")
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
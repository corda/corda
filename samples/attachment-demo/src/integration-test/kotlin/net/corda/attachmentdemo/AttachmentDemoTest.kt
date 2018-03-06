/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.attachmentdemo

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.node.User
import org.junit.ClassRule
import org.junit.Test
import java.util.concurrent.CompletableFuture.supplyAsync

class AttachmentDemoTest : IntegrationTest() {
    companion object {
        @ClassRule @JvmField
        val databaseSchemas = IntegrationTestSchemas(*listOf(DUMMY_BANK_A_NAME, DUMMY_BANK_B_NAME, DUMMY_NOTARY_NAME)
                .map { it.toDatabaseSchemaName() }.toTypedArray())
    }

    // run with a 10,000,000 bytes in-memory zip file. In practice, a slightly bigger file will be used (~10,002,000 bytes).
    // Force INFO logging to prevent printing 10MB arrays in logfiles
    @Test
    fun `attachment demo using a 10MB zip file`() {
        val numOfExpectedBytes = 10_000_000
        driver(DriverParameters(isDebug = true, portAllocation = PortAllocation.Incremental(20000))) {
            val demoUser = listOf(User("demo", "demo", setOf(
                    startFlow<AttachmentDemoFlow>(),
                    invokeRpc(CordaRPCOps::attachmentExists),
                    invokeRpc(CordaRPCOps::uploadAttachment),
                    invokeRpc(CordaRPCOps::openAttachment),
                    invokeRpc(CordaRPCOps::wellKnownPartyFromX500Name),
                    invokeRpc(CordaRPCOps::internalVerifiedTransactionsFeed)
            )))
            val (nodeA, nodeB) = listOf(
                    startNode(providedName = DUMMY_BANK_A_NAME, rpcUsers = demoUser, maximumHeapSize = "1g"),
                    startNode(providedName = DUMMY_BANK_B_NAME, rpcUsers = demoUser, maximumHeapSize = "1g")
            ).map { it.getOrThrow() }
            val webserverHandle = startWebserver(nodeB).getOrThrow()

            val senderThread = supplyAsync {
                CordaRPCClient(nodeA.rpcAddress).start(demoUser[0].username, demoUser[0].password).use {
                    sender(it.proxy, numOfExpectedBytes)
                }
            }

            val recipientThread = supplyAsync {
                CordaRPCClient(nodeB.rpcAddress).start(demoUser[0].username, demoUser[0].password).use {
                    recipient(it.proxy, webserverHandle.listenAddress.port)
                }
            }

            senderThread.getOrThrow()
            recipientThread.getOrThrow()
        }
    }
}

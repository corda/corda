/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.TimedFlow
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.RandomFree
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.node.User
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class TimedFlowMultiThreadedSMMTests : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME.toDatabaseSchemaName(), BOB_NAME.toDatabaseSchemaName(), DUMMY_NOTARY_NAME.toDatabaseSchemaName())

        val requestCount = AtomicInteger(0)
        val invocationCount = AtomicInteger(0)
    }

    @Before
    fun resetCounters() {
        requestCount.set(0)
        invocationCount.set(0)
    }

    @Test
    fun `timed flow is retried`() {
        val user = User("test", "pwd", setOf(Permissions.startFlow<TimedInitiatorFlow>(), Permissions.startFlow<SuperFlow>()))
        driver(DriverParameters(isDebug = true, startNodesInProcess = true,
                portAllocation = RandomFree)) {

            val configOverrides = mapOf("p2pMessagingRetry" to mapOf(
                    "messageRedeliveryDelay" to Duration.ofSeconds(1),
                    "maxRetryCount" to 2,
                    "backoffBase" to 1.0
            ))

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), customOverrides = configOverrides).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()

            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use { rpc ->
                whenInvokedDirectly(rpc, nodeBHandle)
                resetCounters()
                whenInvokedAsSubflow(rpc, nodeBHandle)
            }
        }
    }

    private fun whenInvokedDirectly(rpc: CordaRPCConnection, nodeBHandle: NodeHandle) {
        rpc.proxy.startFlow(::TimedInitiatorFlow, nodeBHandle.nodeInfo.singleIdentity()).returnValue.getOrThrow()
        /* The TimedInitiatorFlow is expected to time out the first time, and succeed the second time. */
        assertEquals(2, invocationCount.get())
    }

    private fun whenInvokedAsSubflow(rpc: CordaRPCConnection, nodeBHandle: NodeHandle) {
        rpc.proxy.startFlow(::SuperFlow, nodeBHandle.nodeInfo.singleIdentity()).returnValue.getOrThrow()
        assertEquals(2, invocationCount.get())
    }

    @StartableByRPC
    class SuperFlow(private val other: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(TimedInitiatorFlow(other))
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class TimedInitiatorFlow(private val other: Party) : FlowLogic<Unit>(), TimedFlow {
        @Suspendable
        override fun call() {
            invocationCount.incrementAndGet()
            val session = initiateFlow(other)
            session.sendAndReceive<Unit>(Unit)
        }
    }

    @InitiatedBy(TimedInitiatorFlow::class)
    class InitiatedFlow(val session: FlowSession) : FlowLogic<Any>() {
        @Suspendable
        override fun call() {
            val value = session.receive<Unit>().unwrap { }
            if (TimedFlowMultiThreadedSMMTests.requestCount.getAndIncrement() == 0) {
                waitForLedgerCommit(SecureHash.randomSHA256())
            } else {
                session.send(value)
            }
        }
    }
}
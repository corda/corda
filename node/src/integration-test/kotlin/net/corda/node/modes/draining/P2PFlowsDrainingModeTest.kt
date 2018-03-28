/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.modes.draining

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.internal.drainAndShutdown
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.map
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions
import net.corda.testing.core.*
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.User
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.fail

class P2PFlowsDrainingModeTest : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(*listOf(ALICE_NAME, BOB_NAME, DUMMY_NOTARY_NAME)
                .map { it.toDatabaseSchemaName() }.toTypedArray())

        private val logger = loggerFor<P2PFlowsDrainingModeTest>()
    }

    private val portAllocation = PortAllocation.Incremental(10000)
    private val user = User("mark", "dadada", setOf(Permissions.all()))
    private val users = listOf(user)

    private var executor: ScheduledExecutorService? = null

    @Before
    fun setup() {
        executor = Executors.newSingleThreadScheduledExecutor()
    }

    @After
    fun cleanUp() {
        executor!!.shutdown()
    }

    @Test
    fun `flows draining mode suspends consumption of initial session messages`() {

        driver(DriverParameters(isDebug = true, startNodesInProcess = false, portAllocation = portAllocation)) {

            val initiatedNode = startNode(providedName = ALICE_NAME).getOrThrow()
            val initiating = startNode(providedName = BOB_NAME, rpcUsers = users).getOrThrow().rpc
            val counterParty = initiatedNode.nodeInfo.singleIdentity()
            val initiated = initiatedNode.rpc

            initiated.setFlowsDrainingModeEnabled(true)

            var shouldFail = true
            initiating.apply {
                val flow = startFlow(::InitiateSessionFlow, counterParty)
                // this should be really fast, for the flow has already started, so 5 seconds should never be a problem
                executor!!.schedule({
                    logger.info("Now disabling flows draining mode for $counterParty.")
                    shouldFail = false
                    initiated.setFlowsDrainingModeEnabled(false)
                }, 5, TimeUnit.SECONDS)
                flow.returnValue.map { result ->
                    if (shouldFail) {
                        fail("Shouldn't happen until flows draining mode is switched off.")
                    } else {
                        assertThat(result).isEqualTo("Hi there answer")
                    }
                }.getOrThrow()
            }
        }
    }

    @Test
    fun `clean shutdown by draining`() {

        driver(DriverParameters(isDebug = true, startNodesInProcess = true, portAllocation = portAllocation)) {

            val nodeA = startNode(providedName = ALICE_NAME, rpcUsers = users).getOrThrow()
            val nodeB = startNode(providedName = BOB_NAME, rpcUsers = users).getOrThrow()
            var successful = false
            val latch = CountDownLatch(1)
            nodeB.rpc.setFlowsDrainingModeEnabled(true)
            IntRange(1, 10).forEach { nodeA.rpc.startFlow(::InitiateSessionFlow, nodeB.nodeInfo.chooseIdentity()) }

            nodeA.rpc.drainAndShutdown()
                    .doOnError { error ->
                        error.printStackTrace()
                        successful = false
                    }
                    .doOnCompleted { successful = true }
                    .doAfterTerminate { latch.countDown() }
                    .subscribe()
            nodeB.rpc.setFlowsDrainingModeEnabled(false)
            latch.await()

            assertThat(successful).isTrue()
        }
    }
}

@StartableByRPC
@InitiatingFlow
class InitiateSessionFlow(private val counterParty: Party) : FlowLogic<String>() {

    @Suspendable
    override fun call(): String {

        val session = initiateFlow(counterParty)
        session.send("Hi there")
        return session.receive<String>().unwrap { it }
    }
}

@InitiatedBy(InitiateSessionFlow::class)
class InitiatedFlow(private val initiatingSession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {

        val message = initiatingSession.receive<String>().unwrap { it }
        initiatingSession.send("$message answer")
    }
}
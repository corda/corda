/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.notaryhealthcheck.cordapp

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.seconds
import net.corda.notaryhealthcheck.utils.Monitorable
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals


class TestScheduleCheckFlow {
    private lateinit var mockNet: MockNetwork
    private lateinit var nodeA: StartedMockNode
    private lateinit var notary: Party

    @Before
    fun setup() {
        mockNet = MockNetwork(threadPerNode = true, cordappPackages = listOf("net.corda.notaryhealthcheck.contract", "net.corda.notaryhealthcheck.cordapp"))

        nodeA = mockNet.createPartyNode()
        notary = mockNet.defaultNotaryIdentity
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun checkDefaultNotary() {
        val target = Monitorable(notary, notary)
        nodeA.startFlow(InstallCheckScheduleStateFlow(listOf(nodeA.info.legalIdentities.first()), target, emptyList(), Instant.now().plusSeconds(1), Instant.MIN, 2, 5, UniqueIdentifier(null)))
        // Careful timing here to make the test pass as reliably and quickly as possible
        // start time is 1s from now, and we run a check every 2 seconds, so there will be one at 1s and one at 3s.
        // after the 4 second wait, we have about a second to grab all the relevant states in a consistent fashion
        // until the next check would start (but the whole thing should be stopped by then)
        Thread.sleep(4.seconds.toMillis())
        nodeA.transaction {
            val successfulChecks = nodeA.services.vaultService.queryBy<SuccessfulCheckState>()

            val pendingChecks = nodeA.services.vaultService.queryBy<ScheduledCheckState>(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED))
            assert(successfulChecks.states.size > 1) { "Expected at least 2 successful checks by now, got ${successfulChecks.states.size}" }
            // sort all successful states, get the second to last one and take it's finish time - that will be the last recorded success. The last one will be
            // handled the next time the scheduled state is run as it was started in the last run and finished after scheduling
            // the current pending state.
            val lastSuccessTime = successfulChecks.states.map { it.state.data.finishTime }.sorted().dropLast(1).last()
            assert(pendingChecks.states.size == 1) { "Expected exactly 1 pending (scheduled check), got ${pendingChecks.states.size}" }
            assertEquals(lastSuccessTime, pendingChecks.states.first().state.data.lastSuccessTime)
        }

        nodeA.startFlow(StopAllChecksFlow()).get()

        val pendingChecksAfterCleanUp = nodeA.transaction {
            nodeA.services.vaultService.queryBy<ScheduledCheckState>(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED))
        }
        assert(pendingChecksAfterCleanUp.states.isEmpty()) { "Expected all pending checks to be removed, got ${pendingChecksAfterCleanUp.states.size}" }
    }


    @Test
    fun checkHangingNotary() {
        val target = Monitorable(notary, notary)
        mockNet.defaultNotaryNode.stop()

        nodeA.startFlow(InstallCheckScheduleStateFlow(listOf(nodeA.info.legalIdentities.first()), target, emptyList(), Instant.now().plusSeconds(1), Instant.MIN, 1, 2, UniqueIdentifier(null)))
        Thread.sleep(5.seconds.toMillis())
        nodeA.transaction {
            val successfulChecks = nodeA.services.vaultService.queryBy<SuccessfulCheckState>()
            assert(successfulChecks.states.isEmpty()) { "Not expecting any successful checks, got ${successfulChecks.states.size}" }

            val startedChecks = nodeA.services.vaultService.queryBy<RunningCheckState>()
            assert(startedChecks.states.size > 1) { "Expected at least 2 started/running checks by now, got ${startedChecks.states.size}" }

            val abandonnedStates = nodeA.services.vaultService.queryBy<AbandonnedCheckState>()
            assert(abandonnedStates.states.size > 1) { "Expected at least 1 abandonned check by now, got ${abandonnedStates.states.size}" }

            val scheduledStates = nodeA.services.vaultService.queryBy<ScheduledCheckState>()
            assert(scheduledStates.states.size == 1) { "Expected exactly 1 pending (scheduled check), got ${scheduledStates.states.size}" }
        }
        nodeA.startFlow(StopAllChecksFlow()).get()

        val pendingChecksAfterCleanUp = nodeA.transaction {
            nodeA.services.vaultService.queryBy<ScheduledCheckState>(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED))
        }
        assert(pendingChecksAfterCleanUp.states.isEmpty()) { "Expected all pending checks to be removed, got ${pendingChecksAfterCleanUp.states.size}" }
    }

    @Test
    fun checkFailingNotary() {
        val lastSuccessTime = Instant.now()
        val target = Monitorable(nodeA.info.legalIdentities.first(), nodeA.info.legalIdentities.first())

        nodeA.startFlow(InstallCheckScheduleStateFlow(listOf(nodeA.info.legalIdentities.first()), target, emptyList(), Instant.now().plusSeconds(1), lastSuccessTime, 2, 5, UniqueIdentifier(null)))
        Thread.sleep(4.seconds.toMillis())

        nodeA.transaction {

            val successfulChecks = nodeA.services.vaultService.queryBy<SuccessfulCheckState>()
            assert(successfulChecks.states.isEmpty()) { "Not expecting any successful checks, got ${successfulChecks.states.size}" }

            val failedStates = nodeA.services.vaultService.queryBy<FailedCheckState>()
            assert(failedStates.states.size > 1) { "Expected at least 2 failed checks by now, got ${failedStates.states.size}" }

            val abandonnedStates = nodeA.services.vaultService.queryBy<AbandonnedCheckState>()
            assert(abandonnedStates.states.isEmpty()) { "Not expecting any abandonned checks, got ${abandonnedStates.states.size}" }

            val scheduledStates = nodeA.services.vaultService.queryBy<ScheduledCheckState>()
            assert(scheduledStates.states.size == 1) { "Expected exactly 1 pending (scheduled check), got ${scheduledStates.states.size}" }
            assert(scheduledStates.states.first().state.data.lastSuccessTime == lastSuccessTime)
        }
        nodeA.startFlow(StopAllChecksFlow())
        Thread.sleep(1.seconds.toMillis())
        val pendingChecksAfterCleanUp = nodeA.transaction {
            nodeA.services.vaultService.queryBy<ScheduledCheckState>(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED))
        }
        assert(pendingChecksAfterCleanUp.states.isEmpty()) { "Expected all pending checks to be removed, got ${pendingChecksAfterCleanUp.states.size}" }
    }
}
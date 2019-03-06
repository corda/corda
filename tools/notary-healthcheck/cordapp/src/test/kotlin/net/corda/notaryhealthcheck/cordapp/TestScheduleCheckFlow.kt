package net.corda.notaryhealthcheck.cordapp

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.seconds
import net.corda.notaryhealthcheck.utils.Monitorable
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.findCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestScheduleCheckFlow {
    private lateinit var mockNet: MockNetwork
    private lateinit var nodeA: StartedMockNode
    private lateinit var notary: Party

    @Before
    fun setup() {
        mockNet = MockNetwork(MockNetworkParameters(
                threadPerNode = true,
                cordappsForAllNodes = listOf(findCordapp("net.corda.notaryhealthcheck.contract"), findCordapp("net.corda.notaryhealthcheck.cordapp"))
        ))

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
        // We wait for 6 seconds to the system enough time to get going and to avoid the test failing because
        // it was starved for threads/runtime. It doesn't matter if we run an extra check. Not great to rely on
        // this kind of timing, but unfortunately, to see if a scheduled state works, we have to wait for it to
        // get kicked off and do something.
        Thread.sleep(6.seconds.toMillis())
        nodeA.transaction {
            val successfulChecks = nodeA.services.vaultService.queryBy<SchedulingContract.SuccessfulCheckState>()

            val pendingChecks = nodeA.services.vaultService.queryBy<SchedulingContract.ScheduledCheckState>(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED))
            assertTrue(successfulChecks.states.size > 1, "Expected at least 2 successful checks by now, got ${successfulChecks.states.size}")
            // sort all successful states, get the second to last one and take it's finish time - that will be the last recorded success. The last one will be
            // handled the next time the scheduled state is run as it was started in the last run and finished after scheduling
            // the current pending state.
            val lastSuccessTime = successfulChecks.states.map { it.state.data.finishTime }.sorted().dropLast(1).last()
            assertEquals(1, pendingChecks.states.size, "Expected exactly 1 pending (scheduled check), got ${pendingChecks.states.size}")
            assertEquals(lastSuccessTime, pendingChecks.states.first().state.data.lastSuccessTime)
        }

        nodeA.startFlow(StopAllChecksFlow()).get()

        val pendingChecksAfterCleanUp = nodeA.transaction {
            nodeA.services.vaultService.queryBy<SchedulingContract.ScheduledCheckState>(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED))
        }
        assertTrue(pendingChecksAfterCleanUp.states.isEmpty(), "Expected all pending checks to be removed, got ${pendingChecksAfterCleanUp.states.size}")
    }


    @Test
    fun checkHangingNotary() {
        val target = Monitorable(notary, notary)
        mockNet.defaultNotaryNode.stop()

        nodeA.startFlow(InstallCheckScheduleStateFlow(listOf(nodeA.info.legalIdentities.first()), target, emptyList(), Instant.now().plusSeconds(1), Instant.MIN, 1, 2, UniqueIdentifier(null)))
        Thread.sleep(5.seconds.toMillis())
        nodeA.transaction {
            val successfulChecks = nodeA.services.vaultService.queryBy<SchedulingContract.SuccessfulCheckState>()
            assertTrue(successfulChecks.states.isEmpty(), "Not expecting any successful checks, got ${successfulChecks.states.size}")

            val startedChecks = nodeA.services.vaultService.queryBy<SchedulingContract.RunningCheckState>()
            assertTrue(startedChecks.states.size > 1, "Expected at least 2 started/running checks by now, got ${startedChecks.states.size}")

            val abandonedStates = nodeA.services.vaultService.queryBy<SchedulingContract.AbandonedCheckState>()
            assertTrue(abandonedStates.states.isNotEmpty(), "Expected at least 1 abandoned check by now, got ${abandonedStates.states.size}")

            val scheduledStates = nodeA.services.vaultService.queryBy<SchedulingContract.ScheduledCheckState>()
            assertEquals(1, scheduledStates.states.size, "Expected exactly 1 pending (scheduled check), got ${scheduledStates.states.size}")
        }
        nodeA.startFlow(StopAllChecksFlow()).get()

        val pendingChecksAfterCleanUp = nodeA.transaction {
            nodeA.services.vaultService.queryBy<SchedulingContract.ScheduledCheckState>(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED))
        }
        assertTrue(pendingChecksAfterCleanUp.states.isEmpty(), "Expected all pending checks to be removed, got ${pendingChecksAfterCleanUp.states.size}")
    }

    @Test
    fun checkFailingNotary() {
        val lastSuccessTime = Instant.now()
        val target = Monitorable(nodeA.info.legalIdentities.first(), nodeA.info.legalIdentities.first())

        nodeA.startFlow(InstallCheckScheduleStateFlow(listOf(nodeA.info.legalIdentities.first()), target, emptyList(), Instant.now().plusSeconds(1), lastSuccessTime, 2, 5, UniqueIdentifier(null)))
        Thread.sleep(4.seconds.toMillis())

        nodeA.transaction {

            val successfulChecks = nodeA.services.vaultService.queryBy<SchedulingContract.SuccessfulCheckState>()
            assertTrue(successfulChecks.states.isEmpty(), "Not expecting any successful checks, got ${successfulChecks.states.size}")

            val failedStates = nodeA.services.vaultService.queryBy<SchedulingContract.FailedCheckState>()
            assertTrue(failedStates.states.size > 1, "Expected at least 2 failed checks by now, got ${failedStates.states.size}")

            val abandonedStates = nodeA.services.vaultService.queryBy<SchedulingContract.AbandonedCheckState>()
            assertTrue(abandonedStates.states.isEmpty(), "Not expecting any abandoned checks, got ${abandonedStates.states.size}")

            val scheduledStates = nodeA.services.vaultService.queryBy<SchedulingContract.ScheduledCheckState>()
            assertEquals(1, scheduledStates.states.size, "Expected exactly 1 pending (scheduled check), got ${scheduledStates.states.size}")
            assertEquals(lastSuccessTime, scheduledStates.states.first().state.data.lastSuccessTime)
        }
        nodeA.startFlow(StopAllChecksFlow())
        Thread.sleep(1.seconds.toMillis())
        val pendingChecksAfterCleanUp = nodeA.transaction {
            nodeA.services.vaultService.queryBy<SchedulingContract.ScheduledCheckState>(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED))
        }
        assertTrue(pendingChecksAfterCleanUp.states.isEmpty(), "Expected all pending checks to be removed, got ${pendingChecksAfterCleanUp.states.size}")
    }
}
package net.corda.notaryhealthcheck.cordapp

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.TransactionBuilder
import net.corda.notaryhealthcheck.contract.SchedulingContract
import net.corda.notaryhealthcheck.utils.Monitorable
import java.time.Instant

/**
 * Flow to install a [ScheduledCheckState] - this is used by the flows that start healthchecks as well
 * as by the [ScheduledCheckFlow] itself to schedule future checks.
 */
@StartableByRPC
class InstallCheckScheduleStateFlow(
        private val participants: List<AbstractParty>,
        private val target: Monitorable,
        private val idsToCheck: List<UniqueIdentifier>,
        private val startTime: Instant,
        private val lastSuccessTime: Instant,
        private val waitTimeSeconds: Int,
        private val waitForOutstandingFlowsSeconds: Int,
        private val newId: UniqueIdentifier) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val state = ScheduledCheckState(participants, newId, idsToCheck, target, startTime, lastSuccessTime, waitTimeSeconds, waitForOutstandingFlowsSeconds)
        val builder = TransactionBuilder(target.notary)
                .addOutputState(state, SchedulingContract.PROGRAM_ID)
                .addCommand(SchedulingContract.emptyCommand(ourIdentity.owningKey))
        val tx = serviceHub.signInitialTransaction(builder)
        serviceHub.recordTransactions(tx)
    }
}
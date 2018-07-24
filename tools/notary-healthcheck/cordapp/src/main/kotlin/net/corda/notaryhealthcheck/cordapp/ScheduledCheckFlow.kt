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

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.SchedulableFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.contextLogger
import net.corda.node.services.api.MonitoringService
import net.corda.node.services.api.ServiceHubInternal
import net.corda.notaryhealthcheck.contract.SchedulingContract
import net.corda.notaryhealthcheck.utils.toHumanReadable
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * This flow gets invoked from a [ScheduledCheckState] as scheduled action. It will consume the calling state and install
 * a new state to start a new check after the appropriate wait time.
 *
 * It will also check all previous states that were still running when the calling state got created, and publish some numbers
 * about outstanding flows, if there are any.
 *
 * It will then go on to actually check its target notary node unless there are already checks in-flight and the target
 * node is either slow or hanging, and the last check has been scheduled less than [waitForOutstandingFlowsSeconds]
 * seconds ago.
 */
@SchedulableFlow
class ScheduledCheckFlow(private val stateRef: StateRef, private val waitTimeSeconds: Int, private val waitForOutstandingFlowsSeconds: Int) : FlowLogic<Unit>() {

    companion object {
        private val log = contextLogger()

        private val X500CleanUpRegex = Regex("[^a-zA-Z\\d]")

        private fun cleanX500forMetrics(name: CordaX500Name): String {
            return name.toString().replace(X500CleanUpRegex, "_")
        }

        private fun formatLastSuccess(lastSuccess: Instant): String {
            return if (lastSuccess > Instant.MIN) {
                "Last successful check: $lastSuccess"
            } else {
                "Never succeeded"
            }
        }

        private data class LatestStates(val ids: List<UniqueIdentifier>, val lastStartTime: Instant, val earliestStartTime: Instant)

        private fun checkRunningStates(idsToCheck: List<UniqueIdentifier>, vaultService: VaultService): LatestStates {
            val criteria = QueryCriteria.LinearStateQueryCriteria(linearId = idsToCheck, status = Vault.StateStatus.UNCONSUMED)
            val statesToCheck = vaultService.queryBy<RunningCheckState>(criteria).states.map { it.state.data }
            var lastStartTime = Instant.MIN
            var earliestStartTime = Instant.MAX
            statesToCheck.forEach {
                lastStartTime = if (it.startTime > lastStartTime) it.startTime else lastStartTime
                earliestStartTime = if (it.startTime < earliestStartTime) it.startTime else earliestStartTime
            }
            return LatestStates(statesToCheck.map { it.linearId }, lastStartTime, earliestStartTime)
        }

        private fun updateLastSuccess(idsToCheck: List<UniqueIdentifier>, vaultService: VaultService, previous: Instant): Instant {
            val criteria = QueryCriteria.LinearStateQueryCriteria(linearId = idsToCheck, status = Vault.StateStatus.UNCONSUMED)
            val statesToCheck = vaultService.queryBy<SuccessfulCheckState>(criteria).states.map { it.state.data }
            var lastSuccessTime = previous
            statesToCheck.forEach {
                lastSuccessTime = if (it.finishTime > lastSuccessTime) it.finishTime else lastSuccessTime
            }
            return lastSuccessTime
        }

    }

    @Suspendable
    override fun call() {
        // get the actual state we are starting/are about to consume
        val state = serviceHub.toStateAndRef<ScheduledCheckState>(stateRef)
        val scheduledState = state.state.data

        // get metrics to report
        val monitoringService = (serviceHub as ServiceHubInternal).monitoringService
        val prefix = "notaryhealthcheck.${cleanX500forMetrics(scheduledState.target.party.name)}"

        val (idsToCheck, startNewCheckFlow, lastSuccessTime, earliestStartTime)
                = checkPreviousStates(scheduledState, monitoringService, prefix)

        // Install new state to schedule the next check
        subFlow(InstallCheckScheduleStateFlow(
                scheduledState.participants,
                scheduledState.target,
                idsToCheck,
                Instant.now().plusSeconds(waitTimeSeconds.toLong()),
                lastSuccessTime,
                waitTimeSeconds,
                waitForOutstandingFlowsSeconds,
                UniqueIdentifier(null)))

        // either start check or abandon this state
        if (startNewCheckFlow) {
            checkNotary(scheduledState, state, lastSuccessTime, monitoringService, prefix)
        } else {
            abandonState(scheduledState, state, lastSuccessTime, earliestStartTime)
        }
    }

    private data class PrevFlowResult(
            val idsToCheck: List<UniqueIdentifier>,
            val needToRunCheck: Boolean,
            val lastSuccessTime: Instant,
            val earliestStartTime: Instant)

    private fun checkPreviousStates(
            state: ScheduledCheckState,
            monitoringService: MonitoringService,
            prefix: String): PrevFlowResult {
        // check if any of the previous flows have succeeded, update the last succeeded time
        val lastSuccessTime = updateLastSuccess(state.statesToCheck, serviceHub.vaultService, state.lastSuccessTime)


        // check if any flows started previously are still in flight - if so log/publish stats about this
        val (statesToCheck, lastStartTime, earliestStartTime)
                = checkRunningStates(state.statesToCheck, serviceHub.vaultService)
        if (!statesToCheck.isEmpty()) {
            val duration = Duration.between(earliestStartTime, Instant.now())
            log.info("${state.target}: Checks in flight: ${statesToCheck.size} Running for: ${duration.toHumanReadable()}.")
            monitoringService.also { it.metrics.timer(prefix + ".maxInflightTime").update(duration.seconds, TimeUnit.SECONDS) }
        }

        // do we need to start a new check flow? We only start a new flow if there are no outstanding flows or the last
        // one has been running for longer than waitForOutstandingFlowsSeconds
        val needToRunCheck = lastStartTime.plusSeconds(waitForOutstandingFlowsSeconds.toLong()) < Instant.now()

        // the next state will have to check for the fate of anything still outstanding plus the flow we
        // are potentially about to start
        return PrevFlowResult(
                if (needToRunCheck) statesToCheck + state.linearId else statesToCheck,
                needToRunCheck,
                lastSuccessTime,
                earliestStartTime)
    }

    @Suspendable
    private fun checkNotary(
            state: ScheduledCheckState,
            stateAndRef: StateAndRef<ScheduledCheckState>,
            lastSuccessTime: Instant,
            monitoringService: MonitoringService,
            prefix: String) {
        val runningState = RunningCheckState(state.linearId, state.participants, Instant.now())
        val runningBuilder = TransactionBuilder(state.target.notary)
                .addInputState(stateAndRef)
                .addOutputState(runningState, SchedulingContract.PROGRAM_ID)
                .addCommand(SchedulingContract.emptyCommand(ourIdentity.owningKey))
        val tx = serviceHub.signInitialTransaction(runningBuilder)
        serviceHub.recordTransactions(tx)

        val finalBuilder = TransactionBuilder(state.target.notary)
                .addInputState(tx.tx.outRef<RunningCheckState>(0))
                .addCommand(SchedulingContract.emptyCommand(ourIdentity.owningKey))

        try {
            monitoringService.also { it.metrics.counter(prefix + ".inflight")?.inc() }
            subFlow(HealthCheckFlow(state.target))

            val successTime = Instant.now()
            log.info("${state.target}: Check successful in ${Duration.between(runningState.startTime, successTime).toHumanReadable()}")
            monitoringService.also { it.metrics.meter(prefix + ".success")?.mark() }

            finalBuilder.addOutputState(SuccessfulCheckState(state.linearId, state.participants, successTime), SchedulingContract.PROGRAM_ID)
        } catch (e: Exception) {
            val failTime = Instant.now()
            log.info("${state.target}: Check failed in ${Duration.between(runningState.startTime, failTime).toHumanReadable()} ${formatLastSuccess(lastSuccessTime)} Failure: ${e}")
            monitoringService.also { it.metrics.meter(prefix + ".fail")?.mark() }

            finalBuilder.addOutputState(FailedCheckState(state.linearId, state.participants), SchedulingContract.PROGRAM_ID)
        } finally {
            monitoringService.also {
                it.metrics.counter(prefix + ".inflight")?.dec()
                it.metrics.timer(prefix + ".checkTime").update(Duration.between(runningState.startTime, Instant.now()).seconds, TimeUnit.SECONDS)
            }
            val finalTx = serviceHub.signInitialTransaction(finalBuilder)
            serviceHub.recordTransactions(finalTx)
        }
    }

    @Suspendable
    private fun abandonState(
            state: ScheduledCheckState,
            stateAndRef: StateAndRef<ScheduledCheckState>,
            lastSuccessTime: Instant,
            earliestStartTime: Instant) {
        log.info("${state.target} Waiting for previous flows since $earliestStartTime ${formatLastSuccess(lastSuccessTime)}")
        val outputState = AbandonnedCheckState(state.linearId, state.participants)
        val builder = TransactionBuilder(state.target.notary)
                .addInputState(stateAndRef)
                .addOutputState(outputState, SchedulingContract.PROGRAM_ID)
                .addCommand(SchedulingContract.emptyCommand(ourIdentity.owningKey))
        val tx = serviceHub.signInitialTransaction(builder)
        serviceHub.recordTransactions(tx)
    }
}
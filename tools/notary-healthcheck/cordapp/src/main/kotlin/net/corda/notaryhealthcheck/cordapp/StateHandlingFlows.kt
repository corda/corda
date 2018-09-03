package net.corda.notaryhealthcheck.cordapp

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.contextLogger
import net.corda.notaryhealthcheck.contract.SchedulingContract
import net.corda.notaryhealthcheck.utils.Monitorable
import java.time.Instant


/**
 * Flow to start monitoring a specified notary node.
 *
 * @param target The notary (or notary member) to monitor
 * @param waitTimeSeconds Time to wait between checks
 * @param waitForOutstandingFlowsSeconds Time to wait before rechecking while checks are still in flight
 */
@StartableByRPC
class StartCheckScheduleFlow(
        private val target: Monitorable,
        private val waitTimeSeconds: Int,
        private val waitForOutstandingFlowsSeconds: Int) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(InstallCheckScheduleStateFlow(
                listOf(ourIdentity),
                target,
                emptyList(),
                Instant.now().plusSeconds(waitTimeSeconds.toLong()),
                Instant.MIN,
                waitTimeSeconds,
                waitForOutstandingFlowsSeconds,
                UniqueIdentifier(null)))
    }
}

/**
 * Flow to stop monitoring a specified notary node

 * @param target The notary (or notary member) to stop monitoring
 */
@StartableByRPC
class StopCheckScheduleFlow(private val target: Monitorable) : FlowLogic<Unit>() {
    companion object {
        val log = contextLogger()
    }

    @Suspendable
    override fun call() {
        val queryCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val states = serviceHub.vaultService.queryBy<ScheduledCheckState>(queryCriteria).states.filter { it.state.data.target.party == target.party }
        if (!states.isEmpty()) {
            val builder = TransactionBuilder(target.notary)
                    .addCommand(SchedulingContract.emptyCommand(ourIdentity.owningKey))
            states.forEach { builder.addInputState(it) }
            val tx = serviceHub.signInitialTransaction(builder)
            serviceHub.recordTransactions(tx)
            log.info("Removed scheduled state for monitoring ${target.party.name}")
        } else {
            log.warn("Failed to find scheduled state for party ${target.party.name}")
        }
    }

}

/**
 * Flow to start monitoring all notaries (and notary cluster members) that are known to the
 * network map
 *
 * @param waitTimeSeconds Time to wait between checks
 * @param waitForOutstandingFlowsSeconds Time to wait before rechecking while checks are still in flight
 */
@StartableByService
@StartableByRPC
class StartAllChecksFlow(private val waitTimeSeconds: Int, private val waitForOutstandingFlowsSeconds: Int) : FlowLogic<Unit>() {
    companion object {
        internal fun getTargets(networkMap: NetworkMapCache): List<Monitorable> {
            val notaries = networkMap.notaryIdentities
            val notaryClusterMembers = networkMap.allNodes.mapNotNull {
                val party = it.legalIdentities.first()
                if (party !in notaries) {
                    val notary = it.legalIdentities.firstOrNull { it in notaries }
                    if (notary != null) {
                        Monitorable(notary, party)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            return notaries.map { Monitorable(it, it) } + notaryClusterMembers
        }
    }

    @Suspendable
    override fun call() {
        val targets = getTargets(serviceHub.networkMapCache)
        val startTime = Instant.now().plusSeconds(waitTimeSeconds.toLong())
        targets.forEach { subFlow(InstallCheckScheduleStateFlow(listOf(ourIdentity), it, emptyList(), startTime, Instant.MIN, waitTimeSeconds, waitForOutstandingFlowsSeconds, UniqueIdentifier(null))) }
    }

}

/**
 * Flow to stop all currently scheduled notary healthchecks
 */
@StartableByService
@StartableByRPC
class StopAllChecksFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val states = serviceHub.vaultService.queryBy<ScheduledCheckState>()
        states.states.groupBy { it.state.notary }.forEach {
            val builder = TransactionBuilder(it.key)
            it.value.forEach { builder.addInputState(it) }
            builder.addCommand(SchedulingContract.emptyCommand(ourIdentity.owningKey))
            val tx = serviceHub.signInitialTransaction(builder)
            serviceHub.recordTransactions(tx)
        }
    }
}
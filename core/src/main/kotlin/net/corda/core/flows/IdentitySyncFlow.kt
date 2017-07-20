package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.AnonymousPartyAndPath
import net.corda.core.identity.Party
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

/**
 * Flow for ensuring that one or more counterparties are aware of all identities in a transaction for KYC purposes.
 * This is intended for use as a subflow of another flow.
 */
@StartableByRPC
@InitiatingFlow
class IdentitySyncFlow(val otherSides: Set<Party>,
                       val tx: WireTransaction,
                       override val progressTracker: ProgressTracker) : FlowLogic<Unit>() {
    constructor(otherSide: Party, tx: WireTransaction) : this(setOf(otherSide), tx, tracker())

    companion object {
        object EXTRACTING_CONFIDENTIAL_IDENTITIES : ProgressTracker.Step("Extracting confidential identities")
        object SYNCING_IDENTITIES : ProgressTracker.Step("Syncing identities")
        object AWAITING_ACKNOWLEDGMENT : ProgressTracker.Step("Awaiting acknowledgement")

        fun tracker() = ProgressTracker(EXTRACTING_CONFIDENTIAL_IDENTITIES, SYNCING_IDENTITIES, AWAITING_ACKNOWLEDGMENT)
    }

    @Suspendable
    override fun call() {
        progressTracker.currentStep = EXTRACTING_CONFIDENTIAL_IDENTITIES
        val states: List<ContractState> = (tx.inputs.map { serviceHub.loadState(it) }.requireNoNulls().map { it.data } + tx.outputs.map { it.data })
        val participants: List<AbstractParty> = states.flatMap { it.participants }
        val confidentialIdentities: List<AnonymousParty> = participants.filterIsInstance<AnonymousParty>()
        val identities: Map<AnonymousParty, AnonymousPartyAndPath> = confidentialIdentities
                .map { Pair(it, serviceHub.identityService.anonymousFromKey(it.owningKey)!!) }
                .toMap()

        progressTracker.currentStep = SYNCING_IDENTITIES
        otherSides.forEach { otherSide ->
            val requestedIdentities: List<AnonymousParty> = sendAndReceive<List<AnonymousParty>>(otherSide, confidentialIdentities).unwrap { req ->
                require(req.all { it in identities }) { "${otherSide} requested a confidential identity not part of transaction ${tx.id}"}
                req
            }
            val sendIdentities: List<AnonymousPartyAndPath> = requestedIdentities.map(identities::get).requireNoNulls()
            send(otherSide, sendIdentities)
        }

        progressTracker.currentStep = AWAITING_ACKNOWLEDGMENT
        otherSides.forEach { otherSide ->
            // Current unused return value from each party
            receive<Boolean>(otherSide)
        }
    }

}

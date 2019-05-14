package net.corda.confidential.identities

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

class SyncKeyMappingFlow(private val session: FlowSession, val tx: WireTransaction) : FlowLogic<Unit>() {

    companion object {
        object SYNCING_KEY_MAPPINGS : ProgressTracker.Step("Syncing key mappings")
    }

    override val progressTracker = ProgressTracker(SYNCING_KEY_MAPPINGS)

    @Suspendable
    override fun call() {
        progressTracker.currentStep = SYNCING_KEY_MAPPINGS
        val confidentialIdentities = extractConfidentialIdentities()

        // Send confidential identities to the counter party and return a list of parties they wish to resolve
        val requestedIdentities = session.sendAndReceive<List<AbstractParty>>(confidentialIdentities).unwrap { req ->
            require(req.all { it in confidentialIdentities }) {
                "${session.counterparty} requested a confidential identity not part of transaction: ${tx.id}"
            }
            req
        }

        val resolvedIds = requestedIdentities.map { serviceHub.identityService.wellKnownPartyFromAnonymous(it) }
        session.send(resolvedIds)
    }

    private fun extractConfidentialIdentities(): List<AbstractParty> {
        val inputStates: List<ContractState> = (tx.inputs.toSet()).mapNotNull {
            try {
                serviceHub.loadState(it).data
            } catch (e: TransactionResolutionException) {
                null
            }
        }
        val states: List<ContractState> = inputStates + tx.outputs.map { it.data }
        val identities: Set<AbstractParty> = states.flatMap(ContractState::participants).toSet()

        return identities
                .filter { serviceHub.networkMapCache.getNodesByLegalIdentityKey(it.owningKey).isEmpty() }
                .toList()
    }
}

class SyncKeyMappingFlowHandler(private val otherSession: FlowSession) : FlowLogic<Unit>() {
    companion object {
        object RECEIVING_IDENTITIES : ProgressTracker.Step("Receiving confidential identities")
        object RECEIVING_PARTIES : ProgressTracker.Step("Receiving potential party objects for unknown identities")
        object PARTIES_RECEIVED : ProgressTracker.Step("List of parties received")
        object REQUESTING_PROOF_OF_ID: ProgressTracker.Step("Requesting a signed key to party mapping for the received parties to verify" +
                "the authenticity of the party")
        object IDENTITIES_SYNCHRONISED : ProgressTracker.Step("Identities have finished synchronising.")
    }

    override val progressTracker: ProgressTracker = ProgressTracker(RECEIVING_IDENTITIES, RECEIVING_PARTIES, PARTIES_RECEIVED, REQUESTING_PROOF_OF_ID, IDENTITIES_SYNCHRONISED)

    @Suspendable
    override fun call() {
        progressTracker.currentStep = RECEIVING_IDENTITIES
        val allConfidentialIds = otherSession.receive<List<AbstractParty>>().unwrap { it }
        val unknownIdentities = allConfidentialIds.filter { serviceHub.identityService.wellKnownPartyFromAnonymous(it) == null }
        otherSession.send(unknownIdentities)
        progressTracker.currentStep = RECEIVING_PARTIES

        val parties = otherSession.receive<List<Party>>().unwrap { it }
        progressTracker.currentStep = PARTIES_RECEIVED

        progressTracker.currentStep = REQUESTING_PROOF_OF_ID
        parties.forEach {
            subFlow(ShareKeyFlowWrapper(it, it.owningKey))
        }
        // Request signed mapping from those parties?
        progressTracker.currentStep = IDENTITIES_SYNCHRONISED
    }
}
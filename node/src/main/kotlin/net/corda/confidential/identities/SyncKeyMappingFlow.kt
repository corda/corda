package net.corda.confidential.identities

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.SignedKeyToPartyMapping
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.toBase58String
import net.corda.core.utilities.unwrap
import java.util.*

class SyncKeyMappingFlow(private val session: FlowSession, val tx: WireTransaction) : FlowLogic<Unit>() {

    companion object {
        object SYNCING_KEY_MAPPINGS : ProgressTracker.Step("Syncing key mappings")
    }

    override val progressTracker = ProgressTracker(SYNCING_KEY_MAPPINGS)

    @Suspendable
    override fun call() {
        progressTracker.currentStep = SYNCING_KEY_MAPPINGS
        val confidentialIdentities = extractOurConfidentialIdentities()

        val requestedIdentities = session.sendAndReceive<List<AbstractParty>>(confidentialIdentities).unwrap { req ->
            require(req.all { it in confidentialIdentities }) {
                "${session.counterparty} requested a confidential identity not part of transaction: ${tx.id}"
            }
            req
        }
        val keyMappings = requestedIdentities.map {
            createSignedPublicKey(serviceHub, UniqueIdentifier().id)
        }.toList()
        session.send(keyMappings)
    }

    private fun extractOurConfidentialIdentities(): List<AbstractParty> {
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
        object RECEIVING_MAPPINGS : ProgressTracker.Step("Receiving signed key mappings for unknown identities")
        object MAPPINGS_RECEIVED : ProgressTracker.Step("Signed key mappings for unknown identities received")
        object MAPPINGS_REGISTERED : ProgressTracker.Step("Signed key mappings for unknown identities registered on other session")
    }

    override val progressTracker: ProgressTracker = ProgressTracker(RECEIVING_IDENTITIES, RECEIVING_MAPPINGS, MAPPINGS_RECEIVED, MAPPINGS_REGISTERED)

    @Suspendable
    override fun call() {
        progressTracker.currentStep = RECEIVING_IDENTITIES
        val allIdentities = otherSession.receive<List<AbstractParty>>().unwrap { it }
        val unknownIdentities = allIdentities.filter { serviceHub.identityService.wellKnownPartyFromAnonymous(it) == null }
        progressTracker.currentStep = RECEIVING_MAPPINGS
        val keyMappings = otherSession.sendAndReceive<List<SignedKeyToPartyMapping>>(unknownIdentities).unwrap{ it }
        progressTracker.currentStep = MAPPINGS_RECEIVED
        //Register the key mapping on our node
        keyMappings.forEach { mapping ->
            serviceHub.identityService.registerPublicKeyToPartyMapping(mapping)
        }
        progressTracker.currentStep = MAPPINGS_REGISTERED
    }
}
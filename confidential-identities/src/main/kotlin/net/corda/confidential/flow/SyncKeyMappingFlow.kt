package net.corda.confidential.flow

import net.corda.confidential.service.SignedPublicKey
import net.corda.confidential.service.createSignedPublicKey
import net.corda.confidential.service.registerIdentityMapping
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
import net.corda.node.services.identity.PersistentIdentityService

class SyncKeyMappingFlow(private val session: FlowSession, val tx: WireTransaction) : FlowLogic<Unit>() {

    companion object {
        object SYNCING_KEY_MAPPINGS : ProgressTracker.Step("Syncing key mappings")
    }

    override val progressTracker = ProgressTracker(SYNCING_KEY_MAPPINGS)

    override fun call() {
        val inputStates: List<ContractState> = (tx.inputs.toSet()).mapNotNull {
            try {
                serviceHub.loadState(it).data
            } catch (e: TransactionResolutionException) {
                null
            }
        }
        val states: List<ContractState> = inputStates + tx.outputs.map { it.data }
        val identities: Set<AbstractParty> = states.flatMap(ContractState::participants).toSet()
        val confidentialIdentities = identities
                .filter { serviceHub.networkMapCache.getNodesByLegalIdentityKey(it.owningKey).isEmpty() }
                .toList()

        val requestedIdentities = session.sendAndReceive<List<AbstractParty>>(confidentialIdentities).unwrap { req ->
            require(req.all { it in confidentialIdentities }) {
                "${session.counterparty} requested a confidential identity not part of transaction: ${tx.id}"
            }
            req
        }
        val resolvedParties = requestedIdentities.map {
            (serviceHub.identityService as PersistentIdentityService).wellKnownPartyFromAnonymous(it)
        }
        // TODO erm
        val keyMappings = resolvedParties.map {
            //TODO erm
            createSignedPublicKey(serviceHub, UniqueIdentifier().id) to it!!
        }.toMap()
        session.send(keyMappings)
    }
}

class SyncKeyMappingFlowHandler(private val otherSession: FlowSession) : FlowLogic<Unit>() {
    companion object {
        object RECEIVING_IDENTITIES : ProgressTracker.Step("Receiving confidential identities")
        object RECEIVING_MAPPINGS : ProgressTracker.Step("Receiving signed key mappings for unknown identities")
    }

    override val progressTracker: ProgressTracker = ProgressTracker(RECEIVING_IDENTITIES, RECEIVING_MAPPINGS)

    override fun call() {
        progressTracker.currentStep = RECEIVING_IDENTITIES
        val allIdentities = otherSession.receive<List<AbstractParty>>().unwrap { it }
        val unknownIdentities = allIdentities.filter { (serviceHub.identityService as PersistentIdentityService).wellKnownPartyFromAnonymous(it) == null }
        progressTracker.currentStep = RECEIVING_MAPPINGS
        val missingIdentities = otherSession.sendAndReceive<Map<SignedPublicKey, Party>>(unknownIdentities)
        // Batch verify the identities we've received, so we know they're all correct before we start storing them in
        // the identity service
        val keyMappings = missingIdentities.unwrap { it }
        // TODO erm
        keyMappings.forEach {entry ->
            registerIdentityMapping(serviceHub, entry.key, entry.value)
        }
    }
}
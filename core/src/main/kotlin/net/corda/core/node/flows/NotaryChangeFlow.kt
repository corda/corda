package net.corda.core.node.flows

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party

/**
 * A flow to be used for changing a state's Notary. This is required since all input states to a transaction
 * must point to the same notary.
 *
 * This assembles the transaction for notary replacement and sends out change proposals to all participants
 * of that state. If participants agree to the proposed change, they each sign the transaction.
 * Finally, the transaction containing all signatures is sent back to each participant so they can record it and
 * use the new updated state for future transactions.
 */
@InitiatingFlow
class NotaryChangeFlow<out T : net.corda.core.contracts.ContractState>(
        originalState: net.corda.core.contracts.StateAndRef<T>,
        newNotary: net.corda.core.identity.Party,
        progressTracker: net.corda.core.utilities.ProgressTracker = AbstractStateReplacementFlow.Instigator.Companion.tracker())
    : AbstractStateReplacementFlow.Instigator<T, T, Party>(originalState, newNotary, progressTracker) {

    override fun assembleTx(): AbstractStateReplacementFlow.UpgradeTx {
        val inputs = resolveEncumbrances(originalState)

        val tx = net.corda.core.transactions.NotaryChangeWireTransaction(
                inputs.map { it.ref },
                originalState.state.notary,
                modification
        )

        val participantKeys = inputs.flatMap { it.state.data.participants }.map { it.owningKey }.toSet()
        // TODO: We need a much faster way of finding our key in the transaction
        val myKey = serviceHub.keyManagementService.filterMyKeys(participantKeys).single()
        val signableData = net.corda.core.crypto.SignableData(tx.id, SignatureMetadata(serviceHub.myInfo.platformVersion, Crypto.findSignatureScheme(myKey).schemeNumberID))
        val mySignature = serviceHub.keyManagementService.sign(signableData, myKey)
        val stx = net.corda.core.transactions.SignedTransaction(tx, listOf(mySignature))

        return AbstractStateReplacementFlow.UpgradeTx(stx, participantKeys, myKey)
    }

    /** Resolves the encumbrance state chain for the given [state] */
    private fun resolveEncumbrances(state: net.corda.core.contracts.StateAndRef<T>): List<net.corda.core.contracts.StateAndRef<T>> {
        val states = mutableListOf(state)
        while (states.last().state.encumbrance != null) {
            val encumbranceStateRef = net.corda.core.contracts.StateRef(states.last().ref.txhash, states.last().state.encumbrance!!)
            val encumbranceState = serviceHub.toStateAndRef<T>(encumbranceStateRef)
            states.add(encumbranceState)
        }
        return states
    }
}

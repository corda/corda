package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.*
import net.corda.core.flows.FlowLogic
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.security.PublicKey

/**
 * The [CollectSignaturesFlow] is used to automate the collection of signatures from the counter-parties to
 * a transaction.
 *
 * Usage:
 * - Subclass [AbstractCollectSignaturesFlowResponder] in your CorDapp.
 * - You are required to override the [AbstractCollectSignaturesFlowResponder.checkTransaction] method to add
 *   custom logic for verifying the transaction in question.
 * - When calling the flow as a [subFlow], pass [CollectSignaturesFlow] a [SignedTransaction] which has the Initiator's
 *   signature. The flow will fail if the Initiator's signature is not present or if there are too many signatures.
 * - The flow will return a [SignedTransaction] with all the counter-party signatures (but not the notary's)
 * - Call the [FinalityFlow] with the return value of this flow.
 */

class CollectSignaturesFlow(val partiallySignedTx: SignedTransaction,
                override val progressTracker: ProgressTracker = tracker()): FlowLogic<SignedTransaction>() {

    companion object {
        object COLLECTING : ProgressTracker.Step("Collecting signatures from counter-parties.")
        object VERIFYING : ProgressTracker.Step("Verifying collected signatures.")

        fun tracker() = ProgressTracker(COLLECTING, VERIFYING)
    }

    @Suspendable override fun call(): SignedTransaction {
        check(partiallySignedTx.sigs.size == 1) {
            "There should be only one signature present when calling CollectSignaturesFlow."
        }

        // TODO: Revisit when key management is properly fleshed out.
        // This will break if a party uses anything other than their legalIdentityKey.
        val myKey = serviceHub.myInfo.legalIdentity.owningKey
        val allButMe = partiallySignedTx.tx.mustSign - myKey
        val notaryKey = partiallySignedTx.tx.notary?.owningKey

        // If the counter-parties list is empty then we don't need to collect any signatures.
        check(allButMe.isNotEmpty()) { return partiallySignedTx }

        // Check the Initiator has already signed and that the transaction is valid.
        partiallySignedTx.verifySignatures(*allButMe.toTypedArray())
        partiallySignedTx.tx.toLedgerTransaction(serviceHub).verify()

        // Collect signatures from all counter-parties.
        progressTracker.currentStep = COLLECTING
        val counterpartySignatures = collectSignatures(allButMe)
        val stx = partiallySignedTx + counterpartySignatures

        // Verify all but the notary's signature if the transaction requires a notary, otherwise verify all signatures.
        progressTracker.currentStep = VERIFYING
        if (notaryKey != null) stx.verifySignatures(notaryKey) else stx.verifySignatures()

        return stx
    }

    /**
     * Lookup the [Party] object for each [PublicKey] using the [serviceHub.networkMapCache].
     */
    @Suspendable private fun collectSignatures(keys: List<PublicKey>): List<DigitalSignature.WithKey> {
        // TODO: Revisit when IdentityService supports resolution of a (possibly random) public key to a legal identity key.
        val parties =  keys.map {
            val partyNode = serviceHub.networkMapCache.getNodeByLegalIdentityKey(it)
                    ?: throw IllegalStateException("Party $it not found on the network.")
            partyNode.legalIdentity
        }
        return parties.map { collectSignature(it) }
    }

    /**
     * Get and check the required signature.
     */
    @Suspendable private fun collectSignature(counterparty: Party): DigitalSignature.WithKey {
        return sendAndReceive<DigitalSignature.WithKey>(counterparty, partiallySignedTx).unwrap { it ->
            check(counterparty.owningKey.isFulfilledBy(it.by)) { "Not signed by the required Party." }
            it.verifyWithECDSA(partiallySignedTx.tx.id)
            it
        }
    }
}

abstract class AbstractCollectSignaturesFlowResponder(val otherParty: Party,
                                                      override val progressTracker: ProgressTracker = tracker()) : FlowLogic<Unit>() {

    companion object {
        object RECEIVING : ProgressTracker.Step("Receiving transaction proposal for signing.")
        object VERIFYING : ProgressTracker.Step("Verifying transaction proposal.")
        object SIGNING : ProgressTracker.Step("Signing transaction proposal.")

        fun tracker() = ProgressTracker(RECEIVING, VERIFYING, SIGNING)
    }

    @Suspendable override fun call(): Unit {
        progressTracker.currentStep = RECEIVING
        val checkedProposal = receive<SignedTransaction>(otherParty).unwrap { proposal ->
            progressTracker.currentStep = VERIFYING
            // Check that the Responder actually needs to sign.
            checkMySignatureRequired(proposal)
            // Resolve dependencies and verify, pass in the WireTransaction as we don't have all signatures.
            subFlow(ResolveTransactionsFlow(proposal.tx, otherParty))
            proposal.tx.toLedgerTransaction(serviceHub).verify()
            // Perform some custom verification over the transaction.
            checkTransaction(proposal)
            // Check the Initiators signature. There should only be one signature in the list.
            proposal.sigs.single().verifyWithECDSA(proposal.id)
            // All good. Unwrap the proposal.
            proposal
        }

        // Sign and send back our signature to the Initiator.
        progressTracker.currentStep = SIGNING
        val mySignature = serviceHub.legalIdentityKey.signWithECDSA(checkedProposal.id)
        send(otherParty, mySignature)
    }

    @Throws(StateReplacementException::class)
    @Suspendable abstract protected fun checkTransaction(stx: SignedTransaction)

    @Suspendable private fun checkMySignatureRequired(stx: SignedTransaction) {
        // TODO: Revisit when key management is properly fleshed out.
        val myKey = serviceHub.myInfo.legalIdentity.owningKey
        require(myKey in stx.tx.mustSign) {
            "Party is not a participant for any of the input states of transaction ${stx.id}"
        }
    }
}

package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.Party
import net.corda.core.crypto.signWithECDSA
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.node.recordTransactions
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.unwrap
import net.corda.flows.AbstractStateReplacementFlow.Acceptor
import net.corda.flows.AbstractStateReplacementFlow.Instigator

/**
 * Abstract flow to be used for replacing one state with another, for example when changing the notary of a state.
 * Notably this requires a one to one replacement of states, states cannot be split, merged or issued as part of these
 * flows.
 *
 * The [Instigator] assembles the transaction for state replacement and sends out change proposals to all participants
 * ([Acceptor]) of that state. If participants agree to the proposed change, they each sign the transaction.
 * Finally, [Instigator] sends the transaction containing all signatures back to each participant so they can record it and
 * use the new updated state for future transactions.
 */
abstract class AbstractStateReplacementFlow {
    data class Proposal<out T>(val stateRef: StateRef, val modification: T, val stx: SignedTransaction)

    abstract class Instigator<out S : ContractState, out T>(
            val originalState: StateAndRef<S>,
            val modification: T,
            override val progressTracker: ProgressTracker = tracker()) : FlowLogic<StateAndRef<S>>() {
        companion object {
            object SIGNING : ProgressTracker.Step("Requesting signatures from other parties")
            object NOTARY : ProgressTracker.Step("Requesting notary signature")

            fun tracker() = ProgressTracker(SIGNING, NOTARY)
        }

        @Suspendable
        @Throws(StateReplacementException::class)
        override fun call(): StateAndRef<S> {
            val (stx, participants) = assembleTx()

            progressTracker.currentStep = SIGNING

            val myKey = serviceHub.myInfo.legalIdentity.owningKey
            val me = listOf(myKey)

            val signatures = if (participants == me) {
                listOf(getNotarySignature(stx))
            } else {
                collectSignatures(participants - me, stx)
            }

            val finalTx = stx + signatures
            serviceHub.recordTransactions(finalTx)
            return finalTx.tx.outRef(0)
        }

        abstract protected fun assembleTx(): Pair<SignedTransaction, Iterable<CompositeKey>>

        @Suspendable
        private fun collectSignatures(participants: Iterable<CompositeKey>, stx: SignedTransaction): List<DigitalSignature.WithKey> {
            val parties = participants.map {
                val participantNode = serviceHub.networkMapCache.getNodeByLegalIdentityKey(it) ?:
                        throw IllegalStateException("Participant $it to state $originalState not found on the network")
                participantNode.legalIdentity
            }

            val participantSignatures = parties.map { getParticipantSignature(it, stx) }

            val allPartySignedTx = stx + participantSignatures

            val allSignatures = participantSignatures + getNotarySignature(allPartySignedTx)
            parties.forEach { send(it, allSignatures) }

            return allSignatures
        }

        @Suspendable
        private fun getParticipantSignature(party: Party, stx: SignedTransaction): DigitalSignature.WithKey {
            val proposal = Proposal(originalState.ref, modification, stx)
            val response = sendAndReceive<DigitalSignature.WithKey>(party, proposal)
            return response.unwrap {
                check(party.owningKey.isFulfilledBy(it.by)) { "Not signed by the required participant" }
                it.verifyWithECDSA(stx.id)
                it
            }
        }

        @Suspendable
        private fun getNotarySignature(stx: SignedTransaction): DigitalSignature.WithKey {
            progressTracker.currentStep = NOTARY
            try {
                return subFlow(NotaryFlow.Client(stx))
            } catch (e: NotaryException) {
                throw StateReplacementException("Unable to notarise state change", e)
            }
        }
    }

    abstract class Acceptor<in T>(val otherSide: Party,
                                  override val progressTracker: ProgressTracker = tracker()) : FlowLogic<Unit>() {
        companion object {
            object VERIFYING : ProgressTracker.Step("Verifying state replacement proposal")
            object APPROVING : ProgressTracker.Step("State replacement approved")

            fun tracker() = ProgressTracker(VERIFYING, APPROVING)
        }

        @Suspendable
        @Throws(StateReplacementException::class)
        override fun call() {
            progressTracker.currentStep = VERIFYING
            val maybeProposal: UntrustworthyData<Proposal<T>> = receive(otherSide)
            val stx: SignedTransaction = maybeProposal.unwrap {
                verifyProposal(it)
                verifyTx(it.stx)
                it.stx
            }
            approve(stx)
        }

        @Suspendable
        private fun verifyTx(stx: SignedTransaction) {
            checkMySignatureRequired(stx.tx)
            checkDependenciesValid(stx)
            // We expect stx to have insufficient signatures, so we convert the WireTransaction to the LedgerTransaction
            // here, thus bypassing the sufficient-signatures check.
            stx.tx.toLedgerTransaction(serviceHub).verify()
        }

        @Suspendable
        private fun approve(stx: SignedTransaction) {
            progressTracker.currentStep = APPROVING

            val mySignature = sign(stx)
            val swapSignatures = sendAndReceive<List<DigitalSignature.WithKey>>(otherSide, mySignature)

            // TODO: This step should not be necessary, as signatures are re-checked in verifySignatures.
            val allSignatures = swapSignatures.unwrap { signatures ->
                signatures.forEach { it.verifyWithECDSA(stx.id) }
                signatures
            }

            val finalTx = stx + allSignatures
            finalTx.verifySignatures()
            serviceHub.recordTransactions(finalTx)
        }

        /**
         * Check the state change proposal to confirm that it's acceptable to this node. Rules for verification depend
         * on the change proposed, and may further depend on the node itself (for example configuration). The
         * proposal is returned if acceptable, otherwise a [StateReplacementException] is thrown.
         */
        @Throws(StateReplacementException::class)
        abstract protected fun verifyProposal(proposal: Proposal<T>)

        private fun checkMySignatureRequired(tx: WireTransaction) {
            // TODO: use keys from the keyManagementService instead
            val myKey = serviceHub.myInfo.legalIdentity.owningKey
            require(myKey in tx.mustSign) { "Party is not a participant for any of the input states of transaction ${tx.id}" }
        }

        @Suspendable
        private fun checkDependenciesValid(stx: SignedTransaction) {
            subFlow(ResolveTransactionsFlow(stx.tx, otherSide))
        }

        private fun sign(stx: SignedTransaction): DigitalSignature.WithKey {
            val myKey = serviceHub.legalIdentityKey
            return myKey.signWithECDSA(stx.id)
        }
    }
}

open class StateReplacementException @JvmOverloads constructor(message: String? = null, cause: Throwable? = null)
    : FlowException(message, cause)

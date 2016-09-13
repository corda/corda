package com.r3corda.protocols

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.contracts.ContractState
import com.r3corda.core.contracts.StateAndRef
import com.r3corda.core.contracts.StateRef
import com.r3corda.core.crypto.DigitalSignature
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.signWithECDSA
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.random63BitValue
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.core.transactions.WireTransaction
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.core.utilities.UntrustworthyData
import com.r3corda.protocols.AbstractStateReplacementProtocol.Acceptor
import com.r3corda.protocols.AbstractStateReplacementProtocol.Instigator
import java.security.PublicKey

/**
 * Abstract protocol to be used for replacing one state with another, for example when changing the notary of a state.
 * Notably this requires a one to one replacement of states, states cannot be split, merged or issued as part of these
 * protocols.
 *
 * The [Instigator] assembles the transaction for state replacement and sends out change proposals to all participants
 * ([Acceptor]) of that state. If participants agree to the proposed change, they each sign the transaction.
 * Finally, [Instigator] sends the transaction containing all signatures back to each participant so they can record it and
 * use the new updated state for future transactions.
 */
abstract class AbstractStateReplacementProtocol<T> {
    interface Proposal<out T> {
        val stateRef: StateRef
        val modification: T
        val stx: SignedTransaction
    }

    data class Handshake(override val replyToParty: Party,
                         override val sendSessionID: Long = random63BitValue(),
                         override val receiveSessionID: Long = random63BitValue()) : HandshakeMessage

    abstract class Instigator<out S : ContractState, T>(val originalState: StateAndRef<S>,
                                                        val modification: T,
                                                        override val progressTracker: ProgressTracker = tracker()) : ProtocolLogic<StateAndRef<S>>() {
        companion object {

            object SIGNING : ProgressTracker.Step("Requesting signatures from other parties")

            object NOTARY : ProgressTracker.Step("Requesting notary signature")

            fun tracker() = ProgressTracker(SIGNING, NOTARY)
        }

        @Suspendable
        override fun call(): StateAndRef<S> {
            val (stx, participants) = assembleTx()

            progressTracker.currentStep = SIGNING

            val myKey = serviceHub.storageService.myLegalIdentity.owningKey
            val me = listOf(myKey)

            val signatures = if (participants == me) {
                listOf(getNotarySignature(stx))
            } else {
                collectSignatures(participants - me, stx)
            }

            val finalTx = stx + signatures
            serviceHub.recordTransactions(listOf(finalTx))
            return finalTx.tx.outRef(0)
        }

        abstract internal fun assembleProposal(stateRef: StateRef, modification: T, stx: SignedTransaction): Proposal<T>
        abstract internal fun assembleTx(): Pair<SignedTransaction, List<PublicKey>>

        @Suspendable
        private fun collectSignatures(participants: List<PublicKey>, stx: SignedTransaction): List<DigitalSignature.WithKey> {
            val parties = participants.map {
                val participantNode = serviceHub.networkMapCache.getNodeByPublicKey(it) ?:
                        throw IllegalStateException("Participant $it to state $originalState not found on the network")
                participantNode.identity
            }

            val participantSignatures = parties.map { getParticipantSignature(it, stx) }

            val allSignatures = participantSignatures + getNotarySignature(stx)
            parties.forEach { send(it, allSignatures) }

            return allSignatures
        }

        @Suspendable
        private fun getParticipantSignature(party: Party, stx: SignedTransaction): DigitalSignature.WithKey {
            val proposal = assembleProposal(originalState.ref, modification, stx)

            send(party, Handshake(serviceHub.storageService.myLegalIdentity))

            val response = sendAndReceive<Result>(party, proposal)
            val participantSignature = response.unwrap {
                if (it.sig == null) throw StateReplacementException(it.error!!)
                else {
                    check(it.sig.by == party.owningKey) { "Not signed by the required participant" }
                    it.sig.verifyWithECDSA(stx.txBits)
                    it.sig
                }
            }

            return participantSignature
        }

        @Suspendable
        private fun getNotarySignature(stx: SignedTransaction): DigitalSignature.LegallyIdentifiable {
            progressTracker.currentStep = NOTARY
            return subProtocol(NotaryProtocol.Client(stx))
        }
    }

    abstract class Acceptor<T>(val otherSide: Party,
                               override val progressTracker: ProgressTracker = tracker()) : ProtocolLogic<Unit>() {

        companion object {
            object VERIFYING : ProgressTracker.Step("Verifying state replacement proposal")

            object APPROVING : ProgressTracker.Step("State replacement approved")

            object REJECTING : ProgressTracker.Step("State replacement rejected")

            fun tracker() = ProgressTracker(VERIFYING, APPROVING, REJECTING)
        }

        @Suspendable
        override fun call() {
            progressTracker.currentStep = VERIFYING
            val maybeProposal: UntrustworthyData<Proposal<T>> = receive(otherSide)
            try {
                val stx: SignedTransaction = maybeProposal.unwrap { verifyProposal(maybeProposal).stx }
                verifyTx(stx)
                approve(stx)
            } catch(e: Exception) {
                // TODO: catch only specific exceptions. However, there are numerous validation exceptions
                //       that might occur (tx validation/resolution, invalid proposal). Need to rethink how
                //       we manage exceptions and maybe introduce some platform exception hierarchy
                val myIdentity = serviceHub.storageService.myLegalIdentity
                val state = maybeProposal.unwrap { it.stateRef }
                val reason = StateReplacementRefused(myIdentity, state, e.message)

                reject(reason)
            }
        }

        @Suspendable
        private fun approve(stx: SignedTransaction) {
            progressTracker.currentStep = APPROVING

            val mySignature = sign(stx)
            val response = Result.noError(mySignature)
            val swapSignatures = sendAndReceive<List<DigitalSignature.WithKey>>(otherSide, response)

            // TODO: This step should not be necessary, as signatures are re-checked in verifySignatures.
            val allSignatures = swapSignatures.unwrap { signatures ->
                signatures.forEach { it.verifyWithECDSA(stx.txBits) }
                signatures
            }

            val finalTx = stx + allSignatures
            finalTx.verifySignatures()
            serviceHub.recordTransactions(listOf(finalTx))
        }

        @Suspendable
        private fun reject(e: StateReplacementRefused) {
            progressTracker.currentStep = REJECTING
            val response = Result.withError(e)
            send(otherSide, response)
        }

        /**
         * Check the state change proposal to confirm that it's acceptable to this node. Rules for verification depend
         * on the change proposed, and may further depend on the node itself (for example configuration). The
         * proposal is returned if acceptable, otherwise an exception is thrown.
         */
        abstract fun verifyProposal(maybeProposal: UntrustworthyData<Proposal<T>>): Proposal<T>

        @Suspendable
        private fun verifyTx(stx: SignedTransaction) {
            checkMySignatureRequired(stx.tx)
            checkDependenciesValid(stx)
            // We expect stx to have insufficient signatures, so we convert the WireTransaction to the LedgerTransaction
            // here, thus bypassing the sufficient-signatures check.
            stx.tx.toLedgerTransaction(serviceHub).verify()
        }

        private fun checkMySignatureRequired(tx: WireTransaction) {
            // TODO: use keys from the keyManagementService instead
            val myKey = serviceHub.storageService.myLegalIdentity.owningKey
            require(myKey in tx.mustSign) { "Party is not a participant for any of the input states of transaction ${tx.id}" }
        }

        @Suspendable
        private fun checkDependenciesValid(stx: SignedTransaction) {
            subProtocol(ResolveTransactionsProtocol(stx.tx, otherSide))
        }

        private fun sign(stx: SignedTransaction) = serviceHub.storageService.myLegalIdentityKey.signWithECDSA(stx.txBits)
    }

    // TODO: similar classes occur in other places (NotaryProtocol), need to consolidate
    data class Result private constructor(val sig: DigitalSignature.WithKey?, val error: StateReplacementRefused?) {
        companion object {
            fun withError(error: StateReplacementRefused) = Result(null, error)
            fun noError(sig: DigitalSignature.WithKey) = Result(sig, null)
        }
    }
}


/** Thrown when a participant refuses the proposed state replacement */
class StateReplacementRefused(val identity: Party, val state: StateRef, val detail: String?) {
    override fun toString()  = "A participant $identity refused to change state $state: " + (detail ?: "no reason provided")
}

class StateReplacementException(val error: StateReplacementRefused)  : Exception("State change failed - $error")
package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.excludeHostNode
import net.corda.core.identity.groupAbstractPartyByWellKnownParty
import net.corda.annotations.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.unwrap

/**
 * Abstract flow to be used for replacing one state with another, for example when changing the notary of a state.
 * Notably this requires a one to one replacement of states, states cannot be split, merged or issued as part of these
 * flows.
 */
abstract class AbstractStateReplacementFlow {
    /**
     * The [Proposal] contains the details of proposed state modification.
     * This is the message sent by the [Instigator] to all participants([Acceptor]) during the state replacement process.
     *
     * @param M the type of a class representing proposed modification by the instigator.
     */
    @CordaSerializable
    data class Proposal<out M>(val stateRef: StateRef, val modification: M)

    /**
     * The assembled transaction for upgrading a contract.
     *
     * @param stx signed transaction to do the upgrade.
     */
    data class UpgradeTx(val stx: SignedTransaction)

    /**
     * The [Instigator] assembles the transaction for state replacement and sends out change proposals to all participants
     * ([Acceptor]) of that state. If participants agree to the proposed change, they each sign the transaction.
     * Finally, [Instigator] sends the transaction containing all participants' signatures to the notary for signature, and
     * then back to each participant so they can record it and use the new updated state for future transactions.
     *
     * @param S the input contract state type
     * @param T the output contract state type, this can be different from [S]. For example, in contract upgrade, the output state type can be different from the input state type after the upgrade process.
     * @param M the type of a class representing proposed modification by the instigator.
     */
    abstract class Instigator<out S : ContractState, out T : ContractState, out M>(
            val originalState: StateAndRef<S>,
            val modification: M,
            override val progressTracker: ProgressTracker = Instigator.tracker()) : FlowLogic<StateAndRef<T>>() {
        companion object {
            object SIGNING : ProgressTracker.Step("Requesting signatures from other parties")
            object NOTARY : ProgressTracker.Step("Requesting notary signature")

            fun tracker() = ProgressTracker(SIGNING, NOTARY)
        }

        @Suspendable
        @Throws(StateReplacementException::class)
        override fun call(): StateAndRef<T> {
            val (stx) = assembleTx()
            val participantSessions = getParticipantSessions()
            progressTracker.currentStep = SIGNING

            val signatures = collectSignatures(participantSessions, stx)

            val finalTx = stx + signatures
            serviceHub.recordTransactions(finalTx)

            return stx.resolveBaseTransaction(serviceHub).outRef<T>(0)
        }

        /**
         * Build the upgrade transaction.
         *
         * @return the transaction
         */
        abstract protected fun assembleTx(): UpgradeTx

        /**
         * Initiate sessions with parties we want signatures from.
         */
        open fun getParticipantSessions(): List<Pair<FlowSession, List<AbstractParty>>> {
            return excludeHostNode(serviceHub, groupAbstractPartyByWellKnownParty(serviceHub, originalState.state.data.participants)).map { initiateFlow(it.key) to it.value }
        }

        @Suspendable
        private fun collectSignatures(sessions: List<Pair<FlowSession, List<AbstractParty>>>, stx: SignedTransaction): List<TransactionSignature> {
            val participantSignatures = sessions.map { getParticipantSignature(it.first, it.second, stx) }

            val allPartySignedTx = stx + participantSignatures

            val allSignatures = participantSignatures + getNotarySignatures(allPartySignedTx)
            sessions.forEach { it.first.send(allSignatures) }

            return allSignatures
        }

        @Suspendable
        private fun getParticipantSignature(session: FlowSession, party: List<AbstractParty>, stx: SignedTransaction): TransactionSignature {
            require(party.size == 1) {
                "We do not currently support multiple signatures from the same party ${session.counterparty}: $party"
            }
            val proposal = Proposal(originalState.ref, modification)
            subFlow(SendTransactionFlow(session, stx))
            return session.sendAndReceive<TransactionSignature>(proposal).unwrap {
                check(party.single().owningKey.isFulfilledBy(it.by)) { "Not signed by the required participant" }
                it.verify(stx.id)
                it
            }
        }

        @Suspendable
        private fun getNotarySignatures(stx: SignedTransaction): List<TransactionSignature> {
            progressTracker.currentStep = NOTARY
            try {
                return subFlow(NotaryFlow.Client(stx))
            } catch (e: NotaryException) {
                throw StateReplacementException("Unable to notarise state change", e)
            }
        }
    }

    // Type parameter should ideally be Unit but that prevents Java code from subclassing it (https://youtrack.jetbrains.com/issue/KT-15964).
    // We use Void? instead of Unit? as that's what you'd use in Java.
    abstract class Acceptor<in T>(val initiatingSession: FlowSession,
                                  override val progressTracker: ProgressTracker = Acceptor.tracker()) : FlowLogic<Void?>() {
        constructor(initiatingSession: FlowSession) : this(initiatingSession, Acceptor.tracker())

        companion object {
            object VERIFYING : ProgressTracker.Step("Verifying state replacement proposal")
            object APPROVING : ProgressTracker.Step("State replacement approved")

            fun tracker() = ProgressTracker(VERIFYING, APPROVING)
        }

        @Suspendable
        @Throws(StateReplacementException::class)
        override fun call(): Void? {
            progressTracker.currentStep = VERIFYING
            // We expect stx to have insufficient signatures here
            val stx = subFlow(ReceiveTransactionFlow(initiatingSession, checkSufficientSignatures = false))
            checkMySignatureRequired(stx)
            val maybeProposal: UntrustworthyData<Proposal<T>> = initiatingSession.receive()
            maybeProposal.unwrap {
                verifyProposal(stx, it)
            }
            approve(stx)
            return null
        }

        @Suspendable
        private fun approve(stx: SignedTransaction) {
            progressTracker.currentStep = APPROVING

            val mySignature = sign(stx)
            val swapSignatures = initiatingSession.sendAndReceive<List<TransactionSignature>>(mySignature)

            // TODO: This step should not be necessary, as signatures are re-checked in verifyRequiredSignatures.
            val allSignatures = swapSignatures.unwrap { signatures ->
                signatures.forEach { it.verify(stx.id) }
                signatures
            }

            val finalTx = stx + allSignatures
            finalTx.resolveTransactionWithSignatures(serviceHub).verifyRequiredSignatures()
            serviceHub.recordTransactions(finalTx)
        }

        /**
         * Check the state change proposal and the signed transaction to confirm that it's acceptable to this node.
         * Rules for verification depend on the change proposed, and may further depend on the node itself (for example configuration).
         * The proposal is returned if acceptable, otherwise a [StateReplacementException] is thrown.
         */
        @Throws(StateReplacementException::class)
        abstract protected fun verifyProposal(stx: SignedTransaction, proposal: Proposal<T>)

        private fun checkMySignatureRequired(stx: SignedTransaction) {
            // TODO: use keys from the keyManagementService instead
            // TODO Check the set of multiple identities?
            val myKey = ourIdentity.owningKey

            val requiredKeys = stx.resolveTransactionWithSignatures(serviceHub).requiredSigningKeys

            require(myKey in requiredKeys) { "Party is not a participant for any of the input states of transaction ${stx.id}" }
        }

        private fun sign(stx: SignedTransaction): TransactionSignature {
            return serviceHub.createSignature(stx)
        }
    }
}

open class StateReplacementException @JvmOverloads constructor(message: String? = null, cause: Throwable? = null)
    : FlowException(message, cause)

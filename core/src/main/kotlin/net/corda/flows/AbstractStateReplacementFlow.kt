package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.unwrap
import java.security.PublicKey

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
    data class Proposal<out M>(val stateRef: StateRef, val modification: M, val stx: SignedTransaction)

    /**
     * The assembled transaction for upgrading a contract.
     *
     * @param stx signed transaction to do the upgrade.
     * @param participants the parties involved in the upgrade transaction.
     * @param myKey key
     */
    data class UpgradeTx(val stx: SignedTransaction, val participants: Iterable<PublicKey>, val myKey: PublicKey)

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
            override val progressTracker: ProgressTracker = tracker()) : FlowLogic<StateAndRef<T>>() {
        companion object {
            object SIGNING : ProgressTracker.Step("Requesting signatures from other parties")
            object NOTARY : ProgressTracker.Step("Requesting notary signature")

            fun tracker() = ProgressTracker(SIGNING, NOTARY)
        }

        @Suspendable
        @Throws(StateReplacementException::class)
        override fun call(): StateAndRef<T> {
            val (stx, participantKeys, myKey) = assembleTx()

            progressTracker.currentStep = SIGNING

            val signatures = if (participantKeys.singleOrNull() == myKey) {
                getNotarySignatures(stx)
            } else {
                collectSignatures(participantKeys - myKey, stx)
            }

            val finalTx = stx + signatures
            serviceHub.recordTransactions(finalTx)
            return finalTx.tx.outRef(0)
        }

        /**
         * Build the upgrade transaction.
         *
         * @return a triple of the transaction, the public keys of all participants, and the participating public key of
         * this node.
         */
        abstract protected fun assembleTx(): UpgradeTx

        @Suspendable
        private fun collectSignatures(participants: Iterable<PublicKey>, stx: SignedTransaction): List<DigitalSignature.WithKey> {
            val parties = participants.map {
                val participantNode = serviceHub.networkMapCache.getNodeByLegalIdentityKey(it) ?:
                        throw IllegalStateException("Participant $it to state $originalState not found on the network")
                participantNode.legalIdentity
            }

            val participantSignatures = parties.map { getParticipantSignature(it, stx) }

            val allPartySignedTx = stx + participantSignatures

            val allSignatures = participantSignatures + getNotarySignatures(allPartySignedTx)
            parties.forEach { send(it, allSignatures) }

            return allSignatures
        }

        @Suspendable
        private fun getParticipantSignature(party: Party, stx: SignedTransaction): DigitalSignature.WithKey {
            val proposal = Proposal(originalState.ref, modification, stx)
            val response = sendAndReceive<DigitalSignature.WithKey>(party, proposal)
            return response.unwrap {
                check(party.owningKey.isFulfilledBy(it.by)) { "Not signed by the required participant" }
                it.verify(stx.id)
                it
            }
        }

        @Suspendable
        private fun getNotarySignatures(stx: SignedTransaction): List<DigitalSignature.WithKey> {
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
    abstract class Acceptor<in T>(val otherSide: Party,
                                  override val progressTracker: ProgressTracker = tracker()) : FlowLogic<Void?>() {
        companion object {
            object VERIFYING : ProgressTracker.Step("Verifying state replacement proposal")
            object APPROVING : ProgressTracker.Step("State replacement approved")

            fun tracker() = ProgressTracker(VERIFYING, APPROVING)
        }

        @Suspendable
        @Throws(StateReplacementException::class)
        override fun call(): Void? {
            progressTracker.currentStep = VERIFYING
            val maybeProposal: UntrustworthyData<Proposal<T>> = receive(otherSide)
            val stx: SignedTransaction = maybeProposal.unwrap {
                verifyProposal(it)
                verifyTx(it.stx)
                it.stx
            }
            approve(stx)
            return null
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
                signatures.forEach { it.verify(stx.id) }
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
            return serviceHub.createSignature(stx)
        }
    }
}

open class StateReplacementException @JvmOverloads constructor(message: String? = null, cause: Throwable? = null)
    : FlowException(message, cause)

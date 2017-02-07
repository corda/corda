package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.Party
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.signWithECDSA
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.node.services.TimestampChecker
import net.corda.core.node.services.UniquenessException
import net.corda.core.node.services.UniquenessProvider
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

object NotaryFlow {
    /**
     * A flow to be used for obtaining a signature from a [NotaryService] ascertaining the transaction
     * timestamp is correct and none of its inputs have been used in another completed transaction.
     *
     * @throws NotaryException in case the any of the inputs to the transaction have been consumed
     *                         by another transaction or the timestamp is invalid.
     */
    open class Client(private val stx: SignedTransaction,
                      override val progressTracker: ProgressTracker) : FlowLogic<DigitalSignature.WithKey>() {
        constructor(stx: SignedTransaction) : this(stx, Client.tracker())

        companion object {
            object REQUESTING : ProgressTracker.Step("Requesting signature by Notary service")
            object VALIDATING : ProgressTracker.Step("Validating response from Notary service")

            fun tracker() = ProgressTracker(REQUESTING, VALIDATING)
        }

        lateinit var notaryParty: Party

        @Suspendable
        @Throws(NotaryException::class)
        override fun call(): DigitalSignature.WithKey {
            progressTracker.currentStep = REQUESTING
            val wtx = stx.tx
            notaryParty = wtx.notary ?: throw IllegalStateException("Transaction does not specify a Notary")
            check(wtx.inputs.all { stateRef -> serviceHub.loadState(stateRef).notary == notaryParty }) {
                "Input states must have the same Notary"
            }
            try {
                stx.verifySignatures(notaryParty.owningKey)
            } catch (ex: SignedTransaction.SignaturesMissingException) {
                throw NotaryException(NotaryError.SignaturesMissing(ex))
            }

            val response = try {
                sendAndReceive<DigitalSignature.WithKey>(notaryParty, SignRequest(stx))
            } catch (e: NotaryException) {
                if (e.error is NotaryError.Conflict) {
                    e.error.conflict.verified()
                }
                throw e
            }

            return response.unwrap { sig ->
                validateSignature(sig, stx.id.bytes)
                sig
            }
        }

        private fun validateSignature(sig: DigitalSignature.WithKey, data: ByteArray) {
            check(sig.by in notaryParty.owningKey.keys) { "Invalid signer for the notary result" }
            sig.verifyWithECDSA(data)
        }
    }


    /**
     * Checks that the timestamp command is valid (if present) and commits the input state, or returns a conflict
     * if any of the input states have been previously committed.
     *
     * Extend this class, overriding _beforeCommit_ to add custom transaction processing/validation logic.
     *
     * TODO: the notary service should only be able to see timestamp commands and inputs
     */
    open class Service(val otherSide: Party,
                       val timestampChecker: TimestampChecker,
                       val uniquenessProvider: UniquenessProvider) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val stx = receive<SignRequest>(otherSide).unwrap { it.tx }
            val wtx = stx.tx

            validateTimestamp(wtx)
            beforeCommit(stx)
            commitInputStates(wtx)
            val sig = sign(stx.id.bytes)
            send(otherSide, sig)
        }

        private fun validateTimestamp(tx: WireTransaction) {
            if (tx.timestamp != null
                    && !timestampChecker.isValid(tx.timestamp))
                throw NotaryException(NotaryError.TimestampInvalid())
        }

        /**
         * No pre-commit processing is done. Transaction is not checked for contract-validity, as that would require fully
         * resolving it into a [TransactionForVerification], for which the caller would have to reveal the whole transaction
         * history chain.
         * As a result, the Notary _will commit invalid transactions_ as well, but as it also records the identity of
         * the caller, it is possible to raise a dispute and verify the validity of the transaction and subsequently
         * undo the commit of the input states (the exact mechanism still needs to be worked out).
         */
        @Suspendable
        open fun beforeCommit(stx: SignedTransaction) {
        }

        /**
         * A NotaryException is thrown if any of the states have been consumed by a different transaction. Note that
         * this method does not throw an exception when input states are present multiple times within the transaction.
         */
        private fun commitInputStates(tx: WireTransaction) {
            try {
                uniquenessProvider.commit(tx.inputs, tx.id, otherSide)
            } catch (e: UniquenessException) {
                val conflicts = tx.inputs.filterIndexed { i, stateRef ->
                    val consumingTx = e.error.stateHistory[stateRef]
                    consumingTx != null && consumingTx != UniquenessProvider.ConsumingTx(tx.id, i, otherSide)
                }
                if (conflicts.isNotEmpty()) {
                    // TODO: Create a new UniquenessException that only contains the conflicts filtered above.
                    throw notaryException(tx, e)
                }
            }
        }

        private fun sign(bits: ByteArray): DigitalSignature.WithKey {
            val mySigningKey = serviceHub.notaryIdentityKey
            return mySigningKey.signWithECDSA(bits)
        }

        private fun notaryException(tx: WireTransaction, e: UniquenessException): NotaryException {
            val conflictData = e.error.serialize()
            val signedConflict = SignedData(conflictData, sign(conflictData.bytes))
            return NotaryException(NotaryError.Conflict(tx, signedConflict))
        }
    }

    data class SignRequest(val tx: SignedTransaction)
}

class NotaryException(val error: NotaryError) : FlowException() {
    override fun toString() = "${super.toString()}: Error response from Notary - $error"
}

sealed class NotaryError {
    class Conflict(val tx: WireTransaction, val conflict: SignedData<UniquenessProvider.Conflict>) : NotaryError() {
        override fun toString() = "One or more input states for transaction ${tx.id} have been used in another transaction"
    }

    /** Thrown if the time specified in the timestamp command is outside the allowed tolerance */
    class TimestampInvalid : NotaryError()

    class TransactionInvalid(val msg: String) : NotaryError()
    class SignaturesInvalid(val msg: String): NotaryError()

    class SignaturesMissing(val cause: SignedTransaction.SignaturesMissingException) : NotaryError() {
        override fun toString() = cause.toString()
    }
}

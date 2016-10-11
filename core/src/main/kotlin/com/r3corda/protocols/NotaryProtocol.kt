package com.r3corda.protocols

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.crypto.DigitalSignature
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SignedData
import com.r3corda.core.crypto.signWithECDSA
import com.r3corda.core.node.services.TimestampChecker
import com.r3corda.core.node.services.UniquenessException
import com.r3corda.core.node.services.UniquenessProvider
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.serialization.SerializedBytes
import com.r3corda.core.serialization.serialize
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.core.transactions.WireTransaction
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.core.utilities.UntrustworthyData
import java.security.PublicKey

object NotaryProtocol {

    /**
     * A protocol to be used for obtaining a signature from a [NotaryService] ascertaining the transaction
     * timestamp is correct and none of its inputs have been used in another completed transaction.
     *
     * @throws NotaryException in case the any of the inputs to the transaction have been consumed
     *                         by another transaction or the timestamp is invalid.
     */
    open class Client(private val stx: SignedTransaction,
                      override val progressTracker: ProgressTracker = Client.tracker()) : ProtocolLogic<DigitalSignature.LegallyIdentifiable>() {

        companion object {

            object REQUESTING : ProgressTracker.Step("Requesting signature by Notary service")

            object VALIDATING : ProgressTracker.Step("Validating response from Notary service")

            fun tracker() = ProgressTracker(REQUESTING, VALIDATING)
        }

        lateinit var notaryParty: Party

        @Suspendable
        override fun call(): DigitalSignature.LegallyIdentifiable {
            progressTracker.currentStep = REQUESTING
            val wtx = stx.tx
            notaryParty = wtx.notary ?: throw IllegalStateException("Transaction does not specify a Notary")
            check(wtx.inputs.all { stateRef -> serviceHub.loadState(stateRef).notary == notaryParty }) {
                "Input states must have the same Notary"
            }
            try {
                stx.verifySignatures(notaryParty.owningKey)
            } catch (ex: SignedTransaction.SignaturesMissingException) {
                throw NotaryException(NotaryError.SignaturesMissing(ex.missing))
            }

            val request = SignRequest(stx, serviceHub.myInfo.legalIdentity)
            val response = sendAndReceive<Result>(notaryParty, request)

            return validateResponse(response)
        }

        @Throws(NotaryException::class, IllegalStateException::class)
        private fun validateResponse(response: UntrustworthyData<Result>): DigitalSignature.LegallyIdentifiable {
            return response.unwrap { notaryResult ->
                progressTracker.currentStep = VALIDATING
                when (notaryResult) {
                    is Result.Success -> {
                        validateSignature(notaryResult.sig, stx.txBits)
                        notaryResult.sig
                    }
                    is Result.Error -> {
                        if (notaryResult.error is NotaryError.Conflict)
                            notaryResult.error.conflict.verified()
                        throw NotaryException(notaryResult.error)
                    }
                    else -> throw IllegalStateException("Received invalid result from Notary service '$notaryParty'")
                }
            }
        }

        private fun validateSignature(sig: DigitalSignature.LegallyIdentifiable, data: SerializedBytes<WireTransaction>) {
            check(sig.signer == notaryParty) { "Notary result not signed by the correct service" }
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
                       val uniquenessProvider: UniquenessProvider) : ProtocolLogic<Unit>() {

        @Suspendable
        override fun call() {
            val (stx, reqIdentity) = receive<SignRequest>(otherSide).unwrap { it }
            val wtx = stx.tx

            val result = try {
                validateTimestamp(wtx)
                beforeCommit(stx, reqIdentity)
                commitInputStates(wtx, reqIdentity)

                val sig = sign(stx.txBits)
                Result.Success(sig)
            } catch(e: NotaryException) {
                Result.Error(e.error)
            }

            send(otherSide, result)
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
        open fun beforeCommit(stx: SignedTransaction, reqIdentity: Party) {
        }

        private fun commitInputStates(tx: WireTransaction, reqIdentity: Party) {
            try {
                uniquenessProvider.commit(tx.inputs, tx.id, reqIdentity)
            } catch (e: UniquenessException) {
                val conflictData = e.error.serialize()
                val signedConflict = SignedData(conflictData, sign(conflictData))
                throw NotaryException(NotaryError.Conflict(tx, signedConflict))
            }
        }

        private fun <T : Any> sign(bits: SerializedBytes<T>): DigitalSignature.LegallyIdentifiable {
            val myNodeInfo = serviceHub.myInfo
            val myIdentity = myNodeInfo.notaryIdentity
            val mySigningKey = serviceHub.notaryIdentityKey
            return mySigningKey.signWithECDSA(bits, myIdentity)
        }
    }

    /** TODO: The caller must authenticate instead of just specifying its identity */
    data class SignRequest(val tx: SignedTransaction, val callerIdentity: Party)

    sealed class Result {
        class Error(val error: NotaryError): Result()
        class Success(val sig: DigitalSignature.LegallyIdentifiable) : Result()
    }

}

class NotaryException(val error: NotaryError) : Exception() {
    override fun toString() = "${super.toString()}: Error response from Notary - $error"
}

sealed class NotaryError {
    class Conflict(val tx: WireTransaction, val conflict: SignedData<UniquenessProvider.Conflict>) : NotaryError() {
        override fun toString() = "One or more input states for transaction ${tx.id} have been used in another transaction"
    }

    /** Thrown if the time specified in the timestamp command is outside the allowed tolerance */
    class TimestampInvalid : NotaryError()

    class TransactionInvalid : NotaryError()

    class SignaturesMissing(val missingSigners: Set<PublicKey>) : NotaryError()
}

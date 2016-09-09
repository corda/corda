package com.r3corda.protocols

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.core.contracts.StateRef
import com.r3corda.core.contracts.Timestamp
import com.r3corda.core.transactions.WireTransaction
import com.r3corda.core.crypto.DigitalSignature
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SignedData
import com.r3corda.core.crypto.signWithECDSA
import com.r3corda.core.messaging.Ack
import com.r3corda.core.node.services.TimestampChecker
import com.r3corda.core.node.services.UniquenessException
import com.r3corda.core.node.services.UniquenessProvider
import com.r3corda.core.noneOrSingle
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.random63BitValue
import com.r3corda.core.serialization.SerializedBytes
import com.r3corda.core.serialization.serialize
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.core.utilities.UntrustworthyData
import java.security.PublicKey

object NotaryProtocol {

    val TOPIC = "platform.notary"

    /**
     * A protocol to be used for obtaining a signature from a [NotaryService] ascertaining the transaction
     * timestamp is correct and none of its inputs have been used in another completed transaction.
     *
     * @throws NotaryException in case the any of the inputs to the transaction have been consumed
     *                         by another transaction or the timestamp is invalid.
     */
    class Client(private val stx: SignedTransaction,
                 override val progressTracker: ProgressTracker = Client.tracker()) : ProtocolLogic<DigitalSignature.LegallyIdentifiable>() {

        companion object {

            object REQUESTING : ProgressTracker.Step("Requesting signature by Notary service")

            object VALIDATING : ProgressTracker.Step("Validating response from Notary service")

            fun tracker() = ProgressTracker(REQUESTING, VALIDATING)
        }

        override val topic: String get() = TOPIC

        lateinit var notaryParty: Party

        @Suspendable
        override fun call(): DigitalSignature.LegallyIdentifiable {
            progressTracker.currentStep = REQUESTING
            val wtx = stx.tx
            notaryParty = wtx.notary ?: throw IllegalStateException("Transaction does not specify a Notary")
            check(wtx.inputs.all { stateRef -> serviceHub.loadState(stateRef).notary == notaryParty }) { "Input states must have the same Notary" }

            val sendSessionID = random63BitValue()
            val receiveSessionID = random63BitValue()

            val handshake = Handshake(serviceHub.storageService.myLegalIdentity, sendSessionID, receiveSessionID)
            sendAndReceive<Ack>(notaryParty, 0, receiveSessionID, handshake)

            val request = SignRequest(stx, serviceHub.storageService.myLegalIdentity)
            val response = sendAndReceive<Result>(notaryParty, sendSessionID, receiveSessionID, request)

            val notaryResult = validateResponse(response)
            return notaryResult.sig ?: throw NotaryException(notaryResult.error!!)
        }

        private fun validateResponse(response: UntrustworthyData<Result>): Result {
            progressTracker.currentStep = VALIDATING

            response.unwrap {
                if (it.sig != null) validateSignature(it.sig, stx.txBits)
                else if (it.error is NotaryError.Conflict) it.error.conflict.verified()
                else if (it.error == null || it.error !is NotaryError)
                    throw IllegalStateException("Received invalid result from Notary service '$notaryParty'")
                return it
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
                       val sendSessionID: Long,
                       val receiveSessionID: Long,
                       val timestampChecker: TimestampChecker,
                       val uniquenessProvider: UniquenessProvider) : ProtocolLogic<Unit>() {

        override val topic: String get() = TOPIC

        @Suspendable
        override fun call() {
            val (stx, reqIdentity) = receive<SignRequest>(receiveSessionID).unwrap { it }
            val wtx = stx.tx

            val result = try {
                validateTimestamp(wtx)
                beforeCommit(stx, reqIdentity)
                commitInputStates(wtx, reqIdentity)

                val sig = sign(stx.txBits)
                Result.noError(sig)
            } catch(e: NotaryException) {
                Result.withError(e.error)
            }

            send(otherSide, sendSessionID, result)
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
            val mySigningKey = serviceHub.storageService.myLegalIdentityKey
            val myIdentity = serviceHub.storageService.myLegalIdentity
            return mySigningKey.signWithECDSA(bits, myIdentity)
        }
    }

    data class Handshake(
            override  val replyToParty: Party,
            val sendSessionID: Long,
            override val sessionID: Long) : PartyRequestMessage

    /** TODO: The caller must authenticate instead of just specifying its identity */
    data class SignRequest(val tx: SignedTransaction, val callerIdentity: Party)

    data class Result private constructor(val sig: DigitalSignature.LegallyIdentifiable?, val error: NotaryError?) {
        companion object {
            fun withError(error: NotaryError) = Result(null, error)
            fun noError(sig: DigitalSignature.LegallyIdentifiable) = Result(sig, null)
        }
    }

    interface Factory {
        fun create(otherSide: Party,
                   sendSessionID: Long,
                   receiveSessionID: Long,
                   timestampChecker: TimestampChecker,
                   uniquenessProvider: UniquenessProvider): Service
    }

    object DefaultFactory : Factory {
        override fun create(otherSide: Party,
                            sendSessionID: Long,
                            receiveSessionID: Long,
                            timestampChecker: TimestampChecker,
                            uniquenessProvider: UniquenessProvider): Service {
            return Service(otherSide, sendSessionID, receiveSessionID, timestampChecker, uniquenessProvider)
        }
    }
}

class NotaryException(val error: NotaryError) : Exception() {
    override fun toString() = "${super.toString()}: Error response from Notary - ${error.toString()}"
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

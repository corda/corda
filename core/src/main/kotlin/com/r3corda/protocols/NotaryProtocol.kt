package com.r3corda.protocols

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.contracts.TimestampCommand
import com.r3corda.core.contracts.WireTransaction
import com.r3corda.core.crypto.DigitalSignature
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SignedData
import com.r3corda.core.crypto.signWithECDSA
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.TimestampChecker
import com.r3corda.core.node.services.UniquenessException
import com.r3corda.core.node.services.UniquenessProvider
import com.r3corda.core.noneOrSingle
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.random63BitValue
import com.r3corda.core.serialization.SerializedBytes
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.serialization.serialize
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.core.utilities.UntrustworthyData
import java.security.PublicKey

object NotaryProtocol {
    val TOPIC = "platform.notary.request"
    val TOPIC_INITIATE = "platform.notary.initiate"

    /**
     * A protocol to be used for obtaining a signature from a [NotaryService] ascertaining the transaction
     * timestamp is correct and none of its inputs have been used in another completed transaction
     *
     * @throws NotaryException in case the any of the inputs to the transaction have been consumed
     *                         by another transaction or the timestamp is invalid
     */
    class Client(private val wtx: WireTransaction,
                 override val progressTracker: ProgressTracker = Client.tracker()) : ProtocolLogic<DigitalSignature.LegallyIdentifiable>() {
        companion object {

            object REQUESTING : ProgressTracker.Step("Requesting signature by Notary service")

            object VALIDATING : ProgressTracker.Step("Validating response from Notary service")

            fun tracker() = ProgressTracker(REQUESTING, VALIDATING)
        }

        lateinit var notaryNode: NodeInfo

        @Suspendable
        override fun call(): DigitalSignature.LegallyIdentifiable {
            progressTracker.currentStep = REQUESTING
            notaryNode = findNotaryNode()

            val sendSessionID = random63BitValue()
            val receiveSessionID = random63BitValue()

            val handshake = Handshake(serviceHub.networkService.myAddress, sendSessionID, receiveSessionID)
            sendAndReceive<Unit>(TOPIC_INITIATE, notaryNode.address, 0, receiveSessionID, handshake)

            val request = SignRequest(wtx.serialized, serviceHub.storageService.myLegalIdentity)
            val response = sendAndReceive<Result>(TOPIC, notaryNode.address, sendSessionID, receiveSessionID, request)

            val notaryResult = validateResponse(response)
            return notaryResult.sig ?: throw NotaryException(notaryResult.error!!)
        }

        private fun validateResponse(response: UntrustworthyData<Result>): Result {
            progressTracker.currentStep = VALIDATING

            response.validate {
                if (it.sig != null) validateSignature(it.sig, wtx.serialized)
                else if (it.error is NotaryError.Conflict) it.error.conflict.verified()
                else if (it.error == null || it.error !is NotaryError)
                    throw IllegalStateException("Received invalid result from Notary service '${notaryNode.identity}'")
                return it
            }
        }

        private fun validateSignature(sig: DigitalSignature.LegallyIdentifiable, data: SerializedBytes<WireTransaction>) {
            check(sig.signer == notaryNode.identity) { "Notary result not signed by the correct service" }
            sig.verifyWithECDSA(data)
        }

        private fun findNotaryNode(): NodeInfo {
            var maybeNotaryKey: PublicKey? = null

            val timestampCommand = wtx.commands.singleOrNull { it.value is TimestampCommand }
            if (timestampCommand != null) maybeNotaryKey = timestampCommand.signers.first()

            for (stateRef in wtx.inputs) {
                val inputNotaryKey = serviceHub.loadState(stateRef).notary.owningKey
                if (maybeNotaryKey != null)
                    check(maybeNotaryKey == inputNotaryKey) { "Input states and timestamp must have the same Notary" }
                else maybeNotaryKey = inputNotaryKey
            }

            val notaryKey = maybeNotaryKey ?: throw IllegalStateException("Transaction does not specify a Notary")
            val notaryNode = serviceHub.networkMapCache.getNodeByPublicKey(notaryKey)
            return notaryNode ?: throw IllegalStateException("No Notary node can be found with the specified public key")
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
    open class Service(val otherSide: SingleMessageRecipient,
                       val sendSessionID: Long,
                       val receiveSessionID: Long,
                       val timestampChecker: TimestampChecker,
                       val uniquenessProvider: UniquenessProvider) : ProtocolLogic<Unit>() {
        @Suspendable
        override fun call() {
            val request = receive<SignRequest>(TOPIC, receiveSessionID).validate { it }
            val txBits = request.txBits
            val reqIdentity = request.callerIdentity

            val wtx = txBits.deserialize()
            val result: Result
            try {
                validateTimestamp(wtx)
                beforeCommit(wtx, reqIdentity)
                commitInputStates(wtx, reqIdentity)

                val sig = sign(txBits)
                result = Result.noError(sig)

            } catch(e: NotaryException) {
                result = Result.withError(e.error)
            }

            send(TOPIC, otherSide, sendSessionID, result)
        }

        private fun validateTimestamp(tx: WireTransaction) {
            val timestampCmd = try {
                tx.commands.noneOrSingle { it.value is TimestampCommand } ?: return
            } catch (e: IllegalArgumentException) {
                throw NotaryException(NotaryError.MoreThanOneTimestamp())
            }
            val myIdentity = serviceHub.storageService.myLegalIdentity
            if (!timestampCmd.signers.contains(myIdentity.owningKey))
                throw NotaryException(NotaryError.NotForMe())
            if (!timestampChecker.isValid(timestampCmd.value as TimestampCommand))
                throw NotaryException(NotaryError.TimestampInvalid())
        }

        /**
         * No pre-commit processing is done. Transaction is not checked for contract-validity, as that would require fully
         * resolving it into a [TransactionForVerification], for which the caller would have to reveal the whole transaction
         * history chain.
         * As a result, the Notary _will commit invalid transactions_ as well, but as it also records the identity of
         * the caller, it is possible to raise a dispute and verify the validity of the transaction and subsequently
         * undo the commit of the input states (the exact mechanism still needs to be worked out)
         */
        @Suspendable
        open fun beforeCommit(wtx: WireTransaction, reqIdentity: Party) {
        }

        private fun commitInputStates(tx: WireTransaction, reqIdentity: Party) {
            try {
                uniquenessProvider.commit(tx, reqIdentity)
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

    class Handshake(
            replyTo: SingleMessageRecipient,
            val sendSessionID: Long,
            sessionID: Long) : AbstractRequestMessage(replyTo, sessionID)

    /** TODO: The caller must authenticate instead of just specifying its identity */
    class SignRequest(val txBits: SerializedBytes<WireTransaction>,
                      val callerIdentity: Party)

    data class Result private constructor(val sig: DigitalSignature.LegallyIdentifiable?, val error: NotaryError?) {
        companion object {
            fun withError(error: NotaryError) = Result(null, error)
            fun noError(sig: DigitalSignature.LegallyIdentifiable) = Result(sig, null)
        }
    }

    interface Factory {
        fun create(otherSide: SingleMessageRecipient,
                   sendSessionID: Long,
                   receiveSessionID: Long,
                   timestampChecker: TimestampChecker,
                   uniquenessProvider: UniquenessProvider): Service
    }

    object DefaultFactory : Factory {
        override fun create(otherSide: SingleMessageRecipient,
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

    class MoreThanOneTimestamp : NotaryError()

    /** Thrown if the timestamp command in the transaction doesn't list this Notary as a signer */
    class NotForMe : NotaryError()

    /** Thrown if the time specified in the timestamp command is outside the allowed tolerance */
    class TimestampInvalid : NotaryError()

    class TransactionInvalid : NotaryError()
}
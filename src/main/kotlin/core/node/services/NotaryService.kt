package core.node.services

import core.Party
import core.TimestampCommand
import core.WireTransaction
import core.crypto.DigitalSignature
import core.crypto.SignedData
import core.crypto.signWithECDSA
import core.messaging.MessagingService
import core.serialization.SerializedBytes
import core.serialization.deserialize
import core.serialization.serialize
import core.utilities.loggerFor
import protocols.NotaryProtocol
import java.security.KeyPair

/**
 * A Notary service acts as the final signer of a transaction ensuring two things:
 * - The (optional) timestamp of the transaction is valid
 * - None of the referenced input states have previously been consumed by a transaction signed by this Notary
 *
 * A transaction has to be signed by a Notary to be considered valid (except for output-only transactions w/o a timestamp)
 */
class NotaryService(net: MessagingService,
                    val identity: Party,
                    val signingKey: KeyPair,
                    val uniquenessProvider: UniquenessProvider,
                    val timestampChecker: TimestampChecker) : AbstractNodeService(net) {
    object Type : ServiceType("corda.notary")

    private val logger = loggerFor<NotaryService>()

    init {
        check(identity.owningKey == signingKey.public)
        addMessageHandler(NotaryProtocol.TOPIC,
                { req: NotaryProtocol.SignRequest -> processRequest(req.txBits, req.callerIdentity) },
                { message, e -> logger.error("Exception during notary service request processing", e) }
        )
    }

    /**
     * Checks that the timestamp command is valid (if present) and commits the input state, or returns a conflict
     * if any of the input states have been previously committed
     *
     * Note that the transaction is not checked for contract-validity, as that would require fully resolving it
     * into a [TransactionForVerification], for which the caller would have to reveal the whole transaction history chain.
     * As a result, the Notary _will commit invalid transactions_ as well, but as it also records the identity of
     * the caller, it is possible to raise a dispute and verify the validity of the transaction and subsequently
     * undo the commit of the input states (the exact mechanism still needs to be worked out)
     *
     * TODO: the notary service should only be able to see timestamp commands and inputs
     */
    fun processRequest(txBits: SerializedBytes<WireTransaction>, reqIdentity: Party): Result {
        val wtx = txBits.deserialize()
        try {
            validateTimestamp(wtx)
            commitInputStates(wtx, reqIdentity)
        } catch(e: NotaryException) {
            return Result.withError(e.error)
        }

        val sig = sign(txBits)
        return Result.noError(sig)
    }

    private fun validateTimestamp(tx: WireTransaction) {
        // Need to have at most one timestamp command
        val timestampCmds = tx.commands.filter { it.value is TimestampCommand }
        if (timestampCmds.count() > 1)
            throw NotaryException(NotaryError.MoreThanOneTimestamp())

        val timestampCmd = timestampCmds.singleOrNull() ?: return
        if (!timestampCmd.signers.contains(identity.owningKey))
            throw NotaryException(NotaryError.NotForMe())
        if (!timestampChecker.isValid(timestampCmd.value as TimestampCommand))
            throw NotaryException(NotaryError.TimestampInvalid())
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
        return signingKey.signWithECDSA(bits, identity)
    }

    data class Result private constructor(val sig: DigitalSignature.LegallyIdentifiable?, val error: NotaryError?) {
        companion object {
            fun withError(error: NotaryError) = Result(null, error)
            fun noError(sig: DigitalSignature.LegallyIdentifiable) = Result(sig, null)
        }
    }
}

class NotaryException(val error: NotaryError) : Exception()

sealed class NotaryError {
    class Conflict(val tx: WireTransaction, val conflict: SignedData<UniquenessProvider.Conflict>) : NotaryError() {
        override fun toString() = "One or more input states for transaction ${tx.id} have been used in another transaction"
    }

    class MoreThanOneTimestamp : NotaryError()

    /** Thrown if the timestamp command in the transaction doesn't list this Notary as a signer */
    class NotForMe : NotaryError()

    /** Thrown if the time specified in the timestamp command is outside the allowed tolerance */
    class TimestampInvalid : NotaryError()
}
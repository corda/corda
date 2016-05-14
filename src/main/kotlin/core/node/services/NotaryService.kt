package core.node.services

import core.crypto.Party
import core.contracts.TimestampCommand
import core.contracts.WireTransaction
import core.crypto.DigitalSignature
import core.crypto.SignedData
import core.crypto.signWithECDSA
import core.messaging.MessagingService
import core.noneOrSingle
import core.serialization.SerializedBytes
import core.serialization.deserialize
import core.serialization.serialize
import core.utilities.loggerFor
import protocols.NotaryError
import protocols.NotaryException
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
    fun processRequest(txBits: SerializedBytes<WireTransaction>, reqIdentity: Party): NotaryProtocol.Result {
        val wtx = txBits.deserialize()
        try {
            validateTimestamp(wtx)
            commitInputStates(wtx, reqIdentity)
        } catch(e: NotaryException) {
            return NotaryProtocol.Result.withError(e.error)
        }

        val sig = sign(txBits)
        return NotaryProtocol.Result.noError(sig)
    }

    private fun validateTimestamp(tx: WireTransaction) {
        val timestampCmd = try {
            tx.commands.noneOrSingle { it.value is TimestampCommand } ?: return
        } catch (e: IllegalArgumentException) {
            throw NotaryException(NotaryError.MoreThanOneTimestamp())
        }
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

}


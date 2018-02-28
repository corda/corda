package net.corda.core.contracts

import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowException
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.NonEmptySet
import net.corda.nodeapiinterfaces.serialization.UnusedConstructorParameter
import net.corda.nodeapiinterfaces.serialization.SerializationOnlyParameter
import java.security.PublicKey

class TransactionResolutionException(val hash: SecureHash) : FlowException("Transaction resolution failure for $hash")
class AttachmentResolutionException(val hash: SecureHash) : FlowException("Attachment resolution failure for $hash")

@Suppress("UNUSED_PARAMETER")
sealed class TransactionVerificationException : FlowException {
    abstract val txId: SecureHash

    constructor (txId: SecureHash, message: String, cause: Throwable?) : super(message, txId, cause)
    constructor (message: String, cause: Throwable?) : super(message, cause)

    class ContractRejection(
            override val txId: SecureHash,
            message: String,
            cause: Throwable?,
            @UnusedConstructorParameter p: SerializationOnlyParameter?)
        : TransactionVerificationException(message, cause) {

        constructor(txId: SecureHash, contract: Contract, cause: Throwable) :
                this(txId, "Contract verification failed: ${cause.message}, contract: $contract", cause, null)
    }

    class ContractConstraintRejection(
            override val txId: SecureHash, message: String, @UnusedConstructorParameter p: SerializationOnlyParameter?)
        : TransactionVerificationException(message, null) {

        constructor (txId: SecureHash, contractClass: String) :
                this(txId, "Contract constraints failed for $contractClass", null)
    }

    class MissingAttachmentRejection(
            override val txId: SecureHash, message: String, @UnusedConstructorParameter p: SerializationOnlyParameter?)
        : TransactionVerificationException(message, null) {

        constructor (txId: SecureHash, contractClass: String) :
                this(txId, "Contract constraints failed, could not find attachment for: $contractClass", null)
    }

    class ConflictingAttachmentsRejection(
            override val txId: SecureHash, message: String, @UnusedConstructorParameter p: SerializationOnlyParameter?)
        : TransactionVerificationException(message, null) {

        constructor (txId: SecureHash, contractClass: String) :
                this (txId, "Contract constraints failed for: $contractClass, because multiple " +
                        "attachments providing this contract were attached.", null)
    }

    class ContractCreationError(
            override val txId:
            SecureHash,
            message: String,
            cause: Throwable,
            @UnusedConstructorParameter p: SerializationOnlyParameter?)
        : TransactionVerificationException(message, cause) {
        constructor (txId: SecureHash, contractClass: String, cause: Throwable) :
            this (txId, "Contract verification failed: ${cause.message}, could not create contract " +
                    "class: $contractClass", cause, null)
    }

    class MoreThanOneNotary(override val txId: SecureHash, message: String)
        : TransactionVerificationException(message, null) {
        constructor(txId: SecureHash) : this (txId, "More than one notary")
    }

    class SignersMissing(override val txId: SecureHash, message: String)
        : TransactionVerificationException(message, null) {
        constructor(txId: SecureHash, missing: List<PublicKey>) :
                this (txId, "Signers missing: ${missing.joinToString()}")
    }

    class DuplicateInputStates(
            override val txId: SecureHash,
            message: String,
            @UnusedConstructorParameter p: SerializationOnlyParameter?)
        : TransactionVerificationException(message, null) {
        constructor(txId: SecureHash, duplicates: NonEmptySet<StateRef>) :
                this (txId, "Duplicate inputs: ${duplicates.joinToString()}", null)
    }

    class InvalidNotaryChange(
            override val txId: SecureHash, message: String, @UnusedConstructorParameter p: SerializationOnlyParameter?)
        : TransactionVerificationException(message, null) {
        constructor(txId: SecureHash) :
                this (txId, "Detected a notary change. Outputs must use the same notary as inputs", null)
    }

    class NotaryChangeInWrongTransactionType(
            override val txId: SecureHash, message: String)
        : TransactionVerificationException(message, null) {
        constructor(txId: SecureHash, txNotary: Party, outputNotary: Party) :
                this (txId, "Found unexpected notary change in transaction. Tx notary: $txNotary, found: $outputNotary")
    }

    class TransactionMissingEncumbranceException(override val txId: SecureHash, message: String)
        : TransactionVerificationException(message, null) {
        constructor(txId: SecureHash, missing: Int, inOut: Direction) :
            this (txId, "Missing required encumbrance $missing in $inOut")
    }

    @CordaSerializable
    enum class Direction {
        INPUT,
        OUTPUT
    }
}

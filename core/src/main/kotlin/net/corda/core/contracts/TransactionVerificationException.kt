package net.corda.core.contracts

import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowException
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.NonEmptySet
import java.security.PublicKey

class TransactionResolutionException(val hash: SecureHash) : FlowException("Transaction resolution failure for $hash")
class AttachmentResolutionException(val hash: SecureHash) : FlowException("Attachment resolution failure for $hash")

sealed class TransactionVerificationException(val txId: SecureHash, message: String, cause: Throwable?)
    : FlowException("$message, transaction: $txId", cause) {

    class ContractRejection(txId: SecureHash, contract: Contract, cause: Throwable)
        : TransactionVerificationException(txId, "Contract verification failed: ${cause.message}, contract: $contract", cause)

    class ContractConstraintRejection(txId: SecureHash, contractClass: String)
        : TransactionVerificationException(txId, "Contract constraints failed for $contractClass", null)

    class MissingAttachmentRejection(txId: SecureHash, val contractClass: String)
        : TransactionVerificationException(txId, "Contract constraints failed, could not find attachment for: $contractClass", null)

    class ContractCreationError(txId: SecureHash, contractClass: String, cause: Throwable)
        : TransactionVerificationException(txId, "Contract verification failed: ${cause.message}, could not create contract class: $contractClass", cause)

    class MoreThanOneNotary(txId: SecureHash)
        : TransactionVerificationException(txId, "More than one notary", null)

    class SignersMissing(txId: SecureHash, missing: List<PublicKey>)
        : TransactionVerificationException(txId, "Signers missing: ${missing.joinToString()}", null)

    class DuplicateInputStates(txId: SecureHash, val duplicates: NonEmptySet<StateRef>)
        : TransactionVerificationException(txId, "Duplicate inputs: ${duplicates.joinToString()}", null)

    class InvalidNotaryChange(txId: SecureHash)
        : TransactionVerificationException(txId, "Detected a notary change. Outputs must use the same notary as inputs", null)

    class NotaryChangeInWrongTransactionType(txId: SecureHash, txNotary: Party, outputNotary: Party)
        : TransactionVerificationException(txId, "Found unexpected notary change in transaction. Tx notary: $txNotary, found: $outputNotary", null)

    class TransactionMissingEncumbranceException(txId: SecureHash, missing: Int, inOut: Direction)
        : TransactionVerificationException(txId, "Missing required encumbrance $missing in $inOut", null)

    @CordaSerializable
    enum class Direction {
        INPUT,
        OUTPUT
    }
}

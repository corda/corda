package net.corda.core.contracts

import net.corda.annotations.serialization.Serializable
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowException
import net.corda.core.identity.Party
import net.corda.core.utilities.NonEmptySet
import java.security.PublicKey

/**
 * The node asked a remote peer for the transaction identified by [hash] because it is a dependency of a transaction
 * being resolved, but the remote peer would not provide it.
 *
 * @property hash Merkle root of the transaction being resolved, see [net.corda.core.transactions.WireTransaction.id]
 */
class TransactionResolutionException(val hash: SecureHash) : FlowException("Transaction resolution failure for $hash")

/**
 * The node asked a remote peer for the attachment identified by [hash] because it is a dependency of a transaction
 * being resolved, but the remote peer would not provide it.
 *
 * @property hash Hash of the bytes of the attachment, see [Attachment.id]
 */
class AttachmentResolutionException(val hash: SecureHash) : FlowException("Attachment resolution failure for $hash")

/**
 * Indicates that some aspect of the transaction named by [txId] violates the platform rules. The exact type of failure
 * is expressed using a subclass. TransactionVerificationException is a [FlowException] and thus when thrown inside
 * a flow, the details of the failure will be serialised, propagated to the peer and rethrown.
 *
 * @property txId the Merkle root hash (identifier) of the transaction that failed verification.
 */
@Suppress("MemberVisibilityCanBePrivate")
@Serializable
sealed class TransactionVerificationException(val txId: SecureHash, message: String, cause: Throwable?)
    : FlowException("$message, transaction: $txId", cause) {

    /**
     * Indicates that one of the [Contract.verify] methods selected by the contract constraints and attachments
     * rejected the transaction by throwing an exception.
     *
     * @property contractClass The fully qualified class name of the failing contract.
     */
    class ContractRejection(txId: SecureHash, val contractClass: String, cause: Throwable) : TransactionVerificationException(txId, "Contract verification failed: ${cause.message}, contract: $contractClass", cause) {
        constructor(txId: SecureHash, contract: Contract, cause: Throwable) :  this(txId, contract.javaClass.name, cause)
    }

    /**
     * The transaction attachment that contains the [contractClass] class didn't meet the constraints specified by
     * the [TransactionState.constraint] object. This usually implies a version mismatch of some kind.
     *
     * @property contractClass The fully qualified class name of the failing contract.
     */
    class ContractConstraintRejection(txId: SecureHash, val contractClass: String)
        : TransactionVerificationException(txId, "Contract constraints failed for $contractClass", null)

    /**
     * A state requested a contract class via its [TransactionState.contract] field that didn't appear in any attached
     * JAR at all. This usually implies the attachments were forgotten or a version mismatch.
     *
     * @property contractClass The fully qualified class name of the failing contract.
     */
    class MissingAttachmentRejection(txId: SecureHash, val contractClass: String)
        : TransactionVerificationException(txId, "Contract constraints failed, could not find attachment for: $contractClass", null)

    /**
     * Indicates this transaction violates the "no overlap" rule: two attachments are trying to provide the same file
     * path. Whereas Java classpaths would normally allow that with the first class taking precedence, this is not
     * allowed in transactions for security reasons. This usually indicates that two separate apps share a dependency,
     * in which case you could try 'shading the fat jars' to rename classes of dependencies. Or you could manually
     * attach dependency JARs when building the transaction.
     *
     * @property contractClass The fully qualified class name of the failing contract.
     */
    class ConflictingAttachmentsRejection(txId: SecureHash, val contractClass: String)
        : TransactionVerificationException(txId, "Contract constraints failed for: $contractClass, because multiple attachments providing this contract were attached.", null)

    /**
     * A [Contract] class named by a state could not be constructed. Most likely you do not have a no-argument
     * constructor, or the class doesn't subclass [Contract].
     *
     * @property contractClass The fully qualified class name of the failing contract.
     */
    class ContractCreationError(txId: SecureHash, val contractClass: String, cause: Throwable)
        : TransactionVerificationException(txId, "Contract verification failed: ${cause.message}, could not create contract class: $contractClass", cause)

    /**
     * An output state has a notary that doesn't match the transaction's notary field. It must!
     *
     * @property txNotary the [Party] specified by the transaction header.
     * @property outputNotary the [Party] specified by the errant state.
     */
    class NotaryChangeInWrongTransactionType(txId: SecureHash, val txNotary: Party, val outputNotary: Party)
        : TransactionVerificationException(txId, "Found unexpected notary change in transaction. Tx notary: $txNotary, found: $outputNotary", null)

    /**
     * If a state is encumbered (the [TransactionState.encumbrance] field is set) then its encumbrance must be used
     * as an input to any transaction that uses it. In this way states can be tied together in chains, thus composing
     * logic. Note that encumbrances aren't fully supported by all aspects of the platform at this time so if you use
     * them, you may find transactions created by the platform don't always respect the encumbrance rule.
     *
     * @property missing the index of the state missing the encumbrance.
     * @property inOut whether the issue exists in the input list or output list.
     */
    class TransactionMissingEncumbranceException(txId: SecureHash, val missing: Int, val inOut: Direction)
        : TransactionVerificationException(txId, "Missing required encumbrance $missing in $inOut", null)

    /** Whether the inputs or outputs list contains an encumbrance issue, see [TransactionMissingEncumbranceException]. */
    @Serializable
    enum class Direction {
        /** Issue in the inputs list */ INPUT,
        /** Issue in the outputs list */ OUTPUT
    }

    // We could revisit and throw this more appropriate type in a future release that uses targetVersion to
    // avoid the compatibility break, because IllegalStateException isn't ideal for this. Or we could use this
    // as a cause.
    /** @suppress This class is not used: duplicate inputs throw a [IllegalStateException] instead. */
    @Deprecated("This class is not used: duplicate inputs throw a [IllegalStateException] instead.")
    class DuplicateInputStates(txId: SecureHash, val duplicates: NonEmptySet<StateRef>)
        : TransactionVerificationException(txId, "Duplicate inputs: ${duplicates.joinToString()}", null)

    /** @suppress This class is obsolete and nothing has ever used it. */
    @Deprecated("This class is obsolete and nothing has ever used it.")
    class MoreThanOneNotary(txId: SecureHash) : TransactionVerificationException(txId, "More than one notary", null)

    /** @suppress This class is obsolete and nothing has ever used it. */
    @Deprecated("This class is obsolete and nothing has ever used it.")
    class SignersMissing(txId: SecureHash, val missing: List<PublicKey>) : TransactionVerificationException(txId, "Signers missing: ${missing.joinToString()}", null)

    /** @suppress This class is obsolete and nothing has ever used it. */
    @Deprecated("This class is obsolete and nothing has ever used it.")
    class InvalidNotaryChange(txId: SecureHash)
        : TransactionVerificationException(txId, "Detected a notary change. Outputs must use the same notary as inputs", null)
}

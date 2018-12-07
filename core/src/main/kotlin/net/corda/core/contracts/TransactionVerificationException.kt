package net.corda.core.contracts

import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowException
import net.corda.core.identity.Party
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.NonEmptySet
import java.security.PublicKey

/**
 * The node asked a remote peer for the transaction identified by [hash] because it is a dependency of a transaction
 * being resolved, but the remote peer would not provide it.
 *
 * @property hash Merkle root of the transaction being resolved, see [net.corda.core.transactions.WireTransaction.id]
 */
@KeepForDJVM
class TransactionResolutionException(val hash: SecureHash) : FlowException("Transaction resolution failure for $hash")

/**
 * The node asked a remote peer for the attachment identified by [hash] because it is a dependency of a transaction
 * being resolved, but the remote peer would not provide it.
 *
 * @property hash Hash of the bytes of the attachment, see [Attachment.id]
 */
@KeepForDJVM
class AttachmentResolutionException(val hash: SecureHash) : FlowException("Attachment resolution failure for $hash")

/**
 * Indicates that some aspect of the transaction named by [txId] violates the platform rules. The exact type of failure
 * is expressed using a subclass. TransactionVerificationException is a [FlowException] and thus when thrown inside
 * a flow, the details of the failure will be serialised, propagated to the peer and rethrown.
 *
 * @property txId the Merkle root hash (identifier) of the transaction that failed verification.
 */
@Suppress("MemberVisibilityCanBePrivate")
@CordaSerializable
abstract class TransactionVerificationException(val txId: SecureHash, message: String, cause: Throwable?)
    : FlowException("$message, transaction: $txId", cause) {

    /**
     * Indicates that one of the [Contract.verify] methods selected by the contract constraints and attachments
     * rejected the transaction by throwing an exception.
     *
     * @property contractClass The fully qualified class name of the failing contract.
     */
    @KeepForDJVM
    class ContractRejection(txId: SecureHash, val contractClass: String, cause: Throwable) : TransactionVerificationException(txId, "Contract verification failed: ${cause.message}, contract: $contractClass", cause) {
        constructor(txId: SecureHash, contract: Contract, cause: Throwable) : this(txId, contract.javaClass.name, cause)
    }

    /**
     * This exception happens when a transaction was not built correctly.
     * When a contract is not annotated with [NoConstraintPropagation], then the platform ensures that the constraints of output states transition correctly from input states.
     *
     * @property txId The transaction.
     * @property contractClass The fully qualified class name of the failing contract.
     * @property inputConstraint The constraint of the input state.
     * @property outputConstraint The constraint of the outputs state.
     */
    @KeepForDJVM
    class ConstraintPropagationRejection(txId: SecureHash, val contractClass: String, inputConstraint: AttachmentConstraint, outputConstraint: AttachmentConstraint)
        : TransactionVerificationException(txId, "Contract constraints for $contractClass are not propagated correctly. The outputConstraint: $outputConstraint is not a valid transition from the input constraint: $inputConstraint.", null)

    /**
     * The transaction attachment that contains the [contractClass] class didn't meet the constraints specified by
     * the [TransactionState.constraint] object. This usually implies a version mismatch of some kind.
     *
     * @property contractClass The fully qualified class name of the failing contract.
     */
    @KeepForDJVM
    class ContractConstraintRejection(txId: SecureHash, val contractClass: String)
        : TransactionVerificationException(txId, "Contract constraints failed for $contractClass", null)

    /**
     * A state requested a contract class via its [TransactionState.contract] field that didn't appear in any attached
     * JAR at all. This usually implies the attachments were forgotten or a version mismatch.
     *
     * @property contractClass The fully qualified class name of the failing contract.
     */
    @KeepForDJVM
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
    @KeepForDJVM
    class ConflictingAttachmentsRejection(txId: SecureHash, val contractClass: String)
        : TransactionVerificationException(txId, "Contract constraints failed for: $contractClass, because multiple attachments providing this contract were attached.", null)

    /**
     * A [Contract] class named by a state could not be constructed. Most likely you do not have a no-argument
     * constructor, or the class doesn't subclass [Contract].
     *
     * @property contractClass The fully qualified class name of the failing contract.
     */
    @KeepForDJVM
    class ContractCreationError(txId: SecureHash, val contractClass: String, cause: Throwable)
        : TransactionVerificationException(txId, "Contract verification failed: ${cause.message}, could not create contract class: $contractClass", cause)

    /**
     * An output state has a notary that doesn't match the transaction's notary field. It must!
     *
     * @property txNotary the [Party] specified by the transaction header.
     * @property outputNotary the [Party] specified by the errant state.
     */
    @KeepForDJVM
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
    @KeepForDJVM
    class TransactionMissingEncumbranceException(txId: SecureHash, val missing: Int, val inOut: Direction)
        : TransactionVerificationException(txId, "Missing required encumbrance $missing in $inOut", null)

    /**
     * If two or more states refer to another state (as their encumbrance), then the bi-directionality property cannot
     * be satisfied.
     */
    @KeepForDJVM
    class TransactionDuplicateEncumbranceException(txId: SecureHash, index: Int)
        : TransactionVerificationException(txId, "The bi-directionality property of encumbered output states " +
            "is not satisfied. Index $index is referenced more than once", null)

    /**
     * An encumbered state should also be referenced as the encumbrance of another state in order to satisfy the
     * bi-directionality property (a full cycle should be present).
     */
    @KeepForDJVM
    class TransactionNonMatchingEncumbranceException(txId: SecureHash, nonMatching: Collection<Int>)
        : TransactionVerificationException(txId, "The bi-directionality property of encumbered output states " +
            "is not satisfied. Encumbered states should also be referenced as an encumbrance of another state to form " +
            "a full cycle. Offending indices $nonMatching", null)

    /**
     * All encumbered states should be assigned to the same notary. This is due to the fact that multi-notary
     * transactions are not supported and thus two encumbered states with different notaries cannot be consumed
     * in the same transaction.
     */
    @KeepForDJVM
    class TransactionNotaryMismatchEncumbranceException(txId: SecureHash, encumberedIndex: Int, encumbranceIndex: Int, encumberedNotary: Party, encumbranceNotary: Party)
        : TransactionVerificationException(txId, "Encumbered output states assigned to different notaries found. " +
            "Output state with index $encumberedIndex is assigned to notary [$encumberedNotary], while its encumbrance with index $encumbranceIndex is assigned to notary [$encumbranceNotary]", null)

    /**
     * If a state is identified as belonging to a contract, either because the state class is defined as an inner class
     * of the contract class or because the state class is annotated with [BelongsToContract], then it must not be
     * bundled in a [TransactionState] with a different contract.
     *
     * @param state The [TransactionState] whose bundled state and contract are in conflict.
     * @param requiredContractClassName The class name of the contract to which the state belongs.
     */
    @KeepForDJVM
    class TransactionContractConflictException(txId: SecureHash, state: TransactionState<ContractState>, requiredContractClassName: String)
        : TransactionVerificationException(txId,
            """
            State of class ${state.data::class.java.typeName} belongs to contract $requiredContractClassName, but
            is bundled in TransactionState with ${state.contract}.

            For details see: https://docs.corda.net/api-contract-constraints.html#contract-state-agreement
            """.trimIndent().replace('\n', ' '), null)

    // TODO: add reference to documentation
    @KeepForDJVM
    class TransactionRequiredContractUnspecifiedException(txId: SecureHash, state: TransactionState<ContractState>)
        : TransactionVerificationException(txId,
            """
            State of class ${state.data::class.java.typeName} does not have a specified owning contract.
            Add the @BelongsToContract annotation to this class to ensure that it can only be bundled in a TransactionState
            with the correct contract.

            For details see: https://docs.corda.net/api-contract-constraints.html#contract-state-agreement
            """.trimIndent(), null)

    /** Whether the inputs or outputs list contains an encumbrance issue, see [TransactionMissingEncumbranceException]. */
    @CordaSerializable
    @KeepForDJVM
    enum class Direction {
        /** Issue in the inputs list. */
        INPUT,
        /** Issue in the outputs list. */
        OUTPUT
    }

    // We could revisit and throw this more appropriate type in a future release that uses targetVersion to
    // avoid the compatibility break, because IllegalStateException isn't ideal for this. Or we could use this
    // as a cause.
    /** @suppress This class is not used: duplicate inputs throw a [IllegalStateException] instead. */
    @Deprecated("This class is not used: duplicate inputs throw a [IllegalStateException] instead.")
    @DeleteForDJVM
    class DuplicateInputStates(txId: SecureHash, val duplicates: NonEmptySet<StateRef>)
        : TransactionVerificationException(txId, "Duplicate inputs: ${duplicates.joinToString()}", null)

    /** @suppress This class is obsolete and nothing has ever used it. */
    @Deprecated("This class is obsolete and nothing has ever used it.")
    @DeleteForDJVM
    class MoreThanOneNotary(txId: SecureHash) : TransactionVerificationException(txId, "More than one notary", null)

    /** @suppress This class is obsolete and nothing has ever used it. */
    @Deprecated("This class is obsolete and nothing has ever used it.")
    @DeleteForDJVM
    class SignersMissing(txId: SecureHash, val missing: List<PublicKey>) : TransactionVerificationException(txId, "Signers missing: ${missing.joinToString()}", null)

    /** @suppress This class is obsolete and nothing has ever used it. */
    @Deprecated("This class is obsolete and nothing has ever used it.")
    @DeleteForDJVM
    class InvalidNotaryChange(txId: SecureHash)
        : TransactionVerificationException(txId, "Detected a notary change. Outputs must use the same notary as inputs", null)

    /**
     * Thrown to indicate that a contract attachment is not signed by the network-wide package owner.
     */
    class ContractAttachmentNotSignedByPackageOwnerException(txId: SecureHash, val attachmentHash: AttachmentId, val contractClass: String) : TransactionVerificationException(txId,
            """The Contract attachment JAR: $attachmentHash containing the contract: $contractClass is not signed by the owner specified in the network parameters.
           Please check the source of this attachment and if it is malicious contact your zone operator to report this incident.
           For details see: https://docs.corda.net/network-map.html#network-parameters""".trimIndent(), null)

    /**
     * Thrown when multiple attachments provide the same file when building the AttachmentsClassloader for a transaction.
     */
    @CordaSerializable
    @KeepForDJVM
    class OverlappingAttachmentsException(path: String) : Exception("Multiple attachments define a file at path `$path`.")

    @KeepForDJVM
    class TransactionVerificationVersionException(txId: SecureHash, contractClassName: ContractClassName, inputVersion: String, outputVersion: String)
        : TransactionVerificationException(txId, " No-Downgrade Rule has been breached for contract class $contractClassName. " +
            "The output state contract version '$outputVersion' is lower that the version of the input state '$inputVersion'.", null)
}

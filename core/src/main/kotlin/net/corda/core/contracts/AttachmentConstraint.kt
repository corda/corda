package net.corda.core.contracts

import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.CordaSerializable

/** Constrain which contract-code-containing attachment can be used with a [ContractState]. */
@CordaSerializable
interface AttachmentConstraint {
    /** Returns whether the given contract attachment can be used with the [ContractState] associated with this constraint object. */
    fun isSatisfiedBy(attachment: Attachment): Boolean
}

/** An [AttachmentConstraint] where [isSatisfiedBy] always returns true. */
object AlwaysAcceptAttachmentConstraint : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment) = true
}

/** An [AttachmentConstraint] that verifies by hash */
data class HashAttachmentConstraint(val attachmentId: SecureHash) : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment) = attachment.id == attachmentId
}

/**
 * This [AttachmentConstraint] is a convenience class that will be automatically resolved to a [HashAttachmentConstraint].
 * The resolution occurs in [TransactionBuilder.toWireTransaction] and uses the [TransactionState.contract] value
 * to find a corresponding loaded [Cordapp] that contains such a contract, and then uses that [Cordapp] as the
 * [Attachment].
 *
 * If, for any reason, this class is not automatically resolved the default implementation is to fail, because the
 * intent of this class is that it should be replaced by a correct [HashAttachmentConstraint] and verify against an
 * actual [Attachment].
 */
object AutomaticHashConstraint : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment): Boolean {
        throw UnsupportedOperationException("Contracts cannot be satisfied by an AutomaticHashConstraint placeholder")
    }
}
package net.corda.core.contracts

import net.corda.core.DoNotImplement
import net.corda.core.KeepForDJVM
import net.corda.core.contracts.AlwaysAcceptAttachmentConstraint.isSatisfiedBy
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.internal.AttachmentWithContext
import net.corda.core.internal.isUploaderTrusted
import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey

/** Constrain which contract-code-containing attachment can be used with a [ContractState]. */
@CordaSerializable
@DoNotImplement
interface AttachmentConstraint {
    /** Returns whether the given contract attachment can be used with the [ContractState] associated with this constraint object. */
    fun isSatisfiedBy(attachment: Attachment): Boolean
}

/** An [AttachmentConstraint] where [isSatisfiedBy] always returns true. */
@KeepForDJVM
object AlwaysAcceptAttachmentConstraint : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment) = true
}

/**
 * An [AttachmentConstraint] that verifies by hash.
 * The state protected by this constraint can only be used in a transaction created with that version of the jar.
 * And a receiving node will only accept it if a cordapp with that hash has (is) been deployed on the node.
 */
@KeepForDJVM
data class HashAttachmentConstraint(val attachmentId: SecureHash) : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment): Boolean {
        return if (attachment is AttachmentWithContext) {
            attachment.id == attachmentId && isUploaderTrusted(attachment.contractAttachment.uploader)
        } else false
    }
}

/**
 * An [AttachmentConstraint] that verifies that the hash of the attachment is in the network parameters whitelist.
 * See: [net.corda.core.node.NetworkParameters.whitelistedContractImplementations]
 * It allows for centralized control over the cordapps that can be used.
 */
@KeepForDJVM
object WhitelistedByZoneAttachmentConstraint : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment): Boolean {
        return if (attachment is AttachmentWithContext) {
            val whitelist = attachment.whitelistedContractImplementations ?: throw IllegalStateException("Unable to verify WhitelistedByZoneAttachmentConstraint - whitelist not specified")
            attachment.id in (whitelist[attachment.stateContract] ?: emptyList())
        } else false
    }
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
@KeepForDJVM
object AutomaticHashConstraint : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment): Boolean {
        throw UnsupportedOperationException("Contracts cannot be satisfied by an AutomaticHashConstraint placeholder")
    }
}

/**
 * An [AttachmentConstraint] that verifies that the attachment has signers that fulfil the provided [PublicKey].
 * See: [Signature Constraints](https://docs.corda.net/design/data-model-upgrades/signature-constraints.html)
 *
 * @param key A [PublicKey] that must be fulfilled by the owning keys of the attachment's signing parties.
 */
@KeepForDJVM
data class SignatureAttachmentConstraint(
        val key: PublicKey
) : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment): Boolean =
        key.isFulfilledBy(attachment.signers.map { it.owningKey })
}
package net.corda.core.contracts

import net.corda.core.CordaInternal
import net.corda.core.DoNotImplement
import net.corda.core.KeepForDJVM
import net.corda.core.contracts.AlwaysAcceptAttachmentConstraint.isSatisfiedBy
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.internal.AttachmentWithContext
import net.corda.core.internal.isUploaderTrusted
import net.corda.core.internal.utilities.Internable
import net.corda.core.internal.utilities.PrivateInterner
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.loggerFor
import java.lang.annotation.Inherited
import java.security.PublicKey

private val log = loggerFor<AttachmentConstraint>()

/**
 * This annotation should only be added to [Contract] classes.
 * If the annotation is present, then we assume that [Contract.verify] will ensure that the output states have an acceptable constraint.
 * If the annotation is missing, then the default - secure - constraint propagation logic is enforced by the platform.
 */
@Target(AnnotationTarget.CLASS)
@Inherited
annotation class NoConstraintPropagation

/**
 * Constrain which contract-code-containing attachment can be used with a [Contract].
 * */
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
    companion object {
        val disableHashConstraints = System.getProperty("net.corda.node.disableHashConstraints")?.toBoolean() ?: false
    }
    override fun isSatisfiedBy(attachment: Attachment): Boolean {
        return if (attachment is AttachmentWithContext) {
            log.debug("Checking attachment uploader ${attachment.contractAttachment.uploader} is trusted")
            attachment.id == attachmentId && isUploaderTrusted(attachment.contractAttachment.uploader)
        } else {
            log.warn("Hash constraint check failed: $attachmentId does not match contract attachment JAR ${attachment.id} or contract attachment JAR is untrusted")
            false
        }
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
            val whitelist = attachment.whitelistedContractImplementations
            log.debug("Checking ${attachment.contract} is in CZ whitelist $whitelist")
            attachment.id in (whitelist[attachment.contract] ?: emptyList())
        } else {
            log.warn("CZ whitelisted constraint check failed: ${attachment.id} not in CZ whitelist")
            false
        }
    }
}

@KeepForDJVM
@Deprecated(
        "The name is no longer valid as multiple constraints were added.",
        replaceWith = ReplaceWith("AutomaticPlaceholderConstraint"),
        level = DeprecationLevel.WARNING
)
object AutomaticHashConstraint : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment): Boolean {
        throw UnsupportedOperationException("Contracts cannot be satisfied by an AutomaticHashConstraint placeholder.")
    }
}

/**
 * This [AttachmentConstraint] is a convenience class that acts as a placeholder and will be automatically resolved by the platform when set
 * on an output state. It is the default constraint of all output states.
 *
 * The resolution occurs in [TransactionBuilder.toWireTransaction] and is based on the input states and the attachments.
 * If the [Contract] was not annotated with [NoConstraintPropagation], then the platform will ensure the correct constraint propagation.
 */
@KeepForDJVM
object AutomaticPlaceholderConstraint : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment): Boolean {
        throw UnsupportedOperationException("Contracts cannot be satisfied by an AutomaticPlaceholderConstraint placeholder.")
    }
}

/**
 * An [AttachmentConstraint] that verifies that the attachment has signers that fulfil the provided [PublicKey].
 * See: [Signature Constraints](https://docs.corda.net/api-contract-constraints.html#signature-constraints)
 *
 * @property key A [PublicKey] that must be fulfilled by the owning keys of the attachment's signing parties.
 */
@KeepForDJVM
data class SignatureAttachmentConstraint(val key: PublicKey) : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment): Boolean {
        log.debug("Checking signature constraints: verifying $key in contract attachment signer keys: ${attachment.signerKeys}")
        return if (!key.isFulfilledBy(attachment.signerKeys.map { it })) {
            log.warn("Untrusted signing key: expected $key. but contract attachment contains ${attachment.signerKeys}")
            false
        } else true
    }

    companion object : Internable<SignatureAttachmentConstraint> {
        @CordaInternal
        override val interner = PrivateInterner<SignatureAttachmentConstraint>()

        /**
         * Factory method to be used in preference to the constructor.
         */
        fun create(key: PublicKey) = interner.intern(SignatureAttachmentConstraint(key))
    }
}

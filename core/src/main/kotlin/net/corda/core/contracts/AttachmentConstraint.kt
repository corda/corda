package net.corda.core.contracts

import net.corda.core.serialization.CordaSerializable

/** Constrain which contract-code-containing attachments can be used with a [ContractState]. */
@CordaSerializable
interface AttachmentConstraint {
    /** Returns whether the given contract attachments can be used with the [ContractState] associated with this constraint object. */
    fun isSatisfiedBy(attachments: List<Attachment>): Boolean
}

/** An [AttachmentConstraint] where [isSatisfiedBy] always returns true. */
object AlwaysAcceptAttachmentConstraint : AttachmentConstraint {
    override fun isSatisfiedBy(attachments: List<Attachment>) = true
}

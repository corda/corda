package net.corda.core.contracts

interface AttachmentConstraint {
    /**
     * @param attachments the contract attachments of the transaction.
     * @return whether the given attachments can be used with the [ContractState] associated with this constraint object.
     */
    fun isSatisfiedBy(attachments: List<Attachment>): Boolean
}

object AlwaysAcceptAttachmentConstraint : AttachmentConstraint {
    override fun isSatisfiedBy(attachments: List<Attachment>) = true
}

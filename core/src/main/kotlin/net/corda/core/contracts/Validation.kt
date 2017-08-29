package net.corda.core.contracts

interface ExecutableAttachmentsValidator {
    /**
     * @param executableAttachments the contract attachments of the transaction.
     * @return whether the given attachments can be used with the [ContractState] associated with this validator.
     */
    fun accept(executableAttachments: List<Attachment>): Boolean
}

object AlwaysAcceptExecutableAttachmentsValidator : ExecutableAttachmentsValidator {
    override fun accept(executableAttachments: List<Attachment>) = true
}

package net.corda.core.internal

import net.corda.core.contracts.*
import net.corda.core.node.NetworkParameters

/**
 * Used only for passing to the Attachment constraint verification.
 */
class AttachmentWithContext(
        val contractAttachment: ContractAttachment,
        val contract: ContractClassName,
        /** Required for verifying [WhitelistedByZoneAttachmentConstraint] and [HashAttachmentConstraint] migration to [SignatureAttachmentConstraint] */
        val networkParameters: NetworkParameters
) : Attachment by contractAttachment {
    init {
        require(contract in contractAttachment.allContracts) {
            "This AttachmentWithContext was not initialised properly"
        }
    }
}
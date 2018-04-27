package net.corda.core.internal

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractClassName
import net.corda.core.node.services.AttachmentId

/**
 * Used only for passing to the Attachment constraint verification.
 */
class AttachmentWithContext(
        val contractAttachment: ContractAttachment,
        val stateContract: ContractClassName,
        /** Required for verifying [WhitelistedByZoneAttachmentConstraint] */
        val whitelistedContractImplementations: Map<String, List<AttachmentId>>?
) : Attachment by contractAttachment {
    init {
        require(stateContract in contractAttachment.allContracts) {
            "This AttachmentWithContext was not initialised properly"
        }
    }

    override fun toString(): String {
        return "ContractAttachment: $contractAttachment, stateContract: $stateContract, whitelistedContractImplementations: $whitelistedContractImplementations"
    }
}
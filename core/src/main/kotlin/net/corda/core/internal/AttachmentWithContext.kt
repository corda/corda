/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
}
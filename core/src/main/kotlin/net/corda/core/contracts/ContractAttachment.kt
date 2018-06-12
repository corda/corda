/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.contracts

import net.corda.core.KeepForDJVM
import net.corda.core.serialization.CordaSerializable

/**
 * Wrap an attachment in this if it is to be used as an executable contract attachment
 *
 * @property attachment The attachment representing the contract JAR
 * @property contract The contract name contained within the JAR. A Contract attachment has to contain at least 1 contract.
 * @property additionalContracts Additional contract names contained within the JAR.
 */
@KeepForDJVM
@CordaSerializable
class ContractAttachment @JvmOverloads constructor(val attachment: Attachment, val contract: ContractClassName, val additionalContracts: Set<ContractClassName> = emptySet(), val uploader: String? = null) : Attachment by attachment {

    val allContracts: Set<ContractClassName> get() = additionalContracts + contract

    override fun toString(): String {
        return "ContractAttachment(attachment=${attachment.id}, contracts='$allContracts', uploader='$uploader')"
    }
}
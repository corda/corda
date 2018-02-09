package net.corda.core.contracts

import net.corda.core.serialization.CordaSerializable

/**
 * Wrap an attachment in this if it is to be used as an executable contract attachment
 *
 * @property attachment The attachment representing the contract JAR
 * @property contract The contract name contained within the JAR. A Contract attachment has to contain at least 1 contract.
 * @property additionalContracts Additional contract names contained within the JAR.
 */
@CordaSerializable
class ContractAttachment(val attachment: Attachment, val contract: ContractClassName, val additionalContracts: Set<ContractClassName> = emptySet()) : Attachment by attachment {

    constructor(attachment: Attachment, contract: ContractClassName) : this(attachment, contract, emptySet())

    val allContracts: Set<ContractClassName> = additionalContracts + contract

    override fun toString(): String {
        return "ContractAttachment(attachment=${attachment.id}, contracts='${allContracts}')"
    }
}

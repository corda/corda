package net.corda.core.contracts

import net.corda.core.serialization.CordaSerializable

/**
 * Wrap an attachment in this if it is to be used as an executable contract attachment
 *
 * @property attachment The attachment representing the contract JAR
 * @property contracts The contract names contained within the JAR
 */
@CordaSerializable
class ContractAttachment(val attachment: Attachment, val contract: ContractClassName, val contracts: Set<ContractClassName> = emptySet()) : Attachment by attachment {

    constructor(attachment: Attachment, contracts: Set<ContractClassName>) : this(attachment, contracts.first(), contracts)

    val allContracts: Set<ContractClassName> = contracts + contract

    override fun toString(): String {
        return "ContractAttachment(attachment=${attachment.id}, contracts='${allContracts}')"
    }
}

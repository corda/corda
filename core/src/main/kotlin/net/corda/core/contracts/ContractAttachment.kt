package net.corda.core.contracts

import net.corda.core.serialization.CordaSerializable

/**
 * Wrap an attachment in this if it is to be used as an executable contract attachment
 *
 * @property attachment The attachment representing the contract JAR
 * @property contracts The contract names contained within the JAR
 */
@CordaSerializable
class ContractAttachment(val attachment: Attachment, val contracts: Set<ContractClassName>) : Attachment by attachment {

    /**
     * used in tests
     */
    constructor(attachment: Attachment, contract: ContractClassName) : this(attachment, setOf(contract))

    override fun toString(): String {
        return "ContractAttachment(attachment=${attachment.id}, contracts='$contracts')"
    }
}

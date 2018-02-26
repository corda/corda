package net.corda.core.contracts

import net.corda.core.serialization.CordaSerializable

/**
 * Wrap an attachment in this if it is to be used as an executable contract attachment
 *
 * @property attachment The attachment representing the contract JAR
 * @property contract The contract name contained within the JAR
 */
@CordaSerializable
class ContractAttachment(val attachment: Attachment, val contract: ContractClassName) : Attachment by attachment

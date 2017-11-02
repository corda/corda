package net.corda.core.contracts

import net.corda.core.crypto.SecureHash
import net.corda.core.internal.AbstractAttachment
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationToken
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SerializeAsTokenContext
import java.util.*

/**
 * Wrap an attachment in this if it is to be used as an executable contract attachment
 *
 * @property id Attachment id
 * @param dataLoader Function to load attachment
 * @property contract The contract name contained within the JAR
 */
@CordaSerializable
class ContractAttachment(override val id: SecureHash, dataLoader: () -> ByteArray, val contract: ContractClassName) : AbstractAttachment(dataLoader), SerializeAsToken {

    private class Token(private val id: SecureHash, val contract: ContractClassName) : SerializationToken {
        override fun fromToken(context: SerializeAsTokenContext) = ContractAttachment(id, context.attachmentDataLoader(id), contract)
    }

    override fun toToken(context: SerializeAsTokenContext): SerializationToken {
        return Token(id, contract)
    }

    override fun equals(other: Any?) = other === this || other is ContractAttachment && other.id == this.id && other.contract == this.contract

    override fun hashCode() = super.hashCode() + 31 * Objects.hash(id, contract)
}
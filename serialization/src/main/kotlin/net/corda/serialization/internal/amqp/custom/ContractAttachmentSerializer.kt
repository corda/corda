package net.corda.serialization.internal.amqp.custom

import net.corda.core.Deterministic
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractClassName
import net.corda.core.internal.readFully
import net.corda.core.serialization.MissingAttachmentsException
import net.corda.serialization.internal.GeneratedAttachment
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory

/**
 * A serializer for [ContractAttachment] that uses a proxy object to write out the full attachment eagerly.
 * @param factory the serializerFactory
 */
class ContractAttachmentSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<ContractAttachment,
        ContractAttachmentSerializer.ContractAttachmentProxy>(ContractAttachment::class.java,
        ContractAttachmentProxy::class.java, factory) {
    override fun toProxy(obj: ContractAttachment): ContractAttachmentProxy {
        val bytes = try {
            obj.attachment.open().readFully()
        } catch (e: Exception) {
            throw MissingAttachmentsException(listOf(obj.id))
        }
        return ContractAttachmentProxy(GeneratedAttachment(bytes), obj.contract, obj.additionalContracts, obj.uploader)
    }

    override fun fromProxy(proxy: ContractAttachmentProxy): ContractAttachment {
        return ContractAttachment(proxy.attachment, proxy.contract, proxy.contracts, proxy.uploader)
    }

    @Deterministic
    data class ContractAttachmentProxy(val attachment: Attachment, val contract: ContractClassName, val contracts: Set<ContractClassName>, val uploader: String?)
}
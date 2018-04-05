/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractClassName
import net.corda.core.internal.readFully
import net.corda.core.serialization.MissingAttachmentsException
import net.corda.nodeapi.internal.serialization.GeneratedAttachment
import net.corda.nodeapi.internal.serialization.amqp.CustomSerializer
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory

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

    data class ContractAttachmentProxy(val attachment: Attachment, val contract: ContractClassName, val contracts: Set<ContractClassName>, val uploader: String?)
}
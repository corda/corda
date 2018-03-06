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

import net.corda.nodeapi.internal.serialization.amqp.CustomSerializer
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import java.time.*

/**
 * A serializer for [OffsetDateTime] that uses a proxy object to write out the date and zone offset.
 */
class OffsetDateTimeSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<OffsetDateTime, OffsetDateTimeSerializer.OffsetDateTimeProxy>(OffsetDateTime::class.java, OffsetDateTimeProxy::class.java, factory) {
    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = listOf(LocalDateTimeSerializer(factory), ZoneIdSerializer(factory))

    override fun toProxy(obj: OffsetDateTime): OffsetDateTimeProxy = OffsetDateTimeProxy(obj.toLocalDateTime(), obj.offset)

    override fun fromProxy(proxy: OffsetDateTimeProxy): OffsetDateTime = OffsetDateTime.of(proxy.dateTime, proxy.offset)

    data class OffsetDateTimeProxy(val dateTime: LocalDateTime, val offset: ZoneOffset)
}
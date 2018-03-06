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
 * A serializer for [OffsetTime] that uses a proxy object to write out the time and zone offset.
 */
class OffsetTimeSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<OffsetTime, OffsetTimeSerializer.OffsetTimeProxy>(OffsetTime::class.java, OffsetTimeProxy::class.java, factory) {
    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = listOf(LocalTimeSerializer(factory), ZoneIdSerializer(factory))

    override fun toProxy(obj: OffsetTime): OffsetTimeProxy = OffsetTimeProxy(obj.toLocalTime(), obj.offset)

    override fun fromProxy(proxy: OffsetTimeProxy): OffsetTime = OffsetTime.of(proxy.time, proxy.offset)

    data class OffsetTimeProxy(val time: LocalTime, val offset: ZoneOffset)
}
/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.serialization.internal.amqp.custom

import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import java.lang.reflect.Method
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * A serializer for [ZonedDateTime] that uses a proxy object to write out the date, time, offset and zone.
 */
class ZonedDateTimeSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<ZonedDateTime, ZonedDateTimeSerializer.ZonedDateTimeProxy>(ZonedDateTime::class.java, ZonedDateTimeProxy::class.java, factory) {
    // Java deserialization of `ZonedDateTime` uses a private method.  We will resolve this somewhat statically
    // so that any change to internals of `ZonedDateTime` is detected early.
    companion object {
        val ofLenient: Method = ZonedDateTime::class.java.getDeclaredMethod("ofLenient", LocalDateTime::class.java, ZoneOffset::class.java, ZoneId::class.java)

        init {
            ofLenient.isAccessible = true
        }
    }

    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = listOf(LocalDateTimeSerializer(factory), ZoneIdSerializer(factory))

    override fun toProxy(obj: ZonedDateTime): ZonedDateTimeProxy = ZonedDateTimeProxy(obj.toLocalDateTime(), obj.offset, obj.zone)

    override fun fromProxy(proxy: ZonedDateTimeProxy): ZonedDateTime = ofLenient.invoke(null, proxy.dateTime, proxy.offset, proxy.zone) as ZonedDateTime

    data class ZonedDateTimeProxy(val dateTime: LocalDateTime, val offset: ZoneOffset, val zone: ZoneId)
}
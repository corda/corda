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

import net.corda.core.KeepForDJVM
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A serializer for [LocalDateTime] that uses a proxy object to write out the date and time.
 */
class LocalDateTimeSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<LocalDateTime, LocalDateTimeSerializer.LocalDateTimeProxy>(LocalDateTime::class.java, LocalDateTimeProxy::class.java, factory) {
    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = listOf(LocalDateSerializer(factory), LocalTimeSerializer(factory))

    override fun toProxy(obj: LocalDateTime): LocalDateTimeProxy = LocalDateTimeProxy(obj.toLocalDate(), obj.toLocalTime())

    override fun fromProxy(proxy: LocalDateTimeProxy): LocalDateTime = LocalDateTime.of(proxy.date, proxy.time)

    @KeepForDJVM
    data class LocalDateTimeProxy(val date: LocalDate, val time: LocalTime)
}
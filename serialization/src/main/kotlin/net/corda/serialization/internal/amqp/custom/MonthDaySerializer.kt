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
import java.time.MonthDay

/**
 * A serializer for [MonthDay] that uses a proxy object to write out the integer form.
 */
class MonthDaySerializer(factory: SerializerFactory)
    : CustomSerializer.Proxy<MonthDay, MonthDaySerializer.MonthDayProxy>(
        MonthDay::class.java, MonthDayProxy::class.java, factory
) {
    override fun toProxy(obj: MonthDay): MonthDayProxy = MonthDayProxy(obj.monthValue.toByte(), obj.dayOfMonth.toByte())

    override fun fromProxy(proxy: MonthDayProxy): MonthDay = MonthDay.of(proxy.month.toInt(), proxy.day.toInt())

    data class MonthDayProxy(val month: Byte, val day: Byte)
}
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
 * A serializer for [YearMonth] that uses a proxy object to write out the integer form.
 */
class YearMonthSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<YearMonth, YearMonthSerializer.YearMonthProxy>(YearMonth::class.java, YearMonthProxy::class.java, factory) {
    override fun toProxy(obj: YearMonth): YearMonthProxy = YearMonthProxy(obj.year, obj.monthValue.toByte())

    override fun fromProxy(proxy: YearMonthProxy): YearMonth = YearMonth.of(proxy.year, proxy.month.toInt())

    data class YearMonthProxy(val year: Int, val month: Byte)
}
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
 * A serializer for [Period] that uses a proxy object to write out the integer form.
 */
class PeriodSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<Period, PeriodSerializer.PeriodProxy>(Period::class.java, PeriodProxy::class.java, factory) {
    override fun toProxy(obj: Period): PeriodProxy = PeriodProxy(obj.years, obj.months, obj.days)

    override fun fromProxy(proxy: PeriodProxy): Period = Period.of(proxy.years, proxy.months, proxy.days)

    data class PeriodProxy(val years: Int, val months: Int, val days: Int)
}
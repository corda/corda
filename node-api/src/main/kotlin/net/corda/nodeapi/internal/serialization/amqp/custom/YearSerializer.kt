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
import java.time.Year

/**
 * A serializer for [Year] that uses a proxy object to write out the integer form.
 */
class YearSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<Year, YearSerializer.YearProxy>(Year::class.java, YearProxy::class.java, factory) {
    override fun toProxy(obj: Year): YearProxy = YearProxy(obj.value)

    override fun fromProxy(proxy: YearProxy): Year = Year.of(proxy.year)

    data class YearProxy(val year: Int)
}
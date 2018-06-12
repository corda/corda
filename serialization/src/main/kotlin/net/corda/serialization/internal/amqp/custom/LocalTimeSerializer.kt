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
import java.time.LocalTime

/**
 * A serializer for [LocalTime] that uses a proxy object to write out the hours, minutes, seconds and the nanos.
 */
class LocalTimeSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<LocalTime, LocalTimeSerializer.LocalTimeProxy>(LocalTime::class.java, LocalTimeProxy::class.java, factory) {
    override fun toProxy(obj: LocalTime): LocalTimeProxy = LocalTimeProxy(obj.hour.toByte(), obj.minute.toByte(), obj.second.toByte(), obj.nano)

    override fun fromProxy(proxy: LocalTimeProxy): LocalTime = LocalTime.of(proxy.hour.toInt(), proxy.minute.toInt(), proxy.second.toInt(), proxy.nano)

    @KeepForDJVM
    data class LocalTimeProxy(val hour: Byte, val minute: Byte, val second: Byte, val nano: Int)
}
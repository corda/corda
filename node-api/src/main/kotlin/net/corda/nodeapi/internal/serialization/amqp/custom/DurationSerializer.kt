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
import java.time.Duration

/**
 * A serializer for [Duration] that uses a proxy object to write out the seconds and the nanos.
 */
class DurationSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<Duration, DurationSerializer.DurationProxy>(Duration::class.java, DurationProxy::class.java, factory) {
    override fun toProxy(obj: Duration): DurationProxy = DurationProxy(obj.seconds, obj.nano)

    override fun fromProxy(proxy: DurationProxy): Duration = Duration.ofSeconds(proxy.seconds, proxy.nanos.toLong())

    data class DurationProxy(val seconds: Long, val nanos: Int)
}
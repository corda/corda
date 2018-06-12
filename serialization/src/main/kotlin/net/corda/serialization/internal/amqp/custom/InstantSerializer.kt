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
import java.time.Instant

/**
 * A serializer for [Instant] that uses a proxy object to write out the seconds since the epoch and the nanos.
 */
class InstantSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<Instant, InstantSerializer.InstantProxy>(Instant::class.java, InstantProxy::class.java, factory) {
    override fun toProxy(obj: Instant): InstantProxy = InstantProxy(obj.epochSecond, obj.nano)

    override fun fromProxy(proxy: InstantProxy): Instant = Instant.ofEpochSecond(proxy.epochSeconds, proxy.nanos.toLong())

    @KeepForDJVM
    data class InstantProxy(val epochSeconds: Long, val nanos: Int)
}
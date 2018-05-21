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
import java.time.ZoneId

/**
 * A serializer for [ZoneId] that uses a proxy object to write out the string form.
 */
class ZoneIdSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<ZoneId, ZoneIdSerializer.ZoneIdProxy>(ZoneId::class.java, ZoneIdProxy::class.java, factory) {
    override val revealSubclassesInSchema: Boolean = true

    override fun toProxy(obj: ZoneId): ZoneIdProxy = ZoneIdProxy(obj.id)

    override fun fromProxy(proxy: ZoneIdProxy): ZoneId = ZoneId.of(proxy.id)

    data class ZoneIdProxy(val id: String)
}
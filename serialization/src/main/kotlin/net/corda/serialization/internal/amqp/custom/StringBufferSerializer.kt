package net.corda.serialization.internal.amqp.custom

import net.corda.serialization.internal.amqp.CustomSerializer

/**
 * A serializer for [StringBuffer].
 */
object StringBufferSerializer : CustomSerializer.ToString<StringBuffer>(StringBuffer::class.java)
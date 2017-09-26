package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.nodeapi.internal.serialization.amqp.CustomSerializer

/**
 * A serializer for [StringBuffer].
 */
object StringBufferSerializer : CustomSerializer.ToString<StringBuffer>(StringBuffer::class.java)
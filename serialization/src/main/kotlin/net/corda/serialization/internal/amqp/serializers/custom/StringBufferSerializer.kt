package net.corda.serialization.internal.amqp.serializers.custom

import net.corda.serialization.internal.amqp.serializers.CustomSerializer

/**
 * A serializer for [StringBuffer].
 */
object StringBufferSerializer : CustomSerializer.ToString<StringBuffer>(StringBuffer::class.java)
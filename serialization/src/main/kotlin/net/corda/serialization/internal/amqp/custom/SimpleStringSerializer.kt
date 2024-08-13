package net.corda.serialization.internal.amqp.custom

import net.corda.serialization.internal.amqp.CustomSerializer
import org.apache.activemq.artemis.api.core.SimpleString

/**
 * A serializer for [SimpleString].
 */
object SimpleStringSerializer : CustomSerializer.ToString<SimpleString>(SimpleString::class.java)
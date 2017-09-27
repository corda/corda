package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.nodeapi.internal.serialization.amqp.CustomSerializer
import org.apache.activemq.artemis.api.core.SimpleString

/**
 * A serializer for [SimpleString].
 */
object SimpleStringSerializer : CustomSerializer.ToString<SimpleString>(SimpleString::class.java)
package net.corda.serialization.internal.amqp.custom

import net.corda.core.NonDeterministic
import net.corda.serialization.internal.amqp.CustomSerializer
import org.apache.activemq.artemis.api.core.SimpleString

/**
 * A serializer for [SimpleString].
 */
@NonDeterministic
object SimpleStringSerializer : CustomSerializer.ToString<SimpleString>(SimpleString::class.java)
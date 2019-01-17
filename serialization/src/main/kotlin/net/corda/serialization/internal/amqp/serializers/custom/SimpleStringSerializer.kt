package net.corda.serialization.internal.amqp.serializers.custom

import net.corda.core.DeleteForDJVM
import net.corda.serialization.internal.amqp.serializers.CustomSerializer
import org.apache.activemq.artemis.api.core.SimpleString

/**
 * A serializer for [SimpleString].
 */
@DeleteForDJVM
object SimpleStringSerializer : CustomSerializer.ToString<SimpleString>(SimpleString::class.java)
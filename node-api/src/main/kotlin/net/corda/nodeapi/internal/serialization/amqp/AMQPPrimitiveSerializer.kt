/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.serialization.SerializationContext
import org.apache.qpid.proton.amqp.Binary
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

/**
 * Serializer / deserializer for native AMQP types (Int, Float, String etc).
 *
 * [ByteArray] is automatically marshalled to/from the Proton-J wrapper, [Binary].
 */
class AMQPPrimitiveSerializer(clazz: Class<*>) : AMQPSerializer<Any> {
    override val typeDescriptor = Symbol.valueOf(SerializerFactory.primitiveTypeName(clazz)!!)
    override val type: Type = clazz

    // NOOP since this is a primitive type.
    override fun writeClassInfo(output: SerializationOutput) {
    }

    override fun writeObject(
            obj: Any,
            data: Data,
            type: Type,
            output: SerializationOutput,
            context: SerializationContext,
            debugIndent: Int
    ) {
        if (obj is ByteArray) {
            data.putObject(Binary(obj))
        } else {
            data.putObject(obj)
        }
    }

    override fun readObject(
            obj: Any,
            schemas: SerializationSchemas,
            input: DeserializationInput,
            context: SerializationContext): Any = (obj as? Binary)?.array ?: obj
}
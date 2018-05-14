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
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

/**
 * A custom serializer that transports nothing on the wire (except a boolean "false", since AMQP does not support
 * absolutely nothing, or null as a described type) when we have a singleton within the node that we just
 * want converting back to that singleton instance on the receiving JVM.
 */
class SingletonSerializer(override val type: Class<*>, val singleton: Any, factory: SerializerFactory) : AMQPSerializer<Any> {
    override val typeDescriptor = Symbol.valueOf(
            "$DESCRIPTOR_DOMAIN:${factory.fingerPrinter.fingerprint(type)}")!!

    private val interfaces = interfacesForSerialization(type, factory)

    private fun generateProvides(): List<String> = interfaces.map { it.typeName }

    internal val typeNotation: TypeNotation = RestrictedType(type.typeName, "Singleton", generateProvides(), "boolean", Descriptor(typeDescriptor), emptyList())

    override fun writeClassInfo(output: SerializationOutput) {
        output.writeTypeNotations(typeNotation)
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        data.withDescribed(typeNotation.descriptor) {
            data.putBoolean(false)
        }
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext
    ): Any {
        return singleton
    }
}
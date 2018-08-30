/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.serialization.internal.amqp

import net.corda.core.serialization.SerializationContext
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.Type

/**
 * Our definition of an enum with the AMQP spec is a list (of two items, a string and an int) that is
 * a restricted type with a number of choices associated with it
 */
class EnumSerializer(declaredType: Type, declaredClass: Class<*>, factory: SerializerFactory) : AMQPSerializer<Any> {
    override val type: Type = declaredType
    private val typeNotation: TypeNotation
    override val typeDescriptor = Symbol.valueOf(
            "$DESCRIPTOR_DOMAIN:${factory.fingerPrinter.fingerprint(type)}")!!

    init {
        typeNotation = RestrictedType(
                SerializerFactory.nameForType(declaredType),
                null, emptyList(), "list", Descriptor(typeDescriptor),
                declaredClass.enumConstants.zip(IntRange(0, declaredClass.enumConstants.size)).map {
                    Choice(it.first.toString(), it.second.toString())
                })
    }

    override fun writeClassInfo(output: SerializationOutput) {
        output.writeTypeNotations(typeNotation)
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput,
                            context: SerializationContext
    ): Any {
        val enumName = (obj as List<*>)[0] as String
        val enumOrd = obj[1] as Int
        val fromOrd = type.asClass().enumConstants[enumOrd] as Enum<*>?

        if (enumName != fromOrd?.name) {
            throw AMQPNotSerializableException(
                    type,
                    "Deserializing obj as enum $type with value $enumName.$enumOrd but ordinality has changed")
        }
        return fromOrd
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        if (obj !is Enum<*>) throw AMQPNotSerializableException(type, "Serializing $obj as enum when it isn't")

        data.withDescribed(typeNotation.descriptor) {
            withList {
                data.putString(obj.name)
                data.putInt(obj.ordinal)
            }
        }
    }
}
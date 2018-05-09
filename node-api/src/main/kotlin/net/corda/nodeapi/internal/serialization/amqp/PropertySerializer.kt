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
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

/**
 * Base class for serialization of a property of an object.
 */
sealed class PropertySerializer(val name: String, val propertyReader: PropertyReader, val resolvedType: Type) {
    abstract fun writeClassInfo(output: SerializationOutput)
    abstract fun writeProperty(obj: Any?, data: Data, output: SerializationOutput, context: SerializationContext, debugIndent: Int = 0)
    abstract fun readProperty(obj: Any?, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any?

    val type: String = generateType()
    val requires: List<String> = generateRequires()
    val default: String? = generateDefault()
    val mandatory: Boolean = generateMandatory()

    private val isInterface: Boolean get() = resolvedType.asClass()?.isInterface == true
    private val isJVMPrimitive: Boolean get() = resolvedType.asClass()?.isPrimitive == true

    private fun generateType(): String {
        return if (isInterface || resolvedType == Any::class.java) "*" else SerializerFactory.nameForType(resolvedType)
    }

    private fun generateRequires(): List<String> {
        return if (isInterface) listOf(SerializerFactory.nameForType(resolvedType)) else emptyList()
    }

    private fun generateDefault(): String? =
            if (isJVMPrimitive) {
                when (resolvedType) {
                    java.lang.Boolean.TYPE -> "false"
                    java.lang.Character.TYPE -> "&#0"
                    else -> "0"
                }
            } else {
                null
            }

    private fun generateMandatory(): Boolean {
        return isJVMPrimitive || !(propertyReader.isNullable())
    }

    companion object {
        fun make(name: String, readMethod: PropertyReader, resolvedType: Type, factory: SerializerFactory): PropertySerializer {
            return if (SerializerFactory.isPrimitive(resolvedType)) {
                when (resolvedType) {
                    Char::class.java, Character::class.java -> AMQPCharPropertySerializer(name, readMethod)
                    else -> AMQPPrimitivePropertySerializer(name, readMethod, resolvedType)
                }
            } else {
                DescribedTypePropertySerializer(name, readMethod, resolvedType) { factory.get(null, resolvedType) }
            }
        }
    }

    /**
     * A property serializer for a complex type (another object).
     */
    class DescribedTypePropertySerializer(
            name: String,
            readMethod: PropertyReader,
            resolvedType: Type,
            private val lazyTypeSerializer: () -> AMQPSerializer<*>) : PropertySerializer(name, readMethod, resolvedType) {
        // This is lazy so we don't get an infinite loop when a method returns an instance of the class.
        private val typeSerializer: AMQPSerializer<*> by lazy { lazyTypeSerializer() }

        override fun writeClassInfo(output: SerializationOutput) = ifThrowsAppend({ nameForDebug }) {
            if (resolvedType != Any::class.java) {
                typeSerializer.writeClassInfo(output)
            }
        }

        override fun readProperty(
                obj: Any?,
                schemas: SerializationSchemas,
                input: DeserializationInput,
                context: SerializationContext): Any? = ifThrowsAppend({ nameForDebug }) {
            input.readObjectOrNull(obj, schemas, resolvedType, context)
        }

        override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput,
                                   context: SerializationContext, debugIndent: Int) = ifThrowsAppend({ nameForDebug }
        ) {
            output.writeObjectOrNull(propertyReader.read(obj), data, resolvedType, context, debugIndent)
        }

        private val nameForDebug = "$name(${resolvedType.typeName})"
    }

    /**
     * A property serializer for most AMQP primitive type (Int, String, etc).
     */
    class AMQPPrimitivePropertySerializer(
            name: String,
            readMethod: PropertyReader,
            resolvedType: Type) : PropertySerializer(name, readMethod, resolvedType) {
        override fun writeClassInfo(output: SerializationOutput) {}

        override fun readProperty(obj: Any?, schemas: SerializationSchemas,
                                  input: DeserializationInput, context: SerializationContext
        ): Any? {
            return if (obj is Binary) obj.array else obj
        }

        override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput,
                                   context: SerializationContext, debugIndent: Int
        ) {
            val value = propertyReader.read(obj)
            if (value is ByteArray) {
                data.putObject(Binary(value))
            } else {
                data.putObject(value)
            }
        }
    }

    /**
     * A property serializer for the AMQP char type, needed as a specialisation as the underlying
     * value of the character is stored in numeric UTF-16 form and on deserialization requires explicit
     * casting back to a char otherwise it's treated as an Integer and a TypeMismatch occurs
     */
    class AMQPCharPropertySerializer(name: String, readMethod: PropertyReader) :
            PropertySerializer(name, readMethod, Character::class.java) {
        override fun writeClassInfo(output: SerializationOutput) {}

        override fun readProperty(obj: Any?, schemas: SerializationSchemas,
                                  input: DeserializationInput, context: SerializationContext
        ): Any? {
            return if (obj == null) null else (obj as Short).toChar()
        }

        override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput,
                                   context: SerializationContext, debugIndent: Int
        ) {
            val input = propertyReader.read(obj)
            if (input != null) data.putShort((input as Char).toShort()) else data.putNull()
        }
    }
}


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
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.serialization.internal.amqp.SerializerFactory.Companion.nameForType
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Type
import kotlin.reflect.jvm.javaConstructor

/**
 * Responsible for serializing and deserializing a regular object instance via a series of properties
 * (matched with a constructor).
 */
open class ObjectSerializer(val clazz: Type, factory: SerializerFactory) : AMQPSerializer<Any> {
    override val type: Type get() = clazz
    open val kotlinConstructor = constructorForDeserialization(clazz)
    val javaConstructor by lazy { kotlinConstructor?.javaConstructor }

    companion object {
        private val logger = contextLogger()
    }

    open val propertySerializers: PropertySerializers by lazy {
        propertiesForSerialization(kotlinConstructor, clazz, factory)
    }

    private val typeName = nameForType(clazz)

    override val typeDescriptor: Symbol = Symbol.valueOf("$DESCRIPTOR_DOMAIN:${factory.fingerPrinter.fingerprint(type)}")

    // We restrict to only those annotated or whitelisted
    private val interfaces = interfacesForSerialization(clazz, factory)

    internal open val typeNotation: TypeNotation by lazy {
        CompositeType(typeName, null, generateProvides(), Descriptor(typeDescriptor), generateFields())
    }

    override fun writeClassInfo(output: SerializationOutput) {
        if (output.writeTypeNotations(typeNotation)) {
            for (iface in interfaces) {
                output.requireSerializer(iface)
            }

            propertySerializers.serializationOrder.forEach { property ->
                property.serializer.writeClassInfo(output)
            }
        }
    }

    override fun writeObject(
            obj: Any,
            data: Data,
            type: Type,
            output: SerializationOutput,
            context: SerializationContext,
            debugIndent: Int) = ifThrowsAppend({ clazz.typeName }
    ) {
        if (propertySerializers.size != javaConstructor?.parameterCount &&
                javaConstructor?.parameterCount ?: 0 > 0
        ) {
            throw AMQPNotSerializableException(type, "Serialization constructor for class $type expects "
                    + "${javaConstructor?.parameterCount} parameters but we have ${propertySerializers.size} "
                    + "properties to serialize.")
        }

        // Write described
        data.withDescribed(typeNotation.descriptor) {
            // Write list
            withList {
                propertySerializers.serializationOrder.forEach { property ->
                    property.serializer.writeProperty(obj, this, output, context, debugIndent + 1)
                }
            }
        }
    }

    override fun readObject(
            obj: Any,
            schemas: SerializationSchemas,
            input: DeserializationInput,
            context: SerializationContext): Any = ifThrowsAppend({ clazz.typeName }) {
        if (obj is List<*>) {
            if (obj.size > propertySerializers.size) {
                throw AMQPNotSerializableException(type, "Too many properties in described type $typeName")
            }

            return if (propertySerializers.byConstructor) {
                readObjectBuildViaConstructor(obj, schemas, input, context)
            } else {
                readObjectBuildViaSetters(obj, schemas, input, context)
            }
        } else {
            throw AMQPNotSerializableException(type, "Body of described type is unexpected $obj")
        }
    }

    private fun readObjectBuildViaConstructor(
            obj: List<*>,
            schemas: SerializationSchemas,
            input: DeserializationInput,
            context: SerializationContext): Any = ifThrowsAppend({ clazz.typeName }) {
        logger.trace { "Calling construction based construction for ${clazz.typeName}" }

        return construct(propertySerializers.serializationOrder
                .zip(obj)
                .map { Pair(it.first.initialPosition, it.first.serializer.readProperty(it.second, schemas, input, context)) }
                .sortedWith(compareBy({ it.first }))
                .map { it.second })
    }

    private fun readObjectBuildViaSetters(
            obj: List<*>,
            schemas: SerializationSchemas,
            input: DeserializationInput,
            context: SerializationContext): Any = ifThrowsAppend({ clazz.typeName }) {
        logger.trace { "Calling setter based construction for ${clazz.typeName}" }

        val instance: Any = javaConstructor?.newInstanceUnwrapped() ?: throw AMQPNotSerializableException(
                type,
                "Failed to instantiate instance of object $clazz")

        // read the properties out of the serialised form, since we're invoking the setters the order we
        // do it in doesn't matter
        val propertiesFromBlob = obj
                .zip(propertySerializers.serializationOrder)
                .map { it.second.serializer.readProperty(it.first, schemas, input, context) }

        // one by one take a property and invoke the setter on the class
        propertySerializers.serializationOrder.zip(propertiesFromBlob).forEach {
            it.first.set(instance, it.second)
        }

        return instance
    }

    private fun generateFields(): List<Field> {
        return propertySerializers.serializationOrder.map {
            Field(it.serializer.name, it.serializer.type, it.serializer.requires, it.serializer.default, null, it.serializer.mandatory, false)
        }
    }

    private fun generateProvides(): List<String> = interfaces.map { nameForType(it) }

    fun construct(properties: List<Any?>): Any {
        logger.trace { "Calling constructor: '$javaConstructor' with properties '$properties'" }

        if (properties.size != javaConstructor?.parameterCount) {
            throw AMQPNotSerializableException(type, "Serialization constructor for class $type expects "
                    + "${javaConstructor?.parameterCount} parameters but we have ${properties.size} "
                    + "serialized properties.")
        }

        return javaConstructor?.newInstanceUnwrapped(*properties.toTypedArray())
                ?: throw AMQPNotSerializableException(
                        type,
                        "Attempt to deserialize an interface: $clazz. Serialized form is invalid.")
    }

    private fun <T> Constructor<T>.newInstanceUnwrapped(vararg args: Any?): T {
        try {
            return newInstance(*args)
        } catch (e: InvocationTargetException) {
            throw e.cause!!
        }
    }
}
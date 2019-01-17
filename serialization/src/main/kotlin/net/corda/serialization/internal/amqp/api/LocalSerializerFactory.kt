package net.corda.serialization.internal.amqp.api

import net.corda.core.internal.reflection.LocalTypeInformation
import net.corda.core.serialization.ClassWhitelist
import org.apache.qpid.proton.amqp.Symbol
import java.lang.reflect.Type

/**
 * A factory that handles the serialisation and deserialisation of [Type]s visible from a given [ClassLoader].
 *
 * Unlike the [RemoteSerializerFactory], which deals with types for which we have [Schema] information and serialised data,
 * the [LocalSerializerFactory] deals with types for which we have a Java [Type] (and perhaps some in-memory data, from which
 * we can discover the actual [Class] we are working with.
 */
interface LocalSerializerFactory {
    /**
     * The [ClassWhitelist] used by this factory. Classes must be whitelisted for serialization, because they are expected
     * to be written in a secure manner.
     */
    val whitelist: ClassWhitelist

    /**
     * The [ClassLoader] used by this factory.
     */
    val classloader: ClassLoader

    /**
     * Obtain an [AMQPSerializer] for an object of actual type [actualClass], and declared type [declaredType].
     */
    fun get(actualClass: Class<*>, declaredType: Type): AMQPSerializer<Any>

    /**
     * Obtain an [AMQPSerializer] for the [declaredType].
     */
    fun get(declaredType: Type): AMQPSerializer<Any> = get(getTypeInformation(declaredType))

    /**
     * Obtain an [AMQPSerializer] for the type having the given [typeInformation].
     */
    fun get(typeInformation: LocalTypeInformation): AMQPSerializer<Any>

    /**
     * Obtain [LocalTypeInformation] for the given [Type].
     */
    fun getTypeInformation(type: Type): LocalTypeInformation

    /**
     * Obtain [LocalTypeInformation] for the [Type] that has the given name in the [ClassLoader] associated with this factory.
     *
     * @return null if the type with the given name does not exist in the [ClassLoader] for this factory.
     */
    fun getTypeInformation(typeName: String): LocalTypeInformation?

    /**
     * Use the [FingerPrinter] to create a type descriptor for the given [type].
     */
    fun createDescriptor(type: Type): Symbol = createDescriptor(getTypeInformation(type))

    /**
     * Use the [FingerPrinter] to create a type descriptor for the given [typeInformation].
     */
    fun createDescriptor(typeInformation: LocalTypeInformation): Symbol
}
package net.corda.serialization.djvm

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationContext.UseCase
import net.corda.core.serialization.SerializedBytes
import net.corda.core.utilities.ByteSequence
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.serializers.*
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.SerializationScheme
import net.corda.serialization.internal.amqp.*
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*
import java.util.function.Function

class AMQPSerializationScheme(
    private val classLoader: SandboxClassLoader,
    private val sandboxBasicInput: Function<in Any?, out Any?>,
    private val taskFactory: Function<in Any, out Function<in Any?, out Any?>>,
    private val serializerFactoryFactory: SerializerFactoryFactory
) : SerializationScheme {

    private fun getSerializerFactory(context: SerializationContext): SerializerFactory {
        return serializerFactoryFactory.make(context).apply {
            register(SandboxBitSetSerializer(classLoader, taskFactory, this))
            register(SandboxCertPathSerializer(classLoader, taskFactory, this))
            register(SandboxDurationSerializer(classLoader, taskFactory, this))
            register(SandboxEnumSetSerializer(classLoader, taskFactory, this))
            register(SandboxInputStreamSerializer(classLoader, taskFactory))
            register(SandboxInstantSerializer(classLoader, taskFactory, this))
            register(SandboxLocalDateSerializer(classLoader, taskFactory, this))
            register(SandboxLocalDateTimeSerializer(classLoader, taskFactory, this))
            register(SandboxLocalTimeSerializer(classLoader, taskFactory, this))
            register(SandboxMonthDaySerializer(classLoader, taskFactory, this))
            register(SandboxOffsetDateTimeSerializer(classLoader, taskFactory, this))
            register(SandboxOffsetTimeSerializer(classLoader, taskFactory, this))
            register(SandboxPeriodSerializer(classLoader, taskFactory, this))
            register(SandboxYearMonthSerializer(classLoader, taskFactory, this))
            register(SandboxYearSerializer(classLoader, taskFactory, this))
            register(SandboxZonedDateTimeSerializer(classLoader, taskFactory, this))
            register(SandboxZoneIdSerializer(classLoader, taskFactory, this))
            register(SandboxOpaqueBytesSubSequenceSerializer(classLoader, taskFactory, this))
            register(SandboxOptionalSerializer(classLoader, taskFactory, this))
            register(SandboxPrimitiveSerializer(UUID::class.java, classLoader, sandboxBasicInput))
            register(SandboxPrimitiveSerializer(String::class.java, classLoader, sandboxBasicInput))
            register(SandboxPrimitiveSerializer(Byte::class.javaObjectType, classLoader, sandboxBasicInput))
            register(SandboxPrimitiveSerializer(Short::class.javaObjectType, classLoader, sandboxBasicInput))
            register(SandboxPrimitiveSerializer(Int::class.javaObjectType, classLoader, sandboxBasicInput))
            register(SandboxPrimitiveSerializer(Long::class.javaObjectType, classLoader, sandboxBasicInput))
            register(SandboxPrimitiveSerializer(Float::class.javaObjectType, classLoader, sandboxBasicInput))
            register(SandboxPrimitiveSerializer(Double::class.javaObjectType, classLoader, sandboxBasicInput))
            register(SandboxPrimitiveSerializer(Boolean::class.javaObjectType, classLoader, sandboxBasicInput))
            register(SandboxPrimitiveSerializer(Date::class.javaObjectType, classLoader, sandboxBasicInput))
            register(SandboxCharacterSerializer(classLoader, sandboxBasicInput))
            register(SandboxCollectionSerializer(classLoader, taskFactory, this))
            register(SandboxMapSerializer(classLoader, taskFactory, this))
            register(SandboxEnumSerializer(classLoader, taskFactory, this))
            register(SandboxPublicKeySerializer(classLoader, taskFactory))
            register(SandboxToStringSerializer(BigDecimal::class.java, classLoader, taskFactory, sandboxBasicInput))
            register(SandboxToStringSerializer(BigInteger::class.java, classLoader, taskFactory, sandboxBasicInput))
            register(SandboxToStringSerializer(StringBuffer::class.java, classLoader, taskFactory, sandboxBasicInput))
            register(SandboxCurrencySerializer(classLoader, taskFactory, sandboxBasicInput))
            register(SandboxX509CertificateSerializer(classLoader, taskFactory))
            register(SandboxX509CRLSerializer(classLoader, taskFactory))
            register(SandboxUnsignedLongSerializer(classLoader, taskFactory))
            register(SandboxUnsignedIntegerSerializer(classLoader, taskFactory))
            register(SandboxUnsignedShortSerializer(classLoader, taskFactory))
            register(SandboxUnsignedByteSerializer(classLoader, taskFactory))
            register(SandboxDecimal128Serializer(classLoader, taskFactory))
            register(SandboxDecimal64Serializer(classLoader, taskFactory))
            register(SandboxDecimal32Serializer(classLoader, taskFactory))
            register(SandboxSymbolSerializer(classLoader, taskFactory, sandboxBasicInput))
        }
    }

    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T {
        val serializerFactory = getSerializerFactory(context)
        return DeserializationInput(serializerFactory).deserialize(byteSequence, clazz, context)
    }

    override fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T> {
        val serializerFactory = getSerializerFactory(context)
        return SerializationOutput(serializerFactory).serialize(obj, context)
    }

    override fun canDeserializeVersion(magic: CordaSerializationMagic, target: UseCase): Boolean {
        return magic == amqpMagic && target == UseCase.P2P
    }
}

package net.corda.djvm.serialization

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationContext.UseCase
import net.corda.core.serialization.SerializedBytes
import net.corda.core.utilities.ByteSequence
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.serializers.*
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.SerializationScheme
import net.corda.serialization.internal.amqp.*
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*
import java.util.function.BiFunction
import java.util.function.Function

class AMQPSerializationScheme(
    private val classLoader: SandboxClassLoader,
    private val sandboxBasicInput: Function<in Any?, out Any?>,
    private val executor: BiFunction<in Any, in Any?, out Any?>,
    private val serializerFactoryFactory: SerializerFactoryFactory
) : SerializationScheme {

    private fun getSerializerFactory(context: SerializationContext): SerializerFactory {
        return serializerFactoryFactory.make(context).apply {
            register(SandboxBitSetSerializer(classLoader, executor, this))
            register(SandboxCertPathSerializer(classLoader, executor, this))
            register(SandboxDurationSerializer(classLoader, executor, this))
            register(SandboxEnumSetSerializer(classLoader, executor, this))
            register(SandboxInputStreamSerializer(classLoader, executor))
            register(SandboxInstantSerializer(classLoader, executor, this))
            register(SandboxLocalDateSerializer(classLoader, executor, this))
            register(SandboxLocalDateTimeSerializer(classLoader, executor, this))
            register(SandboxLocalTimeSerializer(classLoader, executor, this))
            register(SandboxMonthDaySerializer(classLoader, executor, this))
            register(SandboxOffsetDateTimeSerializer(classLoader, executor, this))
            register(SandboxOffsetTimeSerializer(classLoader, executor, this))
            register(SandboxPeriodSerializer(classLoader, executor, this))
            register(SandboxYearMonthSerializer(classLoader, executor, this))
            register(SandboxYearSerializer(classLoader, executor, this))
            register(SandboxZonedDateTimeSerializer(classLoader, executor, this))
            register(SandboxZoneIdSerializer(classLoader, executor, this))
            register(SandboxOpaqueBytesSubSequenceSerializer(classLoader, executor, this))
            register(SandboxOptionalSerializer(classLoader, executor, this))
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
            register(SandboxCollectionSerializer(classLoader, executor, this))
            register(SandboxMapSerializer(classLoader, executor, this))
            register(SandboxEnumSerializer(classLoader, executor, this))
            register(SandboxPublicKeySerializer(classLoader, executor))
            register(SandboxToStringSerializer(BigDecimal::class.java, classLoader, executor, sandboxBasicInput))
            register(SandboxToStringSerializer(BigInteger::class.java, classLoader, executor, sandboxBasicInput))
            register(SandboxToStringSerializer(StringBuffer::class.java, classLoader, executor, sandboxBasicInput))
            register(SandboxCurrencySerializer(classLoader, executor, sandboxBasicInput))
            register(SandboxX509CertificateSerializer(classLoader, executor))
            register(SandboxX509CRLSerializer(classLoader, executor))
            register(SandboxUnsignedLongSerializer(classLoader, executor))
            register(SandboxUnsignedIntegerSerializer(classLoader, executor))
            register(SandboxUnsignedShortSerializer(classLoader, executor))
            register(SandboxUnsignedByteSerializer(classLoader, executor))
            register(SandboxDecimal128Serializer(classLoader, executor))
            register(SandboxDecimal64Serializer(classLoader, executor))
            register(SandboxDecimal32Serializer(classLoader, executor))
            register(SandboxSymbolSerializer(classLoader, executor, sandboxBasicInput))
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

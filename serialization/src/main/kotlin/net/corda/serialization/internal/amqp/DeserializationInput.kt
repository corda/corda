package net.corda.serialization.internal.amqp

import net.corda.core.internal.LazyPool
import net.corda.core.internal.VisibleForTesting
import net.corda.core.serialization.AMQP_ENVELOPE_CACHE_PROPERTY
import net.corda.core.serialization.EncodingWhitelist
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializedBytes
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.trace
import net.corda.serialization.internal.ByteBufferInputStream
import net.corda.serialization.internal.CordaSerializationEncoding
import net.corda.serialization.internal.NullEncodingWhitelist
import net.corda.serialization.internal.SectionId
import net.corda.serialization.internal.encodingNotPermittedFormat
import net.corda.serialization.internal.model.TypeIdentifier
import org.apache.qpid.proton.amqp.Binary
import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.amqp.UnsignedInteger
import org.apache.qpid.proton.codec.DecoderImpl
import org.apache.qpid.proton.codec.EncoderImpl
import java.io.InputStream
import java.io.NotSerializableException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.nio.ByteBuffer

data class ObjectAndEnvelope<out T>(val obj: T, val envelope: Envelope)

/**
 * Main entry point for deserializing an AMQP encoded object.
 *
 * @param serializerFactory This is the factory for [AMQPSerializer] instances and can be shared across multiple
 * instances and threads.
 */
class DeserializationInput constructor(
        private val serializerFactory: SerializerFactory
) {
    private val objectHistory: MutableList<Any> = mutableListOf()

    companion object {
        private val logger = loggerFor<DeserializationInput>()

        @VisibleForTesting
        @Throws(AMQPNoTypeNotSerializableException::class)
        fun <T> withDataBytes(
                byteSequence: ByteSequence,
                encodingWhitelist: EncodingWhitelist,
                task: (ByteBuffer) -> T
        ): T {
            // Check that the lead bytes match expected header
            val amqpSequence = amqpMagic.consume(byteSequence)
                    ?: throw AMQPNoTypeNotSerializableException("Serialization header does not match.")
            var stream: InputStream = ByteBufferInputStream(amqpSequence)
            try {
                while (true) {
                    when (SectionId.reader.readFrom(stream)) {
                        SectionId.ENCODING -> {
                            val encoding = CordaSerializationEncoding.reader.readFrom(stream)
                            encodingWhitelist.acceptEncoding(encoding) ||
                                    throw AMQPNoTypeNotSerializableException(encodingNotPermittedFormat.format(encoding))
                            stream = encoding.wrap(stream)
                        }
                        SectionId.DATA_AND_STOP, SectionId.ALT_DATA_AND_STOP -> return task(stream.asByteBuffer())
                    }
                }
            } finally {
                stream.close()
            }
        }

        private val decoderPool = LazyPool<DecoderImpl> {
            val decoder = DecoderImpl().apply {
                register(Envelope.DESCRIPTOR, Envelope.FastPathConstructor(this))
                register(Schema.DESCRIPTOR, Schema)
                register(Descriptor.DESCRIPTOR, Descriptor)
                register(Field.DESCRIPTOR, Field)
                register(CompositeType.DESCRIPTOR, CompositeType)
                register(Choice.DESCRIPTOR, Choice)
                register(RestrictedType.DESCRIPTOR, RestrictedType)
                register(ReferencedObject.DESCRIPTOR, ReferencedObject)
                register(TransformsSchema.DESCRIPTOR, TransformsSchema)
                register(TransformTypes.DESCRIPTOR, TransformTypes)
            }
            EncoderImpl(decoder)
            decoder
        }

        @Throws(AMQPNoTypeNotSerializableException::class)
        fun getEnvelope(byteSequence: ByteSequence, encodingWhitelist: EncodingWhitelist = NullEncodingWhitelist, lazy: Boolean = false): Envelope {
            return withDataBytes(byteSequence, encodingWhitelist) { dataBytes ->
                decoderPool.reentrantRun {
                    it.byteBuffer = dataBytes
                    (it.readObject() as Envelope).apply {
                        if (!lazy) this.resolvedSchema
                    }
                }
            }
        }
    }

    @VisibleForTesting
    @Throws(AMQPNoTypeNotSerializableException::class)
    fun getEnvelope(byteSequence: ByteSequence, context: SerializationContext) = getEnvelope(byteSequence, context.encodingWhitelist)

    @Throws(
            AMQPNotSerializableException::class,
            AMQPNoTypeNotSerializableException::class)
    inline fun <reified T : Any> deserialize(bytes: SerializedBytes<T>, context: SerializationContext): T =
            deserialize(bytes, T::class.java, context)

    @Throws(
            AMQPNotSerializableException::class,
            AMQPNoTypeNotSerializableException::class)
    private fun <R> des(generator: () -> R): R {
        try {
            return generator()
        } catch (amqp : AMQPNotSerializableException) {
            amqp.log("Deserialize", logger)
            throw NotSerializableException(amqp.mitigation)
        } catch (nse: NotSerializableException) {
            throw nse
        } catch (e: Exception) {
            throw NotSerializableException("Internal deserialization failure: ${e.javaClass.name}: ${e.message}").apply { initCause(e) }
        } finally {
            objectHistory.clear()
        }
    }

    /**
     * This is the main entry point for deserialization of AMQP payloads, and expects a byte sequence involving a header
     * indicating what version of Corda serialization was used, followed by an [Envelope] which carries the object to
     * be deserialized and a schema describing the types of the objects.
     */
    @Throws(NotSerializableException::class)
    fun <T : Any> deserialize(bytes: ByteSequence, clazz: Class<T>, context: SerializationContext): T =
            des {
                /**
                 * So that the [DecoderImpl] is held whilst we get the [Envelope] and [doReadObject],
                 * since we are using lazy when getting the [Envelope] which means [Schema] and
                 * [TransformsSchema] are only parsed out of the [bytes] if demanded by [doReadObject].
                 */
                decoderPool.reentrantRun {
                    /**
                     * The cache uses object identity rather than [ByteSequence.equals] and
                     * [ByteSequence.hashCode]. This is for speed: each [ByteSequence] object
                     * can potentially be large, and we are optimizing for the case when we
                     * know we will be deserializing the exact same objects multiple times.
                     * This also means that the cache MUST be short-lived, as otherwise it
                     * becomes a memory leak.
                     */
                    @Suppress("unchecked_cast")
                    val envelope = (context.properties[AMQP_ENVELOPE_CACHE_PROPERTY] as? MutableMap<IdentityKey, Envelope>)
                            ?.computeIfAbsent(IdentityKey(bytes)) { key ->
                                getEnvelope(key.bytes, context.encodingWhitelist, true)
                            } ?: getEnvelope(bytes, context.encodingWhitelist, true)

                    logger.trace { "deserialize blob scheme=\"${envelope.schema}\"" }

                    doReadObject(envelope, clazz, context)
                }
            }

    @Throws(NotSerializableException::class)
    fun <T : Any> deserializeAndReturnEnvelope(
            bytes: SerializedBytes<T>,
            clazz: Class<T>,
            context: SerializationContext
    ): ObjectAndEnvelope<T> = des {
        val envelope = getEnvelope(bytes, context.encodingWhitelist)
        // Now pick out the obj and schema from the envelope.
        ObjectAndEnvelope(doReadObject(envelope, clazz, context), envelope)
    }

    private fun <T: Any> doReadObject(envelope: Envelope, clazz: Class<T>, context: SerializationContext): T {
        return clazz.cast(readObjectOrNull(
                obj = redescribe(envelope.obj, clazz),
                schema = SerializationSchemas(envelope::resolvedSchema),
                type = clazz,
                context = context
        ))
    }

    fun readObjectOrNull(obj: Any?, schema: SerializationSchemas, type: Type, context: SerializationContext
    ): Any? {
        return if (obj == null) null else readObject(obj, schema, type, context)
    }

    fun readObject(obj: Any, schemas: SerializationSchemas, type: Type, context: SerializationContext): Any =
            if (obj is DescribedType && ReferencedObject.DESCRIPTOR == obj.descriptor) {
                // It must be a reference to an instance that has already been read, cheaply and quickly returning it by reference.
                val objectIndex = (obj.described as UnsignedInteger).toInt()
                if (objectIndex >= objectHistory.size)
                    throw AMQPNotSerializableException(
                            type,
                            "Retrieval of existing reference failed. Requested index $objectIndex " +
                            "is outside of the bounds for the list of size: ${objectHistory.size}")

                val objectRetrieved = objectHistory[objectIndex]
                if (!objectRetrieved::class.java.isSubClassOf(type.asClass())) {
                    throw AMQPNotSerializableException(
                            type,
                            "Existing reference type mismatch. Expected: '$type', found: '${objectRetrieved::class.java}' " +
                                    "@ $objectIndex")
                }
                objectRetrieved
            } else {
                val objectRead = when (obj) {
                    is DescribedType -> {
                        // Look up serializer in factory by descriptor
                        val serializer = serializerFactory.get(obj.descriptor.toString(), schemas, context)
                        if (type != TypeIdentifier.UnknownType.getLocalType() && serializer.type != type && with(serializer.type) {
                                    !isSubClassOf(type) && !materiallyEquivalentTo(type)
                                }
                        ) {
                            throw AMQPNotSerializableException(
                                    type,
                                    "Described type with descriptor ${obj.descriptor} was " +
                                    "expected to be of type $type but was ${serializer.type}")
                        }
                        serializer.readObject(obj.described, schemas, this, context)
                    }
                    is Binary -> obj.array
                    else -> if ((type is Class<*>) && type.isPrimitive) {
                        // this will be the case for primitive types like [boolean] et al.
                        obj
                    } else {
                        // these will be boxed primitive types
                        serializerFactory.get(obj::class.java, type).readObject(obj, schemas, this, context)
                    }
                }

                // Store the reference in case we need it later on.
                // Skip for primitive types as they are too small and overhead of referencing them will be much higher
                // than their content
                if (serializerFactory.isSuitableForObjectReference(objectRead.javaClass)) {
                    objectHistory.add(objectRead)
                }
                objectRead
            }

    /**
     * Currently performs checks aimed at:
     *  * [java.util.List<Command<?>>] and [java.lang.Class<? extends net.corda.core.contracts.Contract>]
     *  * [T : Parent] and [Parent]
     *  * [? extends Parent] and [Parent]
     *
     * In the future tighter control might be needed
     */
    private fun Type.materiallyEquivalentTo(that: Type): Boolean =
            when (that) {
                is ParameterizedType -> asClass() == that.asClass()
                is TypeVariable<*> -> isSubClassOf(that.bounds.first())
                is WildcardType -> isSubClassOf(that.upperBounds.first())
                else -> false
            }
}

/**
 * We cannot use [ByteSequence.equals] and [ByteSequence.hashCode] because
 * these consider the contents of the underlying [ByteArray] object. We
 * only need the [ByteSequence]'s object identity for our use-case.
 */
private class IdentityKey(val bytes: ByteSequence) {
    override fun hashCode() = System.identityHashCode(bytes)

    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is IdentityKey && bytes === other.bytes)
    }
}

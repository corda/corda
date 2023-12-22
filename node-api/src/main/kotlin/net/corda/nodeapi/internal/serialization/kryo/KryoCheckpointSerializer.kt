package net.corda.nodeapi.internal.serialization.kryo

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.io.serialization.kryo.CollectionsSetFromMapSerializer
import co.paralleluniverse.io.serialization.kryo.ExternalizableKryoSerializer
import co.paralleluniverse.io.serialization.kryo.JdkProxySerializer
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import co.paralleluniverse.io.serialization.kryo.ReferenceSerializer
import com.esotericsoftware.kryo.ClassResolver
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Registration
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.ClosureSerializer
import com.esotericsoftware.kryo.serializers.DefaultSerializers
import com.esotericsoftware.kryo.util.MapReferenceResolver
import de.javakaffee.kryoserializers.GregorianCalendarSerializer
import de.javakaffee.kryoserializers.SynchronizedCollectionsSerializer
import de.javakaffee.kryoserializers.UnmodifiableCollectionsSerializer
import net.corda.core.internal.rootCause
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.CheckpointCustomSerializer
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.internal.CheckpointSerializationContext
import net.corda.core.serialization.internal.CheckpointSerializer
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.loggerFor
import net.corda.serialization.internal.AlwaysAcceptEncodingWhitelist
import net.corda.serialization.internal.ByteBufferInputStream
import net.corda.serialization.internal.CheckpointSerializationContextImpl
import net.corda.serialization.internal.CordaSerializationEncoding
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.QuasarWhitelist
import net.corda.serialization.internal.SectionId
import net.corda.serialization.internal.encodingNotPermittedFormat
import java.io.Externalizable
import java.lang.ref.Reference
import java.lang.reflect.InaccessibleObjectException
import java.lang.reflect.InvocationHandler
import java.net.URI
import java.util.Collections
import java.util.EnumMap
import java.util.EnumSet
import java.util.GregorianCalendar
import java.util.LinkedList
import java.util.TreeMap
import java.util.TreeSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern

val kryoMagic = CordaSerializationMagic("corda".toByteArray() + byteArrayOf(0, 0))

private object AutoCloseableSerialisationDetector : Serializer<AutoCloseable>() {
    override fun write(kryo: Kryo, output: Output, closeable: AutoCloseable) {
        val message = "${closeable.javaClass.name}, which is a closeable resource, has been detected during flow checkpointing. " +
                "Restoring such resources across node restarts is not supported. Make sure code accessing it is " +
                "confined to a private method or the reference is nulled out."
        throw UnsupportedOperationException(message)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out AutoCloseable>) = throw IllegalStateException("Should not reach here!")
}

private object FutureSerialisationDetector : Serializer<Future<*>>() {
    override fun write(kryo: Kryo, output: Output, future: Future<*>) {
        val message = "${future.javaClass.name}, which is a Future, has been detected during flow checkpointing. " +
                "Restoring Futures across node restarts is not supported. Make sure code accessing it is " +
                "confined to a private method or the reference is nulled out."
        throw UnsupportedOperationException(message)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out Future<*>>) = throw IllegalStateException("Should not reach here!")
}

object KryoCheckpointSerializer : CheckpointSerializer {
    private val kryoPoolsForContexts = ConcurrentHashMap<Triple<ClassWhitelist, ClassLoader, Iterable<CheckpointCustomSerializer<*,*>>>, KryoPool>()
    private fun getPool(context: CheckpointSerializationContext): KryoPool {
        return kryoPoolsForContexts.computeIfAbsent(Triple(context.whitelist, context.deserializationClassLoader, context.checkpointCustomSerializers)) {
            KryoPool {
                val classResolver = CordaClassResolver(context)
                try {
                    val serializer = Fiber.getFiberSerializer(classResolver,false) as KryoSerializer
                    // TODO The ClassResolver can only be set in the Kryo constructor and Quasar doesn't provide us with a way of doing that
                    val field = Kryo::class.java.getDeclaredField("classResolver").apply { isAccessible = true }
                    serializer.kryo.apply {
                        field.set(this, classResolver)
//                    (this as ReplaceableObjectKryo).isIgnoreInaccessibleClasses = true
                        // don't allow overriding the public key serializer for checkpointing
                        DefaultKryoCustomizer.customize(this)
                        addDefaultSerializer(AutoCloseable::class.java, AutoCloseableSerialisationDetector)
                        addDefaultSerializer(Future::class.java, FutureSerialisationDetector)
                        register(ClosureSerializer.Closure::class.java, CordaClosureSerializer)
                        classLoader = it.second

                        // Add custom serializers
                        val customSerializers = buildCustomSerializerAdaptors(context)
                        warnAboutDuplicateSerializers(customSerializers)
                        val classToSerializer = mapInputClassToCustomSerializer(context.deserializationClassLoader, customSerializers)
                        addDefaultCustomSerializers(this, classToSerializer)
                    }
                } catch (t: Throwable) {
                    if (t.rootCause is InaccessibleObjectException) {
                        CheckpointsNotSupported(classResolver)
                    } else {
                        throw t
                    }
                }

            }
        }
    }

    /**
     * Copy of [co.paralleluniverse.io.serialization.kryo.KryoUtil.registerCommonClasses] ...
     */
    private fun registerCommonClasses(kryo: Kryo) {
        kryo.register(BooleanArray::class.java)
        kryo.register(ByteArray::class.java)
        kryo.register(ShortArray::class.java)
        kryo.register(CharArray::class.java)
        kryo.register(IntArray::class.java)
        kryo.register(FloatArray::class.java)
        kryo.register(LongArray::class.java)
        kryo.register(DoubleArray::class.java)
        kryo.register(Array<String>::class.java)
        kryo.register(Array<IntArray>::class.java)
        kryo.register(ArrayList::class.java)
        kryo.register(LinkedList::class.java)
        kryo.register(HashMap::class.java)
        kryo.register(LinkedHashMap::class.java)
        kryo.register(TreeMap::class.java)
        kryo.register(EnumMap::class.java)
        kryo.register(HashSet::class.java)
        kryo.register(LinkedHashSet::class.java)
        kryo.register(TreeSet::class.java)
        kryo.register(EnumSet::class.java)

        kryo.register(Collections.newSetFromMap(emptyMap<Any, Boolean>()).javaClass, CollectionsSetFromMapSerializer())
        kryo.register(GregorianCalendar::class.java, GregorianCalendarSerializer())
        kryo.register(InvocationHandler::class.java, JdkProxySerializer())
        UnmodifiableCollectionsSerializer.registerSerializers(kryo)
        SynchronizedCollectionsSerializer.registerSerializers(kryo)
        kryo.addDefaultSerializer(Externalizable::class.java, ExternalizableKryoSerializer<Externalizable>())
        kryo.addDefaultSerializer(Reference::class.java, ReferenceSerializer())
        kryo.addDefaultSerializer(URI::class.java, DefaultSerializers.URISerializer::class.java)
        kryo.addDefaultSerializer(UUID::class.java, DefaultSerializers.UUIDSerializer::class.java)
        kryo.addDefaultSerializer(AtomicBoolean::class.java, DefaultSerializers.AtomicBooleanSerializer::class.java)
        kryo.addDefaultSerializer(AtomicInteger::class.java, DefaultSerializers.AtomicIntegerSerializer::class.java)
        kryo.addDefaultSerializer(AtomicLong::class.java, DefaultSerializers.AtomicLongSerializer::class.java)
        kryo.addDefaultSerializer(Pattern::class.java, DefaultSerializers.PatternSerializer::class.java)
    }

    private val inaccessibleLogged = AtomicBoolean(false)

    private inline fun Kryo.registerAccessible(type: Class<*>, createSerializer: () -> Serializer<*>) {
        val serializer = try {
            createSerializer()
        } catch (t: Throwable) {
            if (t.rootCause is InaccessibleObjectException) {
//                if (inaccessibleLogged.compareAndSet(false, true)) {
                    loggerFor<KryoCheckpointSerializer>().info("Objects of type ${type.name} cannot be serialised in checkpoints in the " +
                            "current test environment. Use out-of-process node driver if you wish to deserialise checkpoints.", t)
//                }
                CheckpointDeserialisationNotSupported
            } else {
                throw t
            }
        }
        register(type, serializer)
    }

    private object CheckpointDeserialisationNotSupported : Serializer<Any>() {
        override fun write(kryo: Kryo, output: Output, obj: Any) {
            // Nothing to write
        }
        override fun read(kryo: Kryo, input: Input, type: Class<out Any>): Any {
            throw UnsupportedOperationException("Restoring checkpoints is not supported in the current test environment. Use " +
                    "out-of-process node driver if you wish to deserialise checkpoints.")
        }
    }


    private class CheckpointsNotSupported(classResolver: ClassResolver) : Kryo(classResolver, MapReferenceResolver()) {
//        override fun writeClass(output: Output?, type: Class<*>?): Registration {
//        }

        override fun writeClassAndObject(output: Output?, `object`: Any?) {
        }

        override fun writeObject(output: Output?, `object`: Any?) {
        }

        override fun writeObject(output: Output?, `object`: Any?, serializer: Serializer<*>?) {
        }

        override fun writeObjectOrNull(output: Output?, `object`: Any?, serializer: Serializer<*>?) {
        }

        override fun writeObjectOrNull(output: Output?, `object`: Any?, type: Class<*>?) {
        }

        override fun readClass(input: Input): Registration = unsupported()

        override fun readClassAndObject(input: Input): Any = unsupported()

        override fun <T : Any?> readObject(input: Input, type: Class<T>): T = unsupported()

        override fun <T : Any?> readObjectOrNull(input: Input, type: Class<T>): T = unsupported()

        override fun <T : Any?> readObject(input: Input, type: Class<T>, serializer: Serializer<*>): T = unsupported()

        override fun <T : Any?> readObjectOrNull(input: Input, type: Class<T>, serializer: Serializer<*>): T = unsupported()

        private fun unsupported(): Nothing {
            throw UnsupportedOperationException("Checkpoints are not supported in the current test environment. Use the out-of-process " +
                    "node driver if you wish to test checkpoints.")
        }
    }

    /**
     * Returns a sorted list of CustomSerializerCheckpointAdaptor based on the custom serializers inside context.
     *
     * The adaptors are sorted by serializerName which maps to javaClass.name for the serializer class
     */
    private fun buildCustomSerializerAdaptors(context: CheckpointSerializationContext) =
            context.checkpointCustomSerializers.map { CustomSerializerCheckpointAdaptor(it) }.sortedBy { it.serializerName }

    /**
     * Returns a list of pairs where the first element is the input class of the custom serializer and the second element is the
     * custom serializer.
     */
    private fun mapInputClassToCustomSerializer(classLoader: ClassLoader, customSerializers: Iterable<CustomSerializerCheckpointAdaptor<*, *>>) =
            customSerializers.map { getInputClassForCustomSerializer(classLoader, it) to it }

    /**
     * Returns the Class object for the serializers input type.
     */
    private fun getInputClassForCustomSerializer(classLoader: ClassLoader, customSerializer: CustomSerializerCheckpointAdaptor<*, *>): Class<*> {
        val typeNameWithoutGenerics = customSerializer.cordappType.typeName.substringBefore('<')
        return Class.forName(typeNameWithoutGenerics, false, classLoader)
    }

    /**
     * Emit a warning if two or more custom serializers are found for the same input type.
     */
    private fun warnAboutDuplicateSerializers(customSerializers: Iterable<CustomSerializerCheckpointAdaptor<*,*>>) =
            customSerializers
                    .groupBy({ it.cordappType }, { it.serializerName })
                    .filter { (_, serializerNames) -> serializerNames.distinct().size > 1 }
                    .forEach { (inputType, serializerNames) -> loggerFor<KryoCheckpointSerializer>().warn("Duplicate custom checkpoint serializer for type $inputType. Serializers: ${serializerNames.joinToString(", ")}") }

    /**
     * Register all custom serializers as default, this class + subclass, registrations.
     *
     * Serializers registered before this will take priority. This needs to run after registrations we want to keep otherwise it may
     * replace them.
     */
    private fun addDefaultCustomSerializers(kryo: Kryo, classToSerializer: Iterable<Pair<Class<*>, CustomSerializerCheckpointAdaptor<*, *>>>) =
            classToSerializer
                    .forEach { (clazz, customSerializer) -> kryo.addDefaultSerializer(clazz, customSerializer) }

    private fun <T : Any> CheckpointSerializationContext.kryo(task: Kryo.() -> T): T {
        return getPool(this).run {
            this.context.ensureCapacity(properties.size)
            properties.forEach { this.context.put(it.key, it.value) }
            try {
                this.task()
            } finally {
                this.context.clear()
            }
        }
    }

    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: CheckpointSerializationContext): T {
        val dataBytes = kryoMagic.consume(byteSequence)
                ?: throw KryoException("Serialized bytes header does not match expected format.")
        return context.kryo {
            kryoInput(ByteBufferInputStream(dataBytes)) {
                val result: T
                loop@ while (true) {
                    when (SectionId.reader.readFrom(this)) {
                        SectionId.ENCODING -> {
                            val encoding = CordaSerializationEncoding.reader.readFrom(this)
                            context.encodingWhitelist.acceptEncoding(encoding) || throw KryoException(encodingNotPermittedFormat.format(encoding))
                            substitute(encoding::wrap)
                        }
                        SectionId.DATA_AND_STOP, SectionId.ALT_DATA_AND_STOP -> {
                            result = if (context.objectReferencesEnabled) {
                                uncheckedCast(readClassAndObject(this))
                            } else {
                                withoutReferences { uncheckedCast<Any?, T>(readClassAndObject(this)) }
                            }
                            break@loop
                        }
                    }
                }
                result
            }
        }
    }

    override fun <T : Any> serialize(obj: T, context: CheckpointSerializationContext): SerializedBytes<T> {
        return context.kryo {
            SerializedBytes(kryoOutput {
                kryoMagic.writeTo(this)
                context.encoding?.let { encoding ->
                    SectionId.ENCODING.writeTo(this)
                    (encoding as CordaSerializationEncoding).writeTo(this)
                    substitute(encoding::wrap)
                }
                SectionId.ALT_DATA_AND_STOP.writeTo(this) // Forward-compatible in null-encoding case.
                if (context.objectReferencesEnabled) {
                    writeClassAndObject(this, obj)
                } else {
                    withoutReferences { writeClassAndObject(this, obj) }
                }
            })
        }
    }
}

val KRYO_CHECKPOINT_CONTEXT = CheckpointSerializationContextImpl(
        SerializationDefaults.javaClass.classLoader,
        QuasarWhitelist,
        emptyMap(),
        true,
        null,
        AlwaysAcceptEncodingWhitelist
)

package net.corda.nodeapi.internal.serialization

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.pool.KryoPool
import com.esotericsoftware.kryo.serializers.ClosureSerializer
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.LazyPool
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.*
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.OpaqueBytes
import net.corda.nodeapi.internal.AttachmentsClassLoader
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.NotSerializableException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException

val attachmentsClassLoaderEnabledPropertyName = "attachments.class.loader.enabled"

object NotSupportedSerializationScheme : SerializationScheme {
    private fun doThrow(): Nothing = throw UnsupportedOperationException("Serialization scheme not supported.")

    override fun canDeserializeVersion(byteSequence: ByteSequence, target: SerializationContext.UseCase): Boolean = doThrow()

    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T = doThrow()

    override fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T> = doThrow()
}

data class SerializationContextImpl(override val preferredSerializationVersion: ByteSequence,
                                    override val deserializationClassLoader: ClassLoader,
                                    override val whitelist: ClassWhitelist,
                                    override val properties: Map<Any, Any>,
                                    override val objectReferencesEnabled: Boolean,
                                    override val useCase: SerializationContext.UseCase) : SerializationContext {

    private val cache: Cache<List<SecureHash>, AttachmentsClassLoader> = CacheBuilder.newBuilder().weakValues().maximumSize(1024).build()

    /**
     * {@inheritDoc}
     *
     * We need to cache the AttachmentClassLoaders to avoid too many contexts, since the class loader is part of cache key for the context.
     */
    override fun withAttachmentsClassLoader(attachmentHashes: List<SecureHash>): SerializationContext {
        properties[attachmentsClassLoaderEnabledPropertyName] as? Boolean == true || return this
        val serializationContext = properties[serializationContextKey] as? SerializeAsTokenContextImpl ?: return this // Some tests don't set one.
        try {
            return withClassLoader(cache.get(attachmentHashes) {
                val missing = ArrayList<SecureHash>()
                val attachments = ArrayList<Attachment>()
                attachmentHashes.forEach { id ->
                    serializationContext.serviceHub.attachments.openAttachment(id)?.let { attachments += it } ?: run { missing += id }
                }
                missing.isNotEmpty() && throw MissingAttachmentsException(missing)
                AttachmentsClassLoader(attachments, parent = deserializationClassLoader)
            })
        } catch (e: ExecutionException) {
            // Caught from within the cache get, so unwrap.
            throw e.cause!!
        }
    }

    override fun withProperty(property: Any, value: Any): SerializationContext {
        return copy(properties = properties + (property to value))
    }

    override fun withoutReferences(): SerializationContext {
        return copy(objectReferencesEnabled = false)
    }

    override fun withClassLoader(classLoader: ClassLoader): SerializationContext {
        return copy(deserializationClassLoader = classLoader)
    }

    override fun withWhitelisted(clazz: Class<*>): SerializationContext {
        return copy(whitelist = object : ClassWhitelist {
            override fun hasListed(type: Class<*>): Boolean = whitelist.hasListed(type) || type.name == clazz.name
        })
    }

    override fun withPreferredSerializationVersion(versionHeader: ByteSequence) = copy(preferredSerializationVersion = versionHeader)
}

private const val HEADER_SIZE: Int = 8

fun ByteSequence.obtainHeaderSignature() = take(HEADER_SIZE).copy()

open class SerializationFactoryImpl : SerializationFactory() {
    private val creator: List<StackTraceElement> = Exception().stackTrace.asList()

    private val registeredSchemes: MutableCollection<SerializationScheme> = Collections.synchronizedCollection(mutableListOf())

    private val logger = LoggerFactory.getLogger(javaClass)

    // TODO: This is read-mostly. Probably a faster implementation to be found.
    private val schemes: ConcurrentHashMap<Pair<ByteSequence, SerializationContext.UseCase>, SerializationScheme> = ConcurrentHashMap()

    private fun schemeFor(byteSequence: ByteSequence, target: SerializationContext.UseCase): SerializationScheme {
        // truncate sequence to 8 bytes, and make sure it's a copy to avoid holding onto large ByteArrays
        val lookupKey = byteSequence.obtainHeaderSignature() to target
        return schemes.computeIfAbsent(lookupKey) {
            registeredSchemes
                    .filter { scheme -> scheme.canDeserializeVersion(it.first, it.second) }
                    .forEach { return@computeIfAbsent it }
            logger.warn("Cannot find serialization scheme for: $lookupKey, registeredSchemes are: $registeredSchemes")
            NotSupportedSerializationScheme
        }
    }

    @Throws(NotSerializableException::class)
    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T {
        return asCurrent { withCurrentContext(context) { schemeFor(byteSequence, context.useCase).deserialize(byteSequence, clazz, context) } }
    }

    override fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T> {
        return asCurrent { withCurrentContext(context) { schemeFor(context.preferredSerializationVersion, context.useCase).serialize(obj, context) } }
    }

    fun registerScheme(scheme: SerializationScheme) {
        check(schemes.isEmpty()) { "All serialization schemes must be registered before any scheme is used." }
        registeredSchemes += scheme
    }

    val alreadyRegisteredSchemes: Collection<SerializationScheme> get() = Collections.unmodifiableCollection(registeredSchemes)

    override fun toString(): String {
        return "${this.javaClass.name} registeredSchemes=$registeredSchemes ${creator.joinToString("\n")}"
    }

    override fun equals(other: Any?): Boolean {
        return other is SerializationFactoryImpl &&
                other.registeredSchemes == this.registeredSchemes
    }

    override fun hashCode(): Int = registeredSchemes.hashCode()
}

private object AutoCloseableSerialisationDetector : Serializer<AutoCloseable>() {
    override fun write(kryo: Kryo, output: Output, closeable: AutoCloseable) {
        val message = "${closeable.javaClass.name}, which is a closeable resource, has been detected during flow checkpointing. " +
                "Restoring such resources across node restarts is not supported. Make sure code accessing it is " +
                "confined to a private method or the reference is nulled out."
        throw UnsupportedOperationException(message)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<AutoCloseable>) = throw IllegalStateException("Should not reach here!")
}

abstract class AbstractKryoSerializationScheme : SerializationScheme {
    private val kryoPoolsForContexts = ConcurrentHashMap<Pair<ClassWhitelist, ClassLoader>, KryoPool>()

    protected abstract fun rpcClientKryoPool(context: SerializationContext): KryoPool
    protected abstract fun rpcServerKryoPool(context: SerializationContext): KryoPool

    private fun getPool(context: SerializationContext): KryoPool {
        return kryoPoolsForContexts.computeIfAbsent(Pair(context.whitelist, context.deserializationClassLoader)) {
            when (context.useCase) {
                SerializationContext.UseCase.Checkpoint ->
                    KryoPool.Builder {
                        val serializer = Fiber.getFiberSerializer(false) as KryoSerializer
                        val classResolver = CordaClassResolver(context).apply { setKryo(serializer.kryo) }
                        // TODO The ClassResolver can only be set in the Kryo constructor and Quasar doesn't provide us with a way of doing that
                        val field = Kryo::class.java.getDeclaredField("classResolver").apply { isAccessible = true }
                        serializer.kryo.apply {
                            field.set(this, classResolver)
                            DefaultKryoCustomizer.customize(this)
                            addDefaultSerializer(AutoCloseable::class.java, AutoCloseableSerialisationDetector)
                            register(ClosureSerializer.Closure::class.java, CordaClosureSerializer)
                            classLoader = it.second
                        }
                    }.build()
                SerializationContext.UseCase.RPCClient ->
                    rpcClientKryoPool(context)
                SerializationContext.UseCase.RPCServer ->
                    rpcServerKryoPool(context)
                else ->
                    KryoPool.Builder {
                        DefaultKryoCustomizer.customize(CordaKryo(CordaClassResolver(context))).apply { classLoader = it.second }
                    }.build()
            }
        }
    }

    private fun <T : Any> withContext(kryo: Kryo, context: SerializationContext, block: (Kryo) -> T): T {
        kryo.context.ensureCapacity(context.properties.size)
        context.properties.forEach { kryo.context.put(it.key, it.value) }
        try {
            return block(kryo)
        } finally {
            kryo.context.clear()
        }
    }

    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T {
        val pool = getPool(context)
        val headerSize = KryoHeaderV0_1.size
        val header = byteSequence.take(headerSize)
        if (header != KryoHeaderV0_1) {
            throw KryoException("Serialized bytes header does not match expected format.")
        }
        Input(byteSequence.bytes, byteSequence.offset + headerSize, byteSequence.size - headerSize).use { input ->
            return pool.run { kryo ->
                withContext(kryo, context) {
                    if (context.objectReferencesEnabled) {
                        uncheckedCast(kryo.readClassAndObject(input))
                    } else {
                        kryo.withoutReferences { uncheckedCast<Any?, T>(kryo.readClassAndObject(input)) }
                    }
                }
            }
        }
    }

    override fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T> {
        val pool = getPool(context)
        return pool.run { kryo ->
            withContext(kryo, context) {
                serializeOutputStreamPool.run { stream ->
                    serializeBufferPool.run { buffer ->
                        Output(buffer).use {
                            it.outputStream = stream
                            it.writeBytes(KryoHeaderV0_1.bytes)
                            if (context.objectReferencesEnabled) {
                                kryo.writeClassAndObject(it, obj)
                            } else {
                                kryo.withoutReferences { kryo.writeClassAndObject(it, obj) }
                            }
                        }
                        SerializedBytes(stream.toByteArray())
                    }
                }
            }
        }
    }
}

private val serializeBufferPool = LazyPool(
        newInstance = { ByteArray(64 * 1024) }
)
private val serializeOutputStreamPool = LazyPool(
        clear = ByteArrayOutputStream::reset,
        shouldReturnToPool = { it.size() < 256 * 1024 }, // Discard if it grew too large
        newInstance = { ByteArrayOutputStream(64 * 1024) }
)

// "corda" + majorVersionByte + minorVersionMSB + minorVersionLSB
val KryoHeaderV0_1: OpaqueBytes = OpaqueBytes("corda\u0000\u0000\u0001".toByteArray(Charsets.UTF_8))


val KRYO_P2P_CONTEXT = SerializationContextImpl(KryoHeaderV0_1,
        SerializationDefaults.javaClass.classLoader,
        GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()),
        emptyMap(),
        true,
        SerializationContext.UseCase.P2P)

interface SerializationScheme {
    // byteSequence expected to just be the 8 bytes necessary for versioning
    fun canDeserializeVersion(byteSequence: ByteSequence, target: SerializationContext.UseCase): Boolean

    @Throws(NotSerializableException::class)
    fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T

    @Throws(NotSerializableException::class)
    fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T>
}
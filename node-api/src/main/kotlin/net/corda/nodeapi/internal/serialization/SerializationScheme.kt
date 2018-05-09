package net.corda.nodeapi.internal.serialization

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.copyBytes
import net.corda.core.serialization.*
import net.corda.core.utilities.ByteSequence
import net.corda.nodeapi.internal.AttachmentsClassLoader
import net.corda.nodeapi.internal.serialization.amqp.amqpMagic
import net.corda.nodeapi.internal.serialization.kryo.kryoMagic
import org.slf4j.LoggerFactory
import java.io.NotSerializableException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException

const val attachmentsClassLoaderEnabledPropertyName = "attachments.class.loader.enabled"

internal object NullEncodingWhitelist : EncodingWhitelist {
    override fun acceptEncoding(encoding: SerializationEncoding) = false
}

data class SerializationContextImpl @JvmOverloads constructor(override val preferredSerializationVersion: SerializationMagic,
                                                              override val deserializationClassLoader: ClassLoader,
                                                              override val whitelist: ClassWhitelist,
                                                              override val properties: Map<Any, Any>,
                                                              override val objectReferencesEnabled: Boolean,
                                                              override val useCase: SerializationContext.UseCase,
                                                              override val encoding: SerializationEncoding?,
                                                              override val encodingWhitelist: EncodingWhitelist = NullEncodingWhitelist) : SerializationContext {
    private val builder = AttachmentsClassLoaderBuilder(properties, deserializationClassLoader)

    /**
     * {@inheritDoc}
     *
     * We need to cache the AttachmentClassLoaders to avoid too many contexts, since the class loader is part of cache key for the context.
     */
    override fun withAttachmentsClassLoader(attachmentHashes: List<SecureHash>): SerializationContext {
        properties[attachmentsClassLoaderEnabledPropertyName] as? Boolean == true || return this
        val classLoader = builder.build(attachmentHashes) ?: return this
        return withClassLoader(classLoader)
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

    override fun withPreferredSerializationVersion(magic: SerializationMagic) = copy(preferredSerializationVersion = magic)
    override fun withEncoding(encoding: SerializationEncoding?) = copy(encoding = encoding)
}

/*
 * This class is internal rather than private so that node-api-deterministic
 * can replace it with an alternative version.
 */
internal class AttachmentsClassLoaderBuilder(private val properties: Map<Any, Any>, private val deserializationClassLoader: ClassLoader) {
    private val cache: Cache<List<SecureHash>, AttachmentsClassLoader> = Caffeine.newBuilder().weakValues().maximumSize(1024).build()

    fun build(attachmentHashes: List<SecureHash>): AttachmentsClassLoader? {
        val serializationContext = properties[serializationContextKey] as? SerializeAsTokenContext ?: return null // Some tests don't set one.
        try {
            return cache.get(attachmentHashes) {
                val missing = ArrayList<SecureHash>()
                val attachments = ArrayList<Attachment>()
                attachmentHashes.forEach { id ->
                    serializationContext.serviceHub.attachments.openAttachment(id)?.let { attachments += it }
                        ?: run { missing += id }
                }
                missing.isNotEmpty() && throw MissingAttachmentsException(missing)
                AttachmentsClassLoader(attachments, parent = deserializationClassLoader)
            }!!
        } catch (e: ExecutionException) {
            // Caught from within the cache get, so unwrap.
            throw e.cause!!
        }
    }
}

open class SerializationFactoryImpl : SerializationFactory() {
    companion object {
        val magicSize = sequenceOf(kryoMagic, amqpMagic).map { it.size }.distinct().single()
    }

    private val creator: List<StackTraceElement> = Exception().stackTrace.asList()

    private val registeredSchemes: MutableCollection<SerializationScheme> = Collections.synchronizedCollection(mutableListOf())

    private val logger = LoggerFactory.getLogger(javaClass)

    // TODO: This is read-mostly. Probably a faster implementation to be found.
    private val schemes: ConcurrentHashMap<Pair<CordaSerializationMagic, SerializationContext.UseCase>, SerializationScheme> = ConcurrentHashMap()

    private fun schemeFor(byteSequence: ByteSequence, target: SerializationContext.UseCase): Pair<SerializationScheme, CordaSerializationMagic> {
        // truncate sequence to at most magicSize, and make sure it's a copy to avoid holding onto large ByteArrays
        val magic = CordaSerializationMagic(byteSequence.slice(end = magicSize).copyBytes())
        val lookupKey = magic to target
        return schemes.computeIfAbsent(lookupKey) {
            registeredSchemes.filter { it.canDeserializeVersion(magic, target) }.forEach { return@computeIfAbsent it } // XXX: Not single?
            logger.warn("Cannot find serialization scheme for: $lookupKey, registeredSchemes are: $registeredSchemes")
            throw UnsupportedOperationException("Serialization scheme not supported.")
        } to magic
    }

    @Throws(NotSerializableException::class)
    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T {
        return asCurrent { withCurrentContext(context) { schemeFor(byteSequence, context.useCase).first.deserialize(byteSequence, clazz, context) } }
    }

    @Throws(NotSerializableException::class)
    override fun <T : Any> deserializeWithCompatibleContext(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): ObjectWithCompatibleContext<T> {
        return asCurrent {
            withCurrentContext(context) {
                val (scheme, magic) = schemeFor(byteSequence, context.useCase)
                val deserializedObject = scheme.deserialize(byteSequence, clazz, context)
                ObjectWithCompatibleContext(deserializedObject, context.withPreferredSerializationVersion(magic))
            }
        }
    }

    override fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T> {
        return asCurrent { withCurrentContext(context) { schemeFor(context.preferredSerializationVersion, context.useCase).first.serialize(obj, context) } }
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


interface SerializationScheme {
    fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean
    @Throws(NotSerializableException::class)
    fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T

    @Throws(NotSerializableException::class)
    fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T>
}

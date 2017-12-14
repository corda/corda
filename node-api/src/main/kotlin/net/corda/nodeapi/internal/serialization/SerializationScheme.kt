package net.corda.nodeapi.internal.serialization

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.*
import net.corda.core.utilities.ByteSequence
import net.corda.nodeapi.internal.AttachmentsClassLoader
import org.slf4j.LoggerFactory
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

data class SerializationContextImpl(override val preferredSerializationVersion: VersionHeader,
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

    override fun withPreferredSerializationVersion(versionHeader: VersionHeader) = copy(preferredSerializationVersion = versionHeader)
}

private const val HEADER_SIZE: Int = 8

fun ByteSequence.obtainHeaderSignature(): VersionHeader = take(HEADER_SIZE).copy()

open class SerializationFactoryImpl : SerializationFactory() {
    private val creator: List<StackTraceElement> = Exception().stackTrace.asList()

    private val registeredSchemes: MutableCollection<SerializationScheme> = Collections.synchronizedCollection(mutableListOf())

    private val logger = LoggerFactory.getLogger(javaClass)

    // TODO: This is read-mostly. Probably a faster implementation to be found.
    private val schemes: ConcurrentHashMap<Pair<ByteSequence, SerializationContext.UseCase>, SerializationScheme> = ConcurrentHashMap()

    private fun schemeFor(byteSequence: ByteSequence, target: SerializationContext.UseCase): Pair<SerializationScheme, VersionHeader> {
        // truncate sequence to 8 bytes, and make sure it's a copy to avoid holding onto large ByteArrays
        val lookupKey = byteSequence.obtainHeaderSignature() to target
        val scheme = schemes.computeIfAbsent(lookupKey) {
            registeredSchemes
                    .filter { scheme -> scheme.canDeserializeVersion(it.first, it.second) }
                    .forEach { return@computeIfAbsent it }
            logger.warn("Cannot find serialization scheme for: $lookupKey, registeredSchemes are: $registeredSchemes")
            NotSupportedSerializationScheme
        }
        return scheme to lookupKey.first
    }

    @Throws(NotSerializableException::class)
    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T {
        return asCurrent { withCurrentContext(context) { schemeFor(byteSequence, context.useCase).first.deserialize(byteSequence, clazz, context) } }
    }

    @Throws(NotSerializableException::class)
    override fun <T : Any> deserializeWithCompatibleContext(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): ObjectWithCompatibleContext<T> {
        return asCurrent {
            withCurrentContext(context) {
                val (scheme, versionHeader) = schemeFor(byteSequence, context.useCase)
                val deserializedObject = scheme.deserialize(byteSequence, clazz, context)
                ObjectWithCompatibleContext(deserializedObject, context.withPreferredSerializationVersion(versionHeader))
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
    // byteSequence expected to just be the 8 bytes necessary for versioning
    fun canDeserializeVersion(byteSequence: ByteSequence, target: SerializationContext.UseCase): Boolean

    @Throws(NotSerializableException::class)
    fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T

    @Throws(NotSerializableException::class)
    fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T>
}
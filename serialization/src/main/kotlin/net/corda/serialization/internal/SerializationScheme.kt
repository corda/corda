package net.corda.serialization.internal

import net.corda.core.crypto.SecureHash
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.copyBytes
import net.corda.core.serialization.*
import net.corda.core.serialization.internal.CustomSerializationSchemeUtils.Companion.getSchemeIdIfCustomSerializationMagic
import net.corda.core.utilities.ByteSequence
import net.corda.serialization.internal.amqp.amqpMagic
import org.slf4j.LoggerFactory
import java.io.NotSerializableException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal object NullEncodingWhitelist : EncodingWhitelist {
    override fun acceptEncoding(encoding: SerializationEncoding) = false
}

internal object SnappyEncodingWhitelist: EncodingWhitelist {
    override fun acceptEncoding(encoding: SerializationEncoding): Boolean {
        return encoding == CordaSerializationEncoding.SNAPPY
    }
}

data class SerializationContextImpl @JvmOverloads constructor(override val preferredSerializationVersion: SerializationMagic,
                                                              override val deserializationClassLoader: ClassLoader,
                                                              override val whitelist: ClassWhitelist,
                                                              override val properties: Map<Any, Any>,
                                                              override val objectReferencesEnabled: Boolean,
                                                              override val useCase: SerializationContext.UseCase,
                                                              override val encoding: SerializationEncoding?,
                                                              override val encodingWhitelist: EncodingWhitelist = SnappyEncodingWhitelist,
                                                              override val lenientCarpenterEnabled: Boolean = false,
                                                              override val carpenterDisabled: Boolean = false,
                                                              override val preventDataLoss: Boolean = false,
                                                              override val customSerializers: Set<SerializationCustomSerializer<*, *>> = emptySet()) : SerializationContext {
    /**
     * {@inheritDoc}
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun withAttachmentsClassLoader(attachmentHashes: List<SecureHash>): SerializationContext {
        return this
    }

    override fun withProperty(property: Any, value: Any): SerializationContext {
        return copy(properties = properties + (property to value))
    }

    override fun withProperties(extraProperties: Map<Any, Any>): SerializationContext {
        return copy(properties = properties + extraProperties)
    }

    override fun withoutReferences(): SerializationContext {
        return copy(objectReferencesEnabled = false)
    }

    override fun withLenientCarpenter(): SerializationContext = copy(lenientCarpenterEnabled = true)

    override fun withoutCarpenter(): SerializationContext = copy(carpenterDisabled = true)

    override fun withPreventDataLoss(): SerializationContext = copy(preventDataLoss = true)

    override fun withClassLoader(classLoader: ClassLoader): SerializationContext {
        return copy(deserializationClassLoader = classLoader)
    }

    override fun withWhitelisted(clazz: Class<*>): SerializationContext {
        return copy(whitelist = object : ClassWhitelist {
            override fun hasListed(type: Class<*>): Boolean = whitelist.hasListed(type) || type.name == clazz.name
        })
    }

    override fun withCustomSerializers(serializers: Set<SerializationCustomSerializer<*, *>>): SerializationContextImpl {
        return copy(customSerializers = customSerializers.union(serializers))
    }

    override fun withPreferredSerializationVersion(magic: SerializationMagic) = copy(preferredSerializationVersion = magic)
    override fun withEncoding(encoding: SerializationEncoding?) = copy(encoding = encoding)
    override fun withEncodingWhitelist(encodingWhitelist: EncodingWhitelist) = copy(encodingWhitelist = encodingWhitelist)
}

open class SerializationFactoryImpl(
        // TODO: This is read-mostly. Probably a faster implementation to be found.
        private val schemes: MutableMap<Pair<CordaSerializationMagic, SerializationContext.UseCase>, SerializationScheme>
) : SerializationFactory() {
    constructor() : this(ConcurrentHashMap())

    companion object {
        val magicSize = amqpMagic.size
    }

    @VisibleForTesting
    fun getRegisteredSchemes() = registeredSchemes

    private val creator: List<StackTraceElement> = Exception().stackTrace.asList()

    private val registeredSchemes: MutableCollection<SerializationScheme> = Collections.synchronizedCollection(mutableListOf())

    private val logger = LoggerFactory.getLogger(javaClass)

    private fun schemeFor(byteSequence: ByteSequence, target: SerializationContext.UseCase): Pair<SerializationScheme, CordaSerializationMagic> {
        // truncate sequence to at most magicSize, and make sure it's a copy to avoid holding onto large ByteArrays
        val magic = CordaSerializationMagic(byteSequence.slice(end = magicSize).copyBytes())
        val lookupKey = magic to target
        // ConcurrentHashMap.get() is lock free, but computeIfAbsent is not, even if the key is in the map already.
        return (schemes[lookupKey] ?: schemes.computeIfAbsent(lookupKey) {
            registeredSchemes.firstOrNull { it.canDeserializeVersion(magic, target) } ?: run {
                logger.warn("Cannot find serialization scheme for: [$lookupKey, " +
                        "${if (magic == amqpMagic) "AMQP" else "UNKNOWN MAGIC"}] registeredSchemes are: $registeredSchemes")
                val schemeId = getSchemeIdIfCustomSerializationMagic(magic) ?: throw UnsupportedOperationException("Serialization scheme" +
                        " $lookupKey not supported.")
                throw UnsupportedOperationException("Could not find custom serialization scheme with SchemeId = $schemeId.")
            }
        }) to magic
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

    override fun toString(): String {
        return "${this.javaClass.name} registeredSchemes=$registeredSchemes ${creator.joinToString("\n")}"
    }

    override fun equals(other: Any?): Boolean {
        return other is SerializationFactoryImpl && other.registeredSchemes == this.registeredSchemes
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

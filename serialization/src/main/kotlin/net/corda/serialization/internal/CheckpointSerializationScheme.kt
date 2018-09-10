package net.corda.serialization.internal

import net.corda.core.KeepForDJVM
import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.*
import net.corda.core.utilities.ByteSequence
import java.io.NotSerializableException

@KeepForDJVM
open class CheckpointSerializationFactoryImpl(
        private val scheme: CheckpointSerializationScheme
) : CheckpointSerializationFactory() {

    private val creator: List<StackTraceElement> = Exception().stackTrace.asList()

    @Throws(NotSerializableException::class)
    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: CheckpointSerializationContext): T {
        return asCurrent { withCurrentContext(context) { scheme.deserialize(byteSequence, clazz, context) } }
    }

    override fun <T : Any> serialize(obj: T, context: CheckpointSerializationContext): SerializedBytes<T> {
        return asCurrent { withCurrentContext(context) { scheme.serialize(obj, context) } }
    }

    override fun toString(): String {
        return "${this.javaClass.name} scheme=$scheme ${creator.joinToString("\n")}"
    }

    override fun equals(other: Any?): Boolean {
        return other is CheckpointSerializationFactoryImpl && other.scheme == this.scheme
    }

    override fun hashCode(): Int = scheme.hashCode()
}

@KeepForDJVM
interface CheckpointSerializationScheme {
    @Throws(NotSerializableException::class)
    fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: CheckpointSerializationContext): T

    @Throws(NotSerializableException::class)
    fun <T : Any> serialize(obj: T, context: CheckpointSerializationContext): SerializedBytes<T>
}

@KeepForDJVM
data class CheckpointSerializationContextImpl @JvmOverloads constructor(
                                                              override val deserializationClassLoader: ClassLoader,
                                                              override val whitelist: ClassWhitelist,
                                                              override val properties: Map<Any, Any>,
                                                              override val objectReferencesEnabled: Boolean,
                                                              override val encoding: SerializationEncoding?,
                                                              override val encodingWhitelist: EncodingWhitelist = NullEncodingWhitelist) : CheckpointSerializationContext {
    private val builder = AttachmentsClassLoaderBuilder(properties, deserializationClassLoader)

    /**
     * {@inheritDoc}
     *
     * We need to cache the AttachmentClassLoaders to avoid too many contexts, since the class loader is part of cache key for the context.
     */
    override fun withAttachmentsClassLoader(attachmentHashes: List<SecureHash>): CheckpointSerializationContext {
        properties[attachmentsClassLoaderEnabledPropertyName] as? Boolean == true || return this
        val classLoader = builder.build(attachmentHashes) ?: return this
        return withClassLoader(classLoader)
    }

    override fun withProperty(property: Any, value: Any): CheckpointSerializationContext {
        return copy(properties = properties + (property to value))
    }

    override fun withoutReferences(): CheckpointSerializationContext {
        return copy(objectReferencesEnabled = false)
    }

    override fun withClassLoader(classLoader: ClassLoader): CheckpointSerializationContext {
        return copy(deserializationClassLoader = classLoader)
    }

    override fun withWhitelisted(clazz: Class<*>): CheckpointSerializationContext {
        return copy(whitelist = object : ClassWhitelist {
            override fun hasListed(type: Class<*>): Boolean = whitelist.hasListed(type) || type.name == clazz.name
        })
    }

    override fun withEncoding(encoding: SerializationEncoding?) = copy(encoding = encoding)
    override fun withEncodingWhitelist(encodingWhitelist: EncodingWhitelist) = copy(encodingWhitelist = encodingWhitelist)
}
package net.corda.serialization.internal

import net.corda.core.KeepForDJVM
import net.corda.core.serialization.CheckpointCustomSerializer
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.EncodingWhitelist
import net.corda.core.serialization.SerializationEncoding
import net.corda.core.serialization.internal.CheckpointSerializationContext

@KeepForDJVM
data class CheckpointSerializationContextImpl @JvmOverloads constructor(
                                                              override val deserializationClassLoader: ClassLoader,
                                                              override val whitelist: ClassWhitelist,
                                                              override val properties: Map<Any, Any>,
                                                              override val objectReferencesEnabled: Boolean,
                                                              override val encoding: SerializationEncoding?,
                                                              override val encodingWhitelist: EncodingWhitelist = NullEncodingWhitelist,
                                                              override val checkpointCustomSerializers: Iterable<CheckpointCustomSerializer<*,*>> = emptyList()) : CheckpointSerializationContext {
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
    override fun withCheckpointCustomSerializers(checkpointCustomSerializers : Iterable<CheckpointCustomSerializer<*,*>>)
            = copy(checkpointCustomSerializers = checkpointCustomSerializers)
}
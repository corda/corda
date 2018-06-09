package net.corda.serialization.internal

import net.corda.core.crypto.SecureHash
import java.lang.ClassLoader

/**
 * Drop-in replacement for [AttachmentsClassLoaderBuilder] in the serialization module.
 * This version is not strongly-coupled to [net.corda.core.node.ServiceHub].
 */
@Suppress("UNUSED", "UNUSED_PARAMETER")
internal class AttachmentsClassLoaderBuilder(private val properties: Map<Any, Any>, private val deserializationClassLoader: ClassLoader) {
    fun build(attachmentHashes: List<SecureHash>): AttachmentsClassLoader? = null
}
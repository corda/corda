package net.corda.serialization.internal

import net.corda.core.crypto.SecureHash

/**
 * Drop-in replacement for [AttachmentsClassLoaderBuilder] in the serialization module.
 * This version is not strongly-coupled to [net.corda.core.node.ServiceHub].
 */
@Suppress("UNUSED", "UNUSED_PARAMETER")
internal class AttachmentsClassLoaderBuilder() {
    fun build(attachmentHashes: List<SecureHash>, properties: Map<Any, Any>, deserializationClassLoader: ClassLoader): AttachmentsClassLoader? = null
}
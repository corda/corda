package net.corda.core.serialization.internal

import net.corda.core.internal.createSimpleCache
import net.corda.core.internal.toSynchronised

@Suppress("unused")
private class AttachmentsCacheImpl<K, V>(maxSize: Int = 1000) : AttachmentsCache<K, V> {

    private val cache: MutableMap<K, V> = createSimpleCache<K, V>(maxSize).toSynchronised()

    override fun computeIfAbsent(key: K, mappingFunction: (K) -> V): V {
        return cache.computeIfAbsent(key, mappingFunction)
    }
}
package net.corda.core.serialization.internal

import net.corda.core.internal.createSimpleCache
import net.corda.core.internal.toSynchronised
import java.util.function.Function

@Suppress("unused")
private class AttachmentsCacheImpl<K, V>(maxSize: Int) : AttachmentsCache<K, V> {

    private val cache: MutableMap<K, V> = createSimpleCache<K, V>(maxSize).toSynchronised()

    override fun computeIfAbsent(key: K, mappingFunction: Function<in K, out V>): V {
        return cache.computeIfAbsent(key, mappingFunction)
    }
}
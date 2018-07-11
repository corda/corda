package net.corda.serialization.internal.amqp
import net.corda.core.KeepForDJVM

@KeepForDJVM
class AccessOrderLinkedHashMap<K, V>(private val maxSize: Int) : LinkedHashMap<K, V>(16, 0.75f, true) {
    constructor(loader: () -> Int) : this(loader.invoke())

    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
        return this.size > maxSize
    }
}

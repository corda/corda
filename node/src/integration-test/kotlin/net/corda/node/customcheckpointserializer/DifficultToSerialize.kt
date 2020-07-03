package net.corda.node.customcheckpointserializer

import net.corda.core.flows.FlowException

class DifficultToSerialize {

    // Broken Map
    // This map breaks the rules for the put method. Making the normal map serializer fail.

    open class BrokenMapBaseImpl<K,V> : MutableMap<K,V>{
        private val map = HashMap<K,V>()

        override val size: Int
            get() = map.size
        override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
            get() = map.entries
        override val keys: MutableSet<K>
            get() = map.keys
        override val values: MutableCollection<V>
            get() = map.values

        override fun containsKey(key: K): Boolean = map.containsKey(key)
        override fun containsValue(value: V): Boolean = map.containsValue(value)
        override fun get(key: K): V? = map.get(key)
        override fun isEmpty(): Boolean = map.isEmpty()
        override fun clear() = map.clear()
        override fun put(key: K, value: V): V? = throw FlowException("Broken on purpose")
        override fun putAll(from: Map<out K, V>) = map.putAll(from)
        override fun remove(key: K): V? = map.remove(key)
    }

    // A class to test custom serializers applied to implementations
    class BrokenMapClass<K,V> : BrokenMapBaseImpl<K, V>()

    // An interface and implementation to test custom serializers applied to interface types
    interface BrokenMapInterface<K, V> : MutableMap<K, V>
    class BrokenMapInterfaceImpl<K,V> : BrokenMapBaseImpl<K, V>(), BrokenMapInterface<K, V>

    // An abstract class and implementation to test custom serializers applied to interface types
    abstract class BrokenMapAbstract<K, V> : BrokenMapBaseImpl<K, V>(), MutableMap<K, V>
    class BrokenMapAbstractImpl<K,V> : BrokenMapAbstract<K, V>()
}

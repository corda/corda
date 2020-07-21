package net.corda.node.customcheckpointserializer

import net.corda.core.flows.FlowException

class DifficultToSerialize {

    // Broken Map
    // This map breaks the rules for the put method. Making the normal map serializer fail.

    open class BrokenMapBaseImpl<K,V>(delegate: MutableMap<K, V> = mutableMapOf()) : MutableMap<K,V> by delegate {
        override fun put(key: K, value: V): V? = throw FlowException("Broken on purpose")
    }

    // A class to test custom serializers applied to implementations
    class BrokenMapClass<K,V> : BrokenMapBaseImpl<K, V>()

    // An interface and implementation to test custom serializers applied to interface types
    interface BrokenMapInterface<K, V> : MutableMap<K, V>
    class BrokenMapInterfaceImpl<K,V> : BrokenMapBaseImpl<K, V>(), BrokenMapInterface<K, V>

    // An abstract class and implementation to test custom serializers applied to interface types
    abstract class BrokenMapAbstract<K, V> : BrokenMapBaseImpl<K, V>(), MutableMap<K, V>
    class BrokenMapAbstractImpl<K,V> : BrokenMapAbstract<K, V>()

    // A final class
    final class BrokenMapFinal<K, V>: BrokenMapBaseImpl<K, V>()
}

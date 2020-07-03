package net.corda.node.customcheckpointserializer

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import org.assertj.core.api.Assertions
import org.junit.After
import org.junit.Before
import org.junit.Test

class MockNetworkCustomCheckpointSerializerTest {
    private lateinit var mockNetwork: MockNetwork

    @Before
    fun setup() {
        mockNetwork = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(CustomCheckpointSerializerCorDapp.getCorDapp())))
    }

    @After
    fun shutdown() {
        mockNetwork.stopNodes()
    }

    // Tests

    @Test(timeout = 300_000)
    fun `flow suspend with custom kryo serializer`() {
        val node = mockNetwork.createPartyNode()
        val expected = 5
        val actual = node.startFlow(CustomCheckpointSerializerCorDapp.TestFlowWithDifficultToSerializeLocalVariable(5)).get()

        Assertions.assertThat(actual).isEqualTo(expected)
    }

    @Test(timeout = 300_000)
    fun `check references are restored correctly`() {
        val node = mockNetwork.createPartyNode()
        val expectedReference = BrokenMapClass<String, Int>()
        expectedReference.putAll(mapOf("one" to 1))
        val actualReference = node.startFlow(CustomCheckpointSerializerCorDapp.TestFlowCheckingReferencesWork(expectedReference)).get()

        Assertions.assertThat(actualReference).isSameAs(expectedReference)
        Assertions.assertThat(actualReference["one"]).isEqualTo(1)
    }

    @Test(timeout = 300_000)
    @Suspendable
    fun `check serilization of interfaces`() {
        val node = mockNetwork.createPartyNode()
        val result = node.startFlow(CustomCheckpointSerializerCorDapp.TestFlowWithDifficultToSerializeLocalVariableAsInterface(5)).get()
        Assertions.assertThat(result).isEqualTo(5)
    }

    @Test(timeout = 300_000)
    @Suspendable
    fun `check serilization of abstract classes`() {
        val node = mockNetwork.createPartyNode()
        val result = node.startFlow(CustomCheckpointSerializerCorDapp.TestFlowWithDifficultToSerializeLocalVariableAsAbstract(5)).get()
        Assertions.assertThat(result).isEqualTo(5)
    }


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


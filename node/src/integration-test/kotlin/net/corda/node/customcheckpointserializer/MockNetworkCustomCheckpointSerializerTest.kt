package net.corda.node.customcheckpointserializer

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.serialization.CheckpointCustomSerializer
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.internal.enclosedCordapp
import org.assertj.core.api.Assertions
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Duration

class MockNetworkCustomCheckpointSerializerTest {
    private lateinit var mockNetwork: MockNetwork

    @Before
    fun setup() {
        mockNetwork = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(enclosedCordapp())))
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
        val actual = node.startFlow(TestFlowWithDifficultToSerializeLocalVariable(5)).get()

        Assertions.assertThat(actual).isEqualTo(expected)
    }

    @Test(timeout = 300_000)
    fun `check references are restored correctly`() {
        val node = mockNetwork.createPartyNode()
        val expectedReference = BrokenMapClass<String, Int>()
        expectedReference.putAll(mapOf("one" to 1))
        val actualReference = node.startFlow(TestFlowCheckingReferencesWork(expectedReference)).get()

        Assertions.assertThat(actualReference).isSameAs(expectedReference)
        Assertions.assertThat(actualReference["one"]).isEqualTo(1)
    }

    @Test(timeout = 300_000)
    @Suspendable
    fun `check serilization of interfaces`() {
        val node = mockNetwork.createPartyNode()
        val result = node.startFlow(TestFlowWithDifficultToSerializeLocalVariableAsInterface(5)).get()
        Assertions.assertThat(result).isEqualTo(5)
    }

    @Test(timeout = 300_000)
    @Suspendable
    fun `check serilization of abstract classes`() {
        val node = mockNetwork.createPartyNode()
        val result = node.startFlow(TestFlowWithDifficultToSerializeLocalVariableAsAbstract(5)).get()
        Assertions.assertThat(result).isEqualTo(5)
    }

    // Flows

    @StartableByRPC
    class TestFlowWithDifficultToSerializeLocalVariableAsAbstract(private val purchase: Int) : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int {

            // This object is difficult to serialize with Kryo
            val difficultToSerialize: BrokenMapAbstract<String, Int> = BrokenMapAbstractImpl()
            difficultToSerialize.putAll(mapOf("foo" to purchase))

            // Force a checkpoint
            sleep(Duration.ofSeconds(0))

            // Return value from deserialized object
            return difficultToSerialize["foo"] ?: 0
        }
    }


    @StartableByRPC
    class TestFlowWithDifficultToSerializeLocalVariableAsInterface(private val purchase: Int) : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int {

            // This object is difficult to serialize with Kryo
            val difficultToSerialize: BrokenMapInterface<String, Int> = BrokenMapInterfaceImpl()
            difficultToSerialize.putAll(mapOf("foo" to purchase))

            // Force a checkpoint
            sleep(Duration.ofSeconds(0))

            // Return value from deserialized object
            return difficultToSerialize["foo"] ?: 0
        }
    }

    @StartableByRPC
    class TestFlowWithDifficultToSerializeLocalVariable(private val purchase: Int) : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int {

            // This object is difficult to serialize with Kryo
            val difficultToSerialize: BrokenMapClass<String, Int> = BrokenMapClass()
            difficultToSerialize.putAll(mapOf("foo" to purchase))

            // Force a checkpoint
            sleep(Duration.ofSeconds(0))

            // Return value from deserialized object
            return difficultToSerialize["foo"] ?: 0
        }
    }

    @StartableByRPC
    class TestFlowCheckingReferencesWork(private val reference: BrokenMapClass<String, Int>) : FlowLogic<BrokenMapClass<String, Int>>() {

        private val referenceField = reference

        @Suspendable
        override fun call(): BrokenMapClass<String, Int> {

            val ref = referenceField

            // Force a checkpoint
            sleep(Duration.ofSeconds(0))

            // Check all objects refer to same object
            Assertions.assertThat(reference).isSameAs(referenceField)
            Assertions.assertThat(referenceField).isSameAs(ref)

            // Return deserialized object
            return ref
        }
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


    // Custom serializers

    @Suppress("unused")
    class TestInterfaceSerializer :
            CheckpointCustomSerializer<BrokenMapInterface<Any, Any>, HashMap<Any, Any>> {

        override fun toProxy(obj: BrokenMapInterface<Any, Any>): HashMap<Any, Any> {
            val proxy = HashMap<Any, Any>()
            return obj.toMap(proxy)
        }

        override fun fromProxy(proxy: HashMap<Any, Any>): BrokenMapInterface<Any, Any> {
            return BrokenMapInterfaceImpl<Any, Any>().also { it.putAll(proxy) }
        }
    }

    @Suppress("unused")
    class TestClassSerializer :
            CheckpointCustomSerializer<BrokenMapClass<Any, Any>, HashMap<Any, Any>> {

        override fun toProxy(obj: BrokenMapClass<Any, Any>): HashMap<Any, Any> {
            val proxy = HashMap<Any, Any>()
            return obj.toMap(proxy)
        }

        override fun fromProxy(proxy: HashMap<Any, Any>): BrokenMapClass<Any, Any> {
            return BrokenMapClass<Any, Any>().also { it.putAll(proxy) }
        }
    }

    @Suppress("unused")
    class TestAbstractClassSerializer :
            CheckpointCustomSerializer<BrokenMapAbstract<Any, Any>, HashMap<Any, Any>> {

        override fun toProxy(obj: BrokenMapAbstract<Any, Any>): HashMap<Any, Any> {
            val proxy = HashMap<Any, Any>()
            return obj.toMap(proxy)
        }

        override fun fromProxy(proxy: HashMap<Any, Any>): BrokenMapAbstract<Any, Any> {
            return BrokenMapAbstractImpl<Any, Any>().also { it.putAll(proxy) }
        }
    }

}

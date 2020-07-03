package net.corda.node.customcheckpointserializer

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.serialization.CheckpointCustomSerializer
import net.corda.testing.node.internal.CustomCordapp
import net.corda.testing.node.internal.enclosedCordapp
import org.assertj.core.api.Assertions
import java.time.Duration

class CustomCheckpointSerializerCorDapp {

    companion object {
        fun getCorDapp(): CustomCordapp = enclosedCordapp()
    }

    // Flows
    @StartableByRPC
    class TestFlowWithDifficultToSerializeLocalVariableAsAbstract(private val purchase: Int) : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int {

            // This object is difficult to serialize with Kryo
            val difficultToSerialize: MockNetworkCustomCheckpointSerializerTest.BrokenMapAbstract<String, Int> = MockNetworkCustomCheckpointSerializerTest.BrokenMapAbstractImpl()
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
            val difficultToSerialize: MockNetworkCustomCheckpointSerializerTest.BrokenMapInterface<String, Int> = MockNetworkCustomCheckpointSerializerTest.BrokenMapInterfaceImpl()
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
            val difficultToSerialize: MockNetworkCustomCheckpointSerializerTest.BrokenMapClass<String, Int> = MockNetworkCustomCheckpointSerializerTest.BrokenMapClass()
            difficultToSerialize.putAll(mapOf("foo" to purchase))

            // Force a checkpoint
            sleep(Duration.ofSeconds(0))

            // Return value from deserialized object
            return difficultToSerialize["foo"] ?: 0
        }

    }

    @StartableByRPC
    class TestFlowCheckingReferencesWork(private val reference: MockNetworkCustomCheckpointSerializerTest.BrokenMapClass<String, Int>) : FlowLogic<MockNetworkCustomCheckpointSerializerTest.BrokenMapClass<String, Int>>() {

        private val referenceField = reference
        @Suspendable
        override fun call(): MockNetworkCustomCheckpointSerializerTest.BrokenMapClass<String, Int> {

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

    // Custom serializers

    @Suppress("unused")
    class TestInterfaceSerializer :
            CheckpointCustomSerializer<MockNetworkCustomCheckpointSerializerTest.BrokenMapInterface<Any, Any>, HashMap<Any, Any>> {

        override fun toProxy(obj: MockNetworkCustomCheckpointSerializerTest.BrokenMapInterface<Any, Any>): HashMap<Any, Any> {
            val proxy = HashMap<Any, Any>()
            return obj.toMap(proxy)
        }
        override fun fromProxy(proxy: HashMap<Any, Any>): MockNetworkCustomCheckpointSerializerTest.BrokenMapInterface<Any, Any> {
            return MockNetworkCustomCheckpointSerializerTest.BrokenMapInterfaceImpl<Any, Any>()
                    .also { it.putAll(proxy) }
        }

    }

    @Suppress("unused")
    class TestClassSerializer :
            CheckpointCustomSerializer<MockNetworkCustomCheckpointSerializerTest.BrokenMapClass<Any, Any>, HashMap<Any, Any>> {

        override fun toProxy(obj: MockNetworkCustomCheckpointSerializerTest.BrokenMapClass<Any, Any>): HashMap<Any, Any> {
            val proxy = HashMap<Any, Any>()
            return obj.toMap(proxy)
        }
        override fun fromProxy(proxy: HashMap<Any, Any>): MockNetworkCustomCheckpointSerializerTest.BrokenMapClass<Any, Any> {
            return MockNetworkCustomCheckpointSerializerTest.BrokenMapClass<Any, Any>()
                    .also { it.putAll(proxy) }
        }

    }

    @Suppress("unused")
    class TestAbstractClassSerializer :
            CheckpointCustomSerializer<MockNetworkCustomCheckpointSerializerTest.BrokenMapAbstract<Any, Any>, HashMap<Any, Any>> {

        override fun toProxy(obj: MockNetworkCustomCheckpointSerializerTest.BrokenMapAbstract<Any, Any>): HashMap<Any, Any> {
            val proxy = HashMap<Any, Any>()
            return obj.toMap(proxy)
        }
        override fun fromProxy(proxy: HashMap<Any, Any>): MockNetworkCustomCheckpointSerializerTest.BrokenMapAbstract<Any, Any> {
            return MockNetworkCustomCheckpointSerializerTest.BrokenMapAbstractImpl<Any, Any>()
                    .also { it.putAll(proxy) }
        }

    }
}
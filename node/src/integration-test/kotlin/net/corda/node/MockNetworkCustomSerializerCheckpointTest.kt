package net.corda.node

import co.paralleluniverse.fibers.Suspendable
import com.github.andrewoma.dexx.kollection.ImmutableMap
import com.github.andrewoma.dexx.kollection.immutableMapOf
import com.github.andrewoma.dexx.kollection.toImmutableMap
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.serialization.CheckpointCustomSerializer
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.internal.enclosedCordapp
import org.assertj.core.api.Assertions
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Duration

class MockNetworkCustomSerializerCheckpointTest{
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
        val expectedReference = immutableMapOf(1 to 1)
        val actualReference = node.startFlow(TestFlowCheckingReferencesWork(expectedReference)).get()

        Assertions.assertThat(actualReference).isSameAs(expectedReference)
    }

    // Flows

    @StartableByRPC
    class TestFlowWithDifficultToSerializeLocalVariable(private val purchase: Int) : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int {

            // This object is difficult to serialize with Kryo
            val difficultToSerialize: ImmutableMap<String, Int> = immutableMapOf("foo" to purchase)

            // Force a checkpoint
            sleep(Duration.ofSeconds(0), maySkipCheckpoint = false)

            // Return value from deserialized object
            return difficultToSerialize["foo"] ?: 0
        }
    }

    @StartableByRPC
    class TestFlowCheckingReferencesWork<T>(private val reference: T) : FlowLogic<T>() {

        private val referenceField = reference

        @Suspendable
        override fun call(): T {

            val ref = referenceField

            // Force a checkpoint
            sleep(Duration.ofSeconds(0), maySkipCheckpoint = false)

            // Check all objects refer to same object
            Assertions.assertThat(reference).isSameAs(referenceField)
            Assertions.assertThat(referenceField).isSameAs(ref)

            // Return deserialized object
            return ref
        }
    }

    // Custom serializers

    @Suppress("unused")
    class TestSerializer :
            CheckpointCustomSerializer<ImmutableMap<Any, Any>, HashMap<Any, Any>> {

        override fun toProxy(obj: ImmutableMap<Any, Any>): HashMap<Any, Any> {
            val proxy = HashMap<Any, Any>()
            return obj.toMap(proxy)
        }

        override fun fromProxy(proxy: HashMap<Any, Any>): ImmutableMap<Any, Any> {
            return proxy.toImmutableMap()
        }
    }
}
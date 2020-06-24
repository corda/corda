package net.corda.node

import co.paralleluniverse.fibers.Suspendable
import com.github.andrewoma.dexx.kollection.ImmutableMap
import com.github.andrewoma.dexx.kollection.immutableMapOf
import com.github.andrewoma.dexx.kollection.toImmutableMap
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
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

    @Test(timeout = 300_000)
    fun `flow suspend with custom kryo serializer`() {
        val node = mockNetwork.createPartyNode()
        val expected = 5
        val actual = node.startFlow(TestFlow(5)).get()

        Assertions.assertThat(actual).isEqualTo(expected)
    }

    @StartableByRPC
    class TestFlow(private val purchase: Int) : FlowLogic<Int>() {
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

    @Suppress("unused")
    class TestSerializer : SerializationCustomSerializer<ImmutableMap<String, Int>, HashMap<String, Int>> {
        override fun toProxy(obj: ImmutableMap<String, Int>): HashMap<String, Int> {
            val proxy = HashMap<String, Int>()
            return obj.toMap(proxy)
        }

        override fun fromProxy(proxy: HashMap<String, Int>): ImmutableMap<String, Int> {
            return proxy.toImmutableMap()
        }
    }
}
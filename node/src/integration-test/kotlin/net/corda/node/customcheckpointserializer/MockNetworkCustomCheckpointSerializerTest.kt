package net.corda.node.customcheckpointserializer

import co.paralleluniverse.fibers.Suspendable
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
        val expectedReference = DifficultToSerialize.BrokenMapClass<String, Int>()
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
}

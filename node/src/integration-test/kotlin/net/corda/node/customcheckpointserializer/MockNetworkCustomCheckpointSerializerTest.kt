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
        mockNetwork = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(TestCorDapp.getCorDapp())))
    }

    @After
    fun shutdown() {
        mockNetwork.stopNodes()
    }

    @Test(timeout = 300_000)
    fun `flow suspend with custom kryo serializer`() {
        val node = mockNetwork.createPartyNode()
        val expected = 5
        val actual = node.startFlow(TestCorDapp.TestFlowWithDifficultToSerializeLocalVariable(5)).get()

        Assertions.assertThat(actual).isEqualTo(expected)
    }

    @Test(timeout = 300_000)
    fun `check references are restored correctly`() {
        val node = mockNetwork.createPartyNode()
        val expectedReference = DifficultToSerialize.BrokenMapClass<String, Int>()
        expectedReference.putAll(mapOf("one" to 1))
        val actualReference = node.startFlow(TestCorDapp.TestFlowCheckingReferencesWork(expectedReference)).get()

        Assertions.assertThat(actualReference).isSameAs(expectedReference)
        Assertions.assertThat(actualReference["one"]).isEqualTo(1)
    }

    @Test(timeout = 300_000)
    @Suspendable
    fun `check serialization of interfaces`() {
        val node = mockNetwork.createPartyNode()
        val result = node.startFlow(TestCorDapp.TestFlowWithDifficultToSerializeLocalVariableAsInterface(5)).get()
        Assertions.assertThat(result).isEqualTo(5)
    }

    @Test(timeout = 300_000)
    @Suspendable
    fun `check serialization of abstract classes`() {
        val node = mockNetwork.createPartyNode()
        val result = node.startFlow(TestCorDapp.TestFlowWithDifficultToSerializeLocalVariableAsAbstract(5)).get()
        Assertions.assertThat(result).isEqualTo(5)
    }

    @Test(timeout = 300_000)
    @Suspendable
    fun `check serialization of final classes`() {
        val node = mockNetwork.createPartyNode()
        val result = node.startFlow(TestCorDapp.TestFlowWithDifficultToSerializeLocalVariableAsFinal(5)).get()
        Assertions.assertThat(result).isEqualTo(5)
    }

    @Test(timeout = 300_000)
    @Suspendable
    fun `check PublicKey serializer has not been overridden`() {
        val node = mockNetwork.createPartyNode()
        val result = node.startFlow(TestCorDapp.TestFlowCheckingPublicKeySerializer()).get()
        Assertions.assertThat(result.encoded).isEqualTo(node.info.legalIdentities.first().owningKey.encoded)
    }
}

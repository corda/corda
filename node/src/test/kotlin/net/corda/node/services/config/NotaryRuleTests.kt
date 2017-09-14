package net.corda.node.services.config

import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.getNotaryForState
import net.corda.core.node.services.ServiceInfo
import net.corda.node.internal.StartedNode
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.testing.*
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NotaryRuleTests {
    private lateinit var mockNet: MockNetwork
    private lateinit var networkMapAddress: SingleMessageRecipient

    class DummyState
    class DummyState1

    @Before
    fun setup() {
        mockNet = MockNetwork(threadPerNode = false)
        val notaryService = ServiceInfo(ValidatingNotaryService.type, DUMMY_NOTARY.name)
        val notaryNode = mockNet.createNode(
                legalName = MINI_CORP.name,
                overrideServices = mapOf(notaryService to DUMMY_NOTARY_KEY),
                advertisedServices = *arrayOf(ServiceInfo(NetworkMapService.type), notaryService))
        networkMapAddress = notaryNode.network.myAddress
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `one notary for all state types`() {
        val rules = listOf(
                NotaryRule("*", DUMMY_NOTARY.name)
        )
        val partyNode = createNode(rules)

        assertEquals(DUMMY_NOTARY, partyNode.services.getNotaryForState<DummyState>())
    }

    @Test
    fun `notary for a specific state type`() {
        val rules = listOf(
                NotaryRule(DummyState::class.java.canonicalName, DUMMY_NOTARY.name)
        )
        val partyNode = createNode(rules)

        assertEquals(DUMMY_NOTARY, partyNode.services.getNotaryForState<DummyState>())
        assertFailsWith<IllegalStateException> { partyNode.services.getNotaryForState<DummyState1>() }
    }

    @Test
    fun `notary for wildcard state types`() {
        val rules = listOf(
                NotaryRule(DummyState::class.java.canonicalName + "*", DUMMY_NOTARY.name)
        )
        val partyNode = createNode(rules)

        assertEquals(DUMMY_NOTARY, partyNode.services.getNotaryForState<DummyState>())
        assertEquals(DUMMY_NOTARY, partyNode.services.getNotaryForState<DummyState1>())
    }

    @Test
    fun `different notary per state type`() {
        val notaryService2 = ServiceInfo(ValidatingNotaryService.type, DUMMY_BANK_A.name)
        mockNet.createNode(
                networkMapAddress = networkMapAddress,
                legalName = MEGA_CORP.name,
                overrideServices = mapOf(notaryService2 to DUMMY_BANK_A_KEY),
                advertisedServices = notaryService2
        )
        val rules = listOf(
                NotaryRule(DummyState::class.java.canonicalName, DUMMY_NOTARY.name),
                NotaryRule(DummyState1::class.java.canonicalName, DUMMY_BANK_A.name)
        )
        val partyNode = createNode(rules)

        assertEquals(DUMMY_NOTARY, partyNode.services.getNotaryForState<DummyState>())
        assertEquals(DUMMY_BANK_A, partyNode.services.getNotaryForState<DummyState1>())
    }

    /**
     * Creates a party node with provided notary rules, and runs the network so the network
     * map cache gets populated with all notary nodes.
     */
    private fun createNode(rules: List<NotaryRule>): StartedNode<MockNetwork.MockNode> {
        val partyNode = mockNet.createNode(
                networkMapAddress = networkMapAddress,
                configOverrides = {
                    whenever(it.notaryRules).thenReturn(rules)
                }
        )
        mockNet.runNetwork()
        return partyNode
    }
}

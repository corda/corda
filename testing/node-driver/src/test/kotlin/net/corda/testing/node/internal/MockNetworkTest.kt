package net.corda.testing.node.internal

import net.corda.testing.core.*
import net.corda.testing.node.MockNetwork
import org.assertj.core.api.Assertions.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class MockNetworkTest {
    private companion object {
        private const val NODE_ID = 101
    }
    private lateinit var mockNetwork: MockNetwork

    @Before
    fun setup() {
        mockNetwork = MockNetwork(cordappPackages = emptyList())
    }

    @After
    fun done() {
        mockNetwork.stopNodes()
    }

    @Test
    fun `finding a started node`() {
        val unstarted = mockNetwork.createUnstartedNode(DUMMY_BANK_A_NAME, forcedID = NODE_ID)
        mockNetwork.startNodes()

        val started = mockNetwork.findStartedNode(unstarted)
        assertEquals(NODE_ID, started.id)
        assertEquals(DUMMY_BANK_A_NAME, started.info.identityFromX500Name(DUMMY_BANK_A_NAME).name)
        assertFailsWith<IllegalArgumentException> { started.info.identityFromX500Name(DUMMY_BANK_B_NAME) }
    }

    @Test
    fun `failing to find a node`() {
        val ex = assertFailsWith<NoSuchElementException>{ mockNetwork.findStartedNode(NODE_ID) }
        assertThat(ex).hasMessage("No node with ID=$NODE_ID")
    }
}
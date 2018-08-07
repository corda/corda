package net.corda.testing.node

import net.corda.testing.core.*
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
    fun `with a started node`() {
        val unstarted = mockNetwork.createUnstartedNode(DUMMY_BANK_A_NAME, forcedID = NODE_ID)
        assertFalse(unstarted.isStarted)

        mockNetwork.startNodes()
        assertTrue(unstarted.isStarted)

        val started = unstarted.started
        assertEquals(NODE_ID, started.id)
        assertEquals(DUMMY_BANK_A_NAME, started.info.identityFromX500Name(DUMMY_BANK_A_NAME).name)
        assertFailsWith<IllegalArgumentException> { started.info.identityFromX500Name(DUMMY_BANK_B_NAME) }
    }

    @Test
    fun `with an unstarted node`() {
        val unstarted = mockNetwork.createUnstartedNode(DUMMY_BANK_A_NAME, forcedID = NODE_ID)
        val ex = assertFailsWith<IllegalStateException> { unstarted.started }
        assertThat(ex).hasMessage("Node ID=$NODE_ID is not running")
    }
}
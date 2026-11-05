package net.corda.testing.javaxpersistence

import com.example.javaxcordapp.CountJavaxTestStatesFlow
import com.example.javaxcordapp.CreateJavaxTestStateFlow
import com.example.javaxcordapp.JavaxTestState
import com.example.javaxcordapp.PersistentJavaxTestState
import com.example.javaxtest.findJavaxTestStateLabels
import net.corda.core.flows.FlowLogic
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria.VaultCustomQueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.cordappWithPackages
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * A MockNetwork node running the flows of a CorDapp built against `javax.persistence` (`com.example.javaxcordapp`, built for Corda 4.12),
 * including one whose `withEntityManager` block and suspendable helper are typed on the `javax.persistence` API, under the javax
 * persistence test agent.
 */
class JavaxPersistenceMockNetworkTest {
    private lateinit var mockNet: MockNetwork
    private lateinit var alice: StartedMockNode

    @Before
    fun start() {
        mockNet = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(cordappWithPackages("com.example.javaxcordapp"))))
        alice = mockNet.createPartyNode(ALICE_NAME)
    }

    @After
    fun stop() {
        mockNet.stopNodes()
    }

    private fun <T> run(flow: FlowLogic<T>): T {
        val future = alice.startFlow(flow)
        mockNet.runNetwork()
        return future.getOrThrow()
    }

    @Test(timeout = 300_000)
    fun `the flows of a javax-only CorDapp record a queryable state and query it through withEntityManager`() {
        val stx = run(CreateJavaxTestStateFlow("archived"))
        val state = stx.tx.outputsOfType<JavaxTestState>().single()
        assertThat(state.label).isEqualTo("archived")

        assertThat(run(CountJavaxTestStatesFlow("archived"))).isEqualTo(1L)
        assertThat(run(CountJavaxTestStatesFlow("pending"))).isEqualTo(0L)

        alice.transaction {
            val byRef = VaultCustomQueryCriteria(builder { PersistentJavaxTestState::ref.equal(state.ref) })
            assertThat(alice.services.vaultService.queryBy<JavaxTestState>(byRef).states.map { it.state.data }).containsExactly(state)
            // Test code typed on the javax.persistence API (a withEntityManager block in a test) is converted too.
            assertThat(alice.services.findJavaxTestStateLabels()).containsExactly("archived")
        }
    }
}

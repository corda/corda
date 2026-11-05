package net.corda.testing.javaxpersistence

import com.example.javaxcordapp.CountJavaxTestStatesFlow
import com.example.javaxcordapp.CreateJavaxTestStateFlow
import com.example.javaxcordapp.JavaxTestState
import com.example.javaxcordapp.PersistentJavaxTestState
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.vault.QueryCriteria.VaultCustomQueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.cordappWithPackages
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * An in-process driver node (which, like MockNetwork, has the CorDapp's classes on the test classpath) running the flows of a CorDapp
 * built against `javax.persistence`, under the javax persistence test agent. An out-of-process node needs no agent: it loads the
 * CorDapp through the node's converting class loader.
 */
class JavaxPersistenceDriverTest {
    @Test(timeout = 300_000)
    fun `an in-process driver node runs the flows of a javax-only CorDapp`() {
        driver(DriverParameters(
                startNodesInProcess = true,
                cordappsForAllNodes = listOf(cordappWithPackages("com.example.javaxcordapp"))
        )) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val stx = alice.rpc.startFlow(::CreateJavaxTestStateFlow, "archived").returnValue.getOrThrow()
            val state = stx.tx.outputsOfType<JavaxTestState>().single()
            assertThat(alice.rpc.startFlow(::CountJavaxTestStatesFlow, "archived").returnValue.getOrThrow()).isEqualTo(1L)
            val byRef = VaultCustomQueryCriteria(builder { PersistentJavaxTestState::ref.equal(state.ref) })
            assertThat(alice.rpc.vaultQueryBy<JavaxTestState>(byRef).states.map { it.state.data }).containsExactly(state)
        }
    }
}

package net.corda.node.services

import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.flows.djvm.NonDeterministicFlow
import net.corda.node.DeterministicSourcesRule
import net.corda.node.internal.djvm.DeterministicVerificationException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.cordappsForPackages
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.ClassRule
import org.junit.Test
import org.junit.jupiter.api.assertThrows

class DeterministicContractVerifyTest {
    companion object {
        @ClassRule
        @JvmField
        val djvmSources = DeterministicSourcesRule()
    }

    @Test
    fun `test DJVM rejects non-deterministic contract`() {
        driver(DriverParameters(
            portAllocation = incrementalPortAllocation(),
            startNodesInProcess = false,
            notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = true)),
            cordappsForAllNodes = cordappsForPackages(
                "net.corda.contracts.djvm",
                "net.corda.flows.djvm"
            ),
            djvmBootstrapSource = djvmSources.bootstrap,
            djvmCordaSource = djvmSources.corda
        )) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val ex = assertThrows<DeterministicVerificationException> {
                alice.rpc.startFlow(::NonDeterministicFlow, alice.nodeInfo.singleIdentity()).returnValue.getOrThrow()
            }
            assertThat(ex)
                .hasMessageStartingWith("NoSuchMethodError: ")
                .hasMessageContaining(" sandbox.java.time.Instant.now()")
        }
    }
}

package net.corda.node.services

import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.node.DeterministicSourcesRule
import net.corda.node.services.config.NodeConfiguration
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.findCordapp
import org.junit.ClassRule
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow

@Suppress("FunctionName")
class DeterministicCashIssueAndPaymentTest {
    companion object {
        private val logger = loggerFor<DeterministicCashIssueAndPaymentTest>()

        private val configOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
        private val CASH_AMOUNT = 500.DOLLARS

        @ClassRule
        @JvmField
        val djvmSources = DeterministicSourcesRule()

        fun parametersFor(djvmSources: DeterministicSourcesRule, runInProcess: Boolean = false): DriverParameters {
            return DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = runInProcess,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, startInProcess = runInProcess, validating = true)),
                notaryCustomOverrides = configOverrides,
                cordappsForAllNodes = listOf(
                    findCordapp("net.corda.finance.contracts"),
                    findCordapp("net.corda.finance.workflows")
                ),
                djvmBootstrapSource = djvmSources.bootstrap,
                djvmCordaSource = djvmSources.corda
            )
        }
    }

    @Test(timeout = 300_000)
    fun `test DJVM can issue cash`() {
        val reference = OpaqueBytes.of(0x01)
        driver(parametersFor(djvmSources)) {
            val alice = startNode(providedName = ALICE_NAME, customOverrides = configOverrides).getOrThrow()
            val aliceParty = alice.nodeInfo.singleIdentity()
            val notaryParty = notaryHandles.single().identity
            val txId = assertDoesNotThrow {
                alice.rpc.startFlow(::CashIssueAndPaymentFlow,
                    CASH_AMOUNT,
                    reference,
                    aliceParty,
                    false,
                    notaryParty
                ).use { flowHandle ->
                    flowHandle.returnValue.getOrThrow()
                }
            }
            logger.info("TX-ID: {}", txId)
        }
    }
}

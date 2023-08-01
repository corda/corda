package net.corda.node

import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultTrackBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.node.services.config.NodeConfiguration
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.findCordapp
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals

/**
 * Execute a flow with sub-flows, including the finality flow.
 * This operation should checkpoint, and have its checkpoint restored.
 */
@Suppress("FunctionName")
class CashIssueAndPaymentTest {
    companion object {
        private val logger = loggerFor<CashIssueAndPaymentTest>()

        private val configOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
        private val CASH_AMOUNT = 500.DOLLARS

        fun parametersFor(runInProcess: Boolean = false): DriverParameters {
            return DriverParameters(
                systemProperties = mapOf("co.paralleluniverse.fibers.verifyInstrumentation" to "false"),
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = runInProcess,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, startInProcess = runInProcess, validating = true)),
                notaryCustomOverrides = configOverrides,
                cordappsForAllNodes = listOf(
                    findCordapp("net.corda.finance.contracts"),
                    findCordapp("net.corda.finance.workflows")
                )
            )
        }
    }

    @Test(timeout = 300_000)
    fun `test can issue cash`() {
        driver(parametersFor()) {
            val alice = startNode(providedName = ALICE_NAME, customOverrides = configOverrides).getOrThrow()
            val aliceParty = alice.nodeInfo.singleIdentity()
            val notaryParty = notaryHandles.single().identity
            val result = assertDoesNotThrow {
                alice.rpc.startFlow(::CashIssueAndPaymentFlow,
                    CASH_AMOUNT,
                    OpaqueBytes.of(0x01),
                    aliceParty,
                    false,
                    notaryParty
                ).use { flowHandle ->
                    flowHandle.returnValue.getOrThrow()
                }
            }
            logger.info("TXN={}, recipient={}", result.stx, result.recipient)
        }
    }

    @Test(timeout = 300_000)
    fun `test can issue cash and see consumming transaction id in rpc client`() {
        driver(parametersFor()) {
            val alice = startNode(providedName = ALICE_NAME, customOverrides = configOverrides).getOrThrow()
            val aliceParty = alice.nodeInfo.singleIdentity()
            val notaryParty = notaryHandles.single().identity
            val result = assertDoesNotThrow {

                val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.CONSUMED)
                val (_, vaultUpdates) = alice.rpc.vaultTrackBy<Cash.State>(criteria = criteria, paging = PageSpecification(DEFAULT_PAGE_NUM))
                val updateLatch = CountDownLatch(1)
                vaultUpdates.subscribe { update ->
                    val consumedRef = update.consumed.single().ref
                    assertEquals( update.produced.single().ref.txhash, update.consumingTxIds[consumedRef] )
                    updateLatch.countDown()
                }
                val flowRet = alice.rpc.startFlow(::CashIssueAndPaymentFlow,
                        CASH_AMOUNT,
                        OpaqueBytes.of(0x01),
                        aliceParty,
                        false,
                        notaryParty
                ).use { flowHandle ->
                    flowHandle.returnValue.getOrThrow()
                }
                updateLatch.await()
                flowRet
            }
            logger.info("TXN={}, recipient={}", result.stx, result.recipient)
        }
    }
}

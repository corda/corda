package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByService
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.ServiceLifecycleEvent
import net.corda.core.node.services.ServiceLifecycleObserver
import net.corda.core.node.services.Vault
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.AbstractCashFlow
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.testing.common.internal.eventually
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.After
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The idea of this test is upon start-up of the node check if cash been already issued and if not issue under certain reference.
 * If state is already present - do nothing.
 */
class CordaServiceIssueOnceAtStartupTests {

    companion object {
        private val armedPropName = this::class.java.enclosingClass.name + "-armed"
        private val logger = contextLogger()
        private val tempFilePropertyName = this::class.java.enclosingClass.name + "-tmpFile"
        private val tmpFile = File.createTempFile(tempFilePropertyName, null)
        private const val vaultQueryExecutedMarker = "VaultQueryExecuted"
        private const val sentFlowMarker = "SentFlow"
    }

    @Test(timeout=300_000)
	fun test() {
        driver(DriverParameters(startNodesInProcess = false, cordappsForAllNodes = FINANCE_CORDAPPS + enclosedCordapp(), inMemoryDB = false,
                systemProperties = mapOf(armedPropName to "true", tempFilePropertyName to tmpFile.absolutePath))) {
            var node = startNode(providedName = ALICE_NAME).getOrThrow()
            var page: Vault.Page<Cash.State>?
            eventually(duration = 10.seconds) {
                page = node.rpc.vaultQuery(Cash.State::class.java)
                assertTrue(page!!.states.isNotEmpty())
                assertEquals(listOf(vaultQueryExecutedMarker, sentFlowMarker), tmpFile.readLines(), "First start tracker")
            }
            node.stop()
            node = startNode(providedName = ALICE_NAME).getOrThrow()
            eventually(duration = 10.seconds) {
                assertEquals(listOf(vaultQueryExecutedMarker, sentFlowMarker, vaultQueryExecutedMarker),
                        tmpFile.readLines(), "Re-start tracker")
            }
        }
    }

    @After
    fun testDown() {
        System.clearProperty(armedPropName)
        tmpFile.delete()
    }

    @CordaService
    @Suppress("unused")
    class IssueAndPayOnceService(private val services: AppServiceHub) : SingletonSerializeAsToken() {

        init {
            // There are some "greedy" tests that may take on the package of this test and include it into the CorDapp they assemble
            // Without the "secret" property service upon instantiation will be subscribed to lifecycle events which would be unwanted.
            // Also do not do this for Notary
            val myName = services.myInfo.legalIdentities.single().name
            val notaryName = services.networkMapCache.notaryIdentities.firstOrNull()?.name
            if(java.lang.Boolean.getBoolean(armedPropName) && myName != notaryName) {
                services.register(observer = MyServiceLifecycleObserver())
            } else {
                logger.info("Skipping lifecycle events registration for $myName")
            }
        }

        inner class MyServiceLifecycleObserver : ServiceLifecycleObserver {
            override fun onServiceLifecycleEvent(event: ServiceLifecycleEvent) {
                val tmpFile = File(System.getProperty(tempFilePropertyName))
                if (event == ServiceLifecycleEvent.STATE_MACHINE_STARTED) {
                    val queryResult = services.vaultService.queryBy(Cash.State::class.java)
                    if (tmpFile.length() == 0L) {
                        tmpFile.appendText(vaultQueryExecutedMarker)
                    } else {
                        tmpFile.appendText("\n" + vaultQueryExecutedMarker)
                    }

                    if(queryResult.states.isEmpty()) {
                        val issueAndPayResult = services.startFlow(
                                IssueAndPayByServiceFlow(
                                        services.myInfo.legalIdentities.single(), services.networkMapCache.notaryIdentities.single()))
                                .returnValue.getOrThrow()
                        logger.info("Cash issued and paid: $issueAndPayResult")
                        tmpFile.appendText("\n" + sentFlowMarker)
                    }
                }
            }
        }
    }

    /**
     * The only purpose to have this is to be able to have annotation: [StartableByService]
     */
    @StartableByService
    class IssueAndPayByServiceFlow(private val recipient: Party, private val notary: Party) : FlowLogic<AbstractCashFlow.Result>() {
        @Suspendable
        override fun call(): AbstractCashFlow.Result {
            return subFlow(CashIssueAndPaymentFlow(500.DOLLARS,
                    OpaqueBytes.of(0x01),
                    recipient,
                    false,
                    notary))
        }
    }
}
package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByService
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.ServiceLifecycleEvent
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.AbstractCashFlow
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.finance.schemas.CashSchemaV1
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.CustomCordapp
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.After
import org.junit.Test
import kotlin.test.assertNotNull

/**
 * The idea of this test is upon start-up of the node check if cash been already issued and if not issue under certain reference.
 * If state is already present - do nothing.
 */
class CordaServiceIssueOnceAtStartupTests {

    companion object {
        private fun assembleCustomCordapp(): Collection<CustomCordapp> {
            val enclosed = enclosedCordapp()
            // Augment with "finance"
            val cashContracts = Cash.enclosedCordapp()
            val cashFlows = CashIssueAndPaymentFlow.enclosedCordapp()
            val cashSchemas = CashSchemaV1.enclosedCordapp()
            // Merge into a single one
            val mergedCorDapp = listOf(cashContracts, cashFlows, cashSchemas).fold(enclosed) { existing, incoming ->
                existing.copy(
                        packages = existing.packages + incoming.packages,
                        classes = existing.classes + incoming.classes
                )
            }
            // Return with altered name
            return listOf(mergedCorDapp.copy(name = this::class.java.enclosingClass.name + "-cordapp"))
        }

        private val armedPropName = this::class.java.enclosingClass.name + "-armed"

        private val realNodeName = ALICE_NAME
    }

    @Test
    fun test() {
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = assembleCustomCordapp(), inMemoryDB = false)) {
            System.setProperty(armedPropName, "true") // `systemProperties` have no effect on in-process nodes
            val node = startNode(providedName = realNodeName).getOrThrow()
        }
    }

    @After
    fun testDown() {
        System.clearProperty(armedPropName)
    }

    @CordaService
    class IssueAndPayOnceService(private val services: AppServiceHub) : SingletonSerializeAsToken() {

        init {
            // There are some "greedy" tests that may take on the package of this test and include it into the CorDapp they assemble
            // Without the "secret" property service upon instantiation will be subscribed to lifecycle events which would be unwanted.
            // Also do not do this for Notary
            if(java.lang.Boolean.getBoolean(armedPropName) && services.myInfo.legalIdentities.single().name == realNodeName) {
                services.register(func = this::handleEvent)
            }
        }

        private fun handleEvent(event: ServiceLifecycleEvent) {

            when (event) {
                ServiceLifecycleEvent.CORDAPP_STARTED -> {
                    val issueAndPayResult = services.startFlow(
                            IssueAndPayByServiceFlow(
                                    services.myInfo.legalIdentities.single(), services.networkMapCache.notaryIdentities.single()))
                            .returnValue.getOrThrow()
                    assertNotNull(issueAndPayResult)
                }
                else -> {
                    // Do nothing
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
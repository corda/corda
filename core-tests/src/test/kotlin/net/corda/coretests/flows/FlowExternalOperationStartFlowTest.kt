package net.corda.coretests.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.minutes
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.junit.Test

class FlowExternalOperationStartFlowTest : AbstractFlowExternalOperationTest() {

    @Test(timeout = 300_000)
    fun `starting a flow inside of a flow that starts a future will succeed`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            alice.rpc.startFlow(::FlowThatStartsAnotherFlowInAnExternalOperation, bob.nodeInfo.singleIdentity())
                .returnValue.getOrThrow(1.minutes)
            assertHospitalCounters(0, 0)
        }
    }

    @Test(timeout = 300_000)
    fun `multiple flows can be started and their futures joined from inside a flow`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            alice.rpc.startFlow(::ForkJoinFlows, bob.nodeInfo.singleIdentity())
                .returnValue.getOrThrow(1.minutes)
            assertHospitalCounters(0, 0)
        }
    }

    @StartableByRPC
    class FlowThatStartsAnotherFlowInAnExternalOperation(party: Party) : FlowWithExternalProcess(party) {

        @Suspendable
        override fun testCode(): Any {
            return await(
                ExternalAsyncOperation(serviceHub) { serviceHub, _ ->
                    serviceHub.cordaService(FutureService::class.java).startFlow(party)
                }.also { log.info("Result - $it") }
            )
        }
    }

    @StartableByRPC
    class ForkJoinFlows(party: Party) : FlowWithExternalProcess(party) {

        @Suspendable
        override fun testCode(): Any {
            return await(
                ExternalAsyncOperation(serviceHub) { serviceHub, _ ->
                    serviceHub.cordaService(FutureService::class.java).startFlows(party)
                }.also { log.info("Result - $it") }
            )
        }
    }
}
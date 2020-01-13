package net.corda.coretests.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.junit.Test
import kotlin.test.assertEquals

class FlowExternalResultStartFlowTest : AbstractFlowExternalResultTest() {

    @Test
    fun `starting a flow inside of a flow that starts a future will succeed`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            alice.rpc.startFlow(::FlowThatStartsAnotherFlowInABackgroundProcess, bob.nodeInfo.singleIdentity())
                .returnValue.getOrThrow(40.seconds)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `multiple flows can be started and their futures joined from inside a flow`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            alice.rpc.startFlow(::ForkJoinFlows, bob.nodeInfo.singleIdentity())
                .returnValue.getOrThrow(40.seconds)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @StartableByRPC
    class FlowThatStartsAnotherFlowInABackgroundProcess(party: Party) : FlowWithExternalProcess(party) {

        @Suspendable
        override fun testCode(): Any {
            return await(
                ExternalFuture(serviceHub) { serviceHub, _ ->
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
                ExternalFuture(serviceHub) { serviceHub, _ ->
                    serviceHub.cordaService(FutureService::class.java).startFlows(party)
                }.also { log.info("Result - $it") }
            )
        }
    }
}
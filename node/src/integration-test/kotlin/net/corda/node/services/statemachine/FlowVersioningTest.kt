package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.node.NodeBasedTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class FlowVersioningTest : NodeBasedTest() {
    @Test
    fun `getFlowContext returns the platform version for core flows`() {
        val (alice, bob) = listOf(
                startNode(ALICE.name, platformVersion = 2),
                startNode(BOB.name, platformVersion = 3)).transpose().getOrThrow()
        bob.installCoreFlow(PretendInitiatingCoreFlow::class, ::PretendInitiatedCoreFlow)
        val (alicePlatformVersionAccordingToBob, bobPlatformVersionAccordingToAlice) = alice.services.startFlow(
                PretendInitiatingCoreFlow(bob.info.legalIdentity)).resultFuture.getOrThrow()
        assertThat(alicePlatformVersionAccordingToBob).isEqualTo(2)
        assertThat(bobPlatformVersionAccordingToAlice).isEqualTo(3)
    }

    @InitiatingFlow
    private class PretendInitiatingCoreFlow(val initiatedParty: Party) : FlowLogic<Pair<Int, Int>>() {
        @Suspendable
        override fun call(): Pair<Int, Int> {
            return Pair(
                    receive<Int>(initiatedParty).unwrap { it },
                    getFlowContext(initiatedParty).flowVersion
            )
        }
    }

    private class PretendInitiatedCoreFlow(val initiatingParty: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = send(initiatingParty, getFlowContext(initiatingParty).flowVersion)
    }

}
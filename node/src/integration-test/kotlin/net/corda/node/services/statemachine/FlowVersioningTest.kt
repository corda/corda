package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.NodeBasedTest
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test

class FlowVersioningTest : NodeBasedTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME.toDatabaseSchemaName(), BOB_NAME.toDatabaseSchemaName())
    }

    @Test
    fun `getFlowContext returns the platform version for core flows`() {
        val alice = startNode(ALICE_NAME, platformVersion = 2)
        val bob = startNode(BOB_NAME, platformVersion = 3)
        bob.node.installCoreFlow(PretendInitiatingCoreFlow::class, ::PretendInitiatedCoreFlow)
        val (alicePlatformVersionAccordingToBob, bobPlatformVersionAccordingToAlice) = alice.services.startFlow(
                PretendInitiatingCoreFlow(bob.info.singleIdentity())).resultFuture.getOrThrow()
        assertThat(alicePlatformVersionAccordingToBob).isEqualTo(2)
        assertThat(bobPlatformVersionAccordingToAlice).isEqualTo(3)
    }

    @InitiatingFlow
    private class PretendInitiatingCoreFlow(val initiatedParty: Party) : FlowLogic<Pair<Int, Int>>() {
        @Suspendable
        override fun call(): Pair<Int, Int> {
            // Execute receive() outside of the Pair constructor to avoid Kotlin/Quasar instrumentation bug.
            val session = initiateFlow(initiatedParty)
            val alicePlatformVersionAccordingToBob = session.receive<Int>().unwrap { it }
            return Pair(
                    alicePlatformVersionAccordingToBob,
                    session.getCounterpartyFlowInfo().flowVersion
            )
        }
    }

    private class PretendInitiatedCoreFlow(val otherSideSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = otherSideSession.send(otherSideSession.getCounterpartyFlowInfo().flowVersion)
    }
}
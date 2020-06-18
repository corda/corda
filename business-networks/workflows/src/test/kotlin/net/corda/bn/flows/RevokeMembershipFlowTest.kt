package net.corda.bn.flows

import net.corda.bn.states.MembershipState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RevokeMembershipFlowTest : MembershipManagementFlowTest(numberOfAuthorisedMembers = 1, numberOfRegularMembers = 2) {

    @Test(timeout = 300_000)
    fun `revoke membership flow should fail if membership with given ID doesn't exist`() {
        val authorisedMember = authorisedMembers.first()

        val invalidMembershipId = UniqueIdentifier()
        assertFailsWith<FlowException>("Membership state with $invalidMembershipId linear ID doesn't exist") {
            runRevokeMembershipFlow(authorisedMember, invalidMembershipId)
        }
    }

    @Test(timeout = 300_000)
    fun `revoke membership flow should fail if initiator is not part of the business network or if its membership is not active`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()
        val nonMember = regularMembers[1]

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId
        val membership = runRequestMembershipFlow(regularMember, authorisedMember, networkId).tx.outputStates.single() as MembershipState

        assertFailsWith<FlowException>("Initiator is not member of a business network") {
            runRevokeMembershipFlow(nonMember, membership.linearId)
        }

        runRequestAndSuspendMembershipFlow(nonMember, authorisedMember, networkId)
        assertFailsWith<FlowException>("Initiator's membership is not active") {
            runRevokeMembershipFlow(nonMember, membership.linearId)
        }
    }

    @Test(timeout = 300_000)
    fun `revoke membership flow happy path`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId
        val membership = runRequestAndActivateMembershipFlows(regularMember, authorisedMember, networkId).tx.outputStates.single() as MembershipState

        runRevokeMembershipFlow(authorisedMember, membership.linearId).apply {
            assertEquals(1, tx.inputs.size)
            assertTrue(tx.outputs.isEmpty())
            verifyRequiredSignatures()
        }

        // also check ledgers
        listOf(authorisedMember, regularMember).forEach { member ->
            getAllMembershipsFromVault(member, networkId).single().apply {
                assertEquals(authorisedMember.identity(), identity)
            }
        }
    }
}
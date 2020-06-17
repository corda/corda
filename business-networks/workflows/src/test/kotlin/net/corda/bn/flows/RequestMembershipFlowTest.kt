package net.corda.bn.flows

import net.corda.bn.contracts.MembershipContract
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.flows.FlowException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RequestMembershipFlowTest : MembershipManagementFlowTest(numberOfAuthorisedMembers = 1, numberOfRegularMembers = 2) {

    @Test(timeout = 300_000)
    fun `request membership flow should fail if initiator is already business network member`() {
        val authorisedMember = authorisedMembers.first()

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId

        assertFailsWith<FlowException>("Initiator is already a member of Business Network with $networkId ID") {
            runRequestMembershipFlow(authorisedMember, authorisedMember, networkId)
        }
    }

    @Test(timeout = 300_000)
    fun `request membership flow should fail if receiver is not member of a business network`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId
        val invalidNetworkId = "invalid-network-id"

        assertFailsWith<FlowException>("Receiver is not member of a business network") {
            runRequestMembershipFlow(regularMember, regularMember, networkId)
        }
        assertFailsWith<FlowException>("Receiver is not member of a business network") {
            runRequestMembershipFlow(regularMember, authorisedMember, invalidNetworkId)
        }
    }

    @Test(timeout = 300_000)
    fun `request membership flow should fail if receiver's membership is not active`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()
        val pendingMember = regularMembers[1]

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId
        runRequestMembershipFlow(pendingMember, authorisedMember, networkId)

        assertFailsWith<FlowException>("Receiver's membership is not active") {
            runRequestMembershipFlow(regularMember, pendingMember, networkId)
        }
    }

    @Test(timeout = 300_000)
    fun `request membership flow happy path`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId

        val (membership, command) = runRequestMembershipFlow(regularMember, authorisedMember, networkId).run {
            assertTrue(tx.inputs.isEmpty())
            verifyRequiredSignatures()
            tx.outputs.single() to tx.commands.single()
        }

        membership.apply {
            assertEquals(MembershipContract.CONTRACT_NAME, contract)
            assertTrue(data is MembershipState)
            val data = data as MembershipState
            assertEquals(regularMember.identity(), data.identity)
            assertEquals(networkId, data.networkId)
            assertEquals(MembershipStatus.PENDING, data.status)
        }
        assertTrue(command.value is MembershipContract.Commands.Request)

        // also check ledgers
        getAllMembershipsFromVault(authorisedMember, networkId).apply {
            assertEquals(2, size)
            assertTrue(any { it.identity == authorisedMember.identity() })
            assertTrue(any { it.identity == regularMember.identity() })
        }
        getAllMembershipsFromVault(regularMember, networkId).single().apply {
            assertEquals(regularMember.identity(), identity)
        }
    }
}
package net.corda.bn.flows

import net.corda.bn.states.GroupState
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.UniqueIdentifier
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DeleteGroupFlowTest : MembershipManagementFlowTest(numberOfAuthorisedMembers = 1, numberOfRegularMembers = 1) {

    @Test(timeout = 300_000)
    fun `delete group flow should fail if group with given ID doesn't exits`() {
        val authorisedMember = authorisedMembers.first()

        val invalidGroupId = UniqueIdentifier()
        assertFailsWith<BusinessNetworkGroupNotFoundException> { runDeleteGroupFlow(authorisedMember, invalidGroupId) }
    }

    @Test(timeout = 300_000)
    fun `delete group flow should fail if invalid notary argument is provided`() {
        val authorisedMember = authorisedMembers.first()

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId
        val groupId = (runCreateGroupFlow(authorisedMember, networkId).tx.outputStates.single() as GroupState).linearId

        assertFailsWith<IllegalArgumentException> { runDeleteGroupFlow(authorisedMember, groupId, authorisedMember.identity()) }
    }

    @Test(timeout = 300_000)
    fun `delete group flow should fail if initiator is not part of the business network, its membership is not active or is not authorised`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId

        runRequestAndSuspendMembershipFlow(regularMember, authorisedMember, networkId).apply {
            val membership = tx.outputStates.single() as MembershipState
            val group = runCreateGroupFlow(authorisedMember, networkId, additionalParticipants = setOf(membership.linearId)).tx.outputStates.single() as GroupState
            val groupId = group.linearId

            assertFailsWith<IllegalMembershipStatusException> { runDeleteGroupFlow(regularMember, groupId) }

            runActivateMembershipFlow(authorisedMember, membership.linearId)
            assertFailsWith<MembershipAuthorisationException> { runDeleteGroupFlow(regularMember, groupId) }
        }
    }

    @Test(timeout = 300_000)
    fun `delete group flow should fail if any member remains without any group participation`() {
        val authorisedMember = authorisedMembers.first()

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId
        val groupId = getAllGroupsFromVault(authorisedMember, networkId).single().linearId
        assertFailsWith<MembershipMissingGroupParticipationException> { runDeleteGroupFlow(authorisedMember, groupId) }
    }

    @Test(timeout = 300_000)
    fun `delete group flow happy path`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val authorisedMembership = runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState
        val networkId = authorisedMembership.networkId
        val regularMembership = runRequestAndActivateMembershipFlows(regularMember, authorisedMember, networkId).tx.outputStates.single() as MembershipState

        val group = runCreateGroupFlow(authorisedMember, networkId, additionalParticipants = setOf(regularMembership.linearId)).tx.outputStates.single() as GroupState
        runDeleteGroupFlow(authorisedMember, group.linearId).apply {
            assertEquals(1, tx.inputs.size)
            assertTrue(tx.outputs.isEmpty())
            verifyRequiredSignatures()
        }

        // also check ledgers
        listOf(authorisedMember, regularMember).forEach { member ->
            val ledgerGroup = getAllGroupsFromVault(member, networkId).single()
            assertNotEquals(group, ledgerGroup)
        }
    }
}
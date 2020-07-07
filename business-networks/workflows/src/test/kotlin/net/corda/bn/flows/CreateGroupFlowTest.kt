package net.corda.bn.flows

import net.corda.bn.contracts.GroupContract
import net.corda.bn.states.GroupState
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.UniqueIdentifier
import org.junit.Test
import java.lang.IllegalStateException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CreateGroupFlowTest : MembershipManagementFlowTest(numberOfAuthorisedMembers = 1, numberOfRegularMembers = 1) {

    @Test(timeout = 300_000)
    fun `create group flow should fail if business network doesn't exist`() {
        val authorisedMember = authorisedMembers.first()
        val invalidNetworkId = "invalid-network-id"

        assertFailsWith<BusinessNetworkNotFoundException> { runCreateGroupFlow(authorisedMember, invalidNetworkId) }
    }

    @Test(timeout = 300_000)
    fun `create group flow should fail when trying to create group with already existing group ID`() {
        val authorisedMember = authorisedMembers.first()

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId
        val groupId = getAllGroupsFromVault(authorisedMember, networkId).single().linearId
        assertFailsWith<DuplicateBusinessNetworkGroupException> { runCreateGroupFlow(authorisedMember, networkId, groupId) }
    }

    @Test(timeout = 300_000)
    fun `create group flow should fail if initiator is not part of the business network, its membership is not active or is not authorised`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId

        runRequestAndSuspendMembershipFlow(regularMember, authorisedMember, networkId).apply {
            val membership = tx.outputStates.single() as MembershipState

            assertFailsWith<IllegalMembershipStatusException> { runCreateGroupFlow(regularMember, networkId) }

            runActivateMembershipFlow(authorisedMember, membership.linearId)
            assertFailsWith<MembershipAuthorisationException> { runCreateGroupFlow(regularMember, networkId) }
        }
    }

    @Test(timeout = 300_000)
    fun `create group flow should fail if invalid notary argument is provided`() {
        val authorisedMember = authorisedMembers.first()

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId
        assertFailsWith<IllegalStateException> { runCreateGroupFlow(authorisedMember, networkId, notary = authorisedMember.identity()) }
    }

    @Test(timeout = 300_000)
    fun `create group flow should fail if any of the additional participants is not member of business network`() {
        val authorisedMember = authorisedMembers.first()

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId
        assertFailsWith<MembershipNotFoundException> { runCreateGroupFlow(authorisedMember, networkId, additionalParticipants = setOf(UniqueIdentifier())) }
    }

    @Test(timeout = 300_000)
    fun `create group flow should fail if any of the additional participants memberships is in pending status`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId
        val membership = runRequestMembershipFlow(regularMember, authorisedMember, networkId).tx.outputStates.single() as MembershipState
        assertFailsWith<IllegalMembershipStatusException> { runCreateGroupFlow(authorisedMember, networkId, additionalParticipants = setOf(membership.linearId)) }
    }

    @Test(timeout = 300_000)
    fun `create group flow happy path`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val authorisedMembership = runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState
        val networkId = authorisedMembership.networkId
        val regularMembership = runRequestAndActivateMembershipFlows(regularMember, authorisedMember, networkId).tx.outputStates.single() as MembershipState

        val groupId = UniqueIdentifier()
        val groupName = "group-name"
        val (group, command) = runCreateGroupFlow(authorisedMember, networkId, groupId, groupName, setOf(regularMembership.linearId)).run {
            assertTrue(tx.inputs.isEmpty())
            verifyRequiredSignatures()
            tx.outputs.single() to tx.commands.single()
        }

        group.apply {
            assertEquals(GroupContract.CONTRACT_NAME, contract)
            assertTrue(data is GroupState)
            val data = data as GroupState
            assertEquals(networkId, data.networkId)
            assertEquals(groupName, data.name)
            assertEquals(groupId, data.linearId)
            assertEquals(setOf(authorisedMember.identity(), regularMember.identity()), data.participants.toSet())
        }
        assertTrue(command.value is GroupContract.Commands.Create)

        // also check ledgers
        listOf(authorisedMember, regularMember).forEach { member ->
            getAllGroupsFromVault(member, networkId).run {
                assertEquals(2, size)
                single { it.linearId == groupId }
            }.apply {
                assertEquals(2, participants.size, "Vault size assertion failed for ${member.identity()}")
                assertTrue(participants.any { it == authorisedMember.identity() }, "Expected to have ${authorisedMember.identity()} in new group of ${member.identity()} vault")
                assertTrue(participants.any { it == regularMember.identity() }, "Expected to have ${regularMember.identity()} in new group of ${member.identity()} vault")
            }
        }
    }
}
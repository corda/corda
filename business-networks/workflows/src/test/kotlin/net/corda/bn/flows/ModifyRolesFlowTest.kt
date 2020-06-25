package net.corda.bn.flows

import net.corda.bn.contracts.MembershipContract
import net.corda.bn.states.BNORole
import net.corda.bn.states.MemberRole
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.UniqueIdentifier
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModifyRolesFlowTest : MembershipManagementFlowTest(numberOfAuthorisedMembers = 1, numberOfRegularMembers = 2) {

    @Test(timeout = 300_000)
    fun `modify roles flow should fail if membership with given ID doesn't exist`() {
        val authorisedMember = authorisedMembers.first()

        val invalidMembershipId = UniqueIdentifier()
        assertFailsWith<MembershipNotFoundException> { runModifyRolesFlow(authorisedMember, invalidMembershipId, setOf(BNORole())) }
    }

    @Test(timeout = 300_000)
    fun `modify roles flow should fail if initiator is not part of the business network, its membership is not active or is not authorised`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()
        val nonMember = regularMembers[1]

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId
        val membership = runRequestMembershipFlow(regularMember, authorisedMember, networkId).tx.outputStates.single() as MembershipState

        assertFailsWith<MembershipNotFoundException> { runModifyRolesFlow(nonMember, membership.linearId, setOf(BNORole())) }

        runRequestAndSuspendMembershipFlow(nonMember, authorisedMember, networkId).apply {
            val membership = tx.outputStates.single() as MembershipState

            // make `nonMember` authorised to modify membership so he fetches all members to be modified
            runModifyRolesFlow(authorisedMember, membership.linearId, setOf(BNORole()))
            assertFailsWith<IllegalMembershipStatusException> { runModifyRolesFlow(nonMember, membership.linearId, setOf(BNORole())) }

            // remove permissions from `nonMember` and activate membership
            runActivateMembershipFlow(authorisedMember, membership.linearId)
            runModifyRolesFlow(authorisedMember, membership.linearId, setOf(MemberRole()))
            assertFailsWith<MembershipAuthorisationException> { runModifyRolesFlow(nonMember, membership.linearId, setOf(BNORole())) }
        }
    }

    @Test(timeout = 300_000)
    fun `modify roles flow should fail if invalid notary argument is provided`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId

        val membership = runRequestMembershipFlow(regularMember, authorisedMember, networkId).tx.outputStates.single() as MembershipState
        assertFailsWith<IllegalArgumentException> { runModifyRolesFlow(authorisedMember, membership.linearId, setOf(BNORole()), authorisedMember.identity()) }
    }

    @Test(timeout = 300_000)
    fun `modify roles flow happy path`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId

        val activatedMembership = runRequestAndActivateMembershipFlows(regularMember, authorisedMember, networkId).tx.outputStates.single() as MembershipState
        val (membership, command) = runModifyRolesFlow(authorisedMember, activatedMembership.linearId, setOf(BNORole())).run {
            assertEquals(1, tx.inputs.size)
            verifyRequiredSignatures()
            tx.outputs.single() to tx.commands.single()
        }

        membership.apply {
            assertEquals(MembershipContract.CONTRACT_NAME, contract)
            assertTrue(data is MembershipState)
            val data = data as MembershipState
            assertEquals(regularMember.identity(), data.identity)
            assertEquals(networkId, data.networkId)
            assertEquals(BNORole(), data.roles.single())
        }
        assertTrue(command.value is MembershipContract.Commands.ModifyRoles)

        // also check ledgers
        listOf(authorisedMember, regularMember).forEach { member ->
            getAllMembershipsFromVault(member, networkId).apply {
                assertEquals(2, size, "Vault size assertion failed for ${member.identity()}")
                assertTrue(any { it.identity == authorisedMember.identity() }, "Expected to have ${authorisedMember.identity()} in ${member.identity()} vault")
                assertTrue(any { it.identity == regularMember.identity() }, "Expected to have ${regularMember.identity()} in ${member.identity()} vault")
            }
        }
    }
}
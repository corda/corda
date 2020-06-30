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

class ModifyAdditionalIdentityFlowTest : MembershipManagementFlowTest(numberOfAuthorisedMembers = 1, numberOfRegularMembers = 2) {

    @Test(timeout = 300_000)
    fun `modify additional identity flow should fail if membership with given ID doesn't exist`() {
        val authorisedMember = authorisedMembers.first()

        val invalidMembershipId = UniqueIdentifier()
        assertFailsWith<MembershipNotFoundException> { runModifyAdditionalIdentityFlow(authorisedMember, invalidMembershipId, DummyIdentity("dummy-identity")) }
    }

    @Test(timeout = 300_000)
    fun `modify additional identity flow should fail if initiator is not part of the business network, its membership is not active or is not authorised`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()
        val nonMember = regularMembers[1]

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId
        val membership = runRequestMembershipFlow(regularMember, authorisedMember, networkId).tx.outputStates.single() as MembershipState

        assertFailsWith<MembershipNotFoundException> { runModifyAdditionalIdentityFlow(nonMember, membership.linearId, DummyIdentity("dummy-identity")) }

        runRequestAndSuspendMembershipFlow(nonMember, authorisedMember, networkId).apply {
            val initiatorMembership = tx.outputStates.single() as MembershipState

            // make `nonMember` authorised to modify membership so he fetches all members to be modified
            runModifyRolesFlow(authorisedMember, initiatorMembership.linearId, setOf(BNORole()))
            assertFailsWith<IllegalMembershipStatusException> { runModifyAdditionalIdentityFlow(nonMember, membership.linearId, DummyIdentity("dummy-identity")) }

            // remove permissions from `nonMember` and activate membership
            runActivateMembershipFlow(authorisedMember, initiatorMembership.linearId)
            runModifyRolesFlow(authorisedMember, initiatorMembership.linearId, setOf(MemberRole()))
            assertFailsWith<MembershipAuthorisationException> { runModifyAdditionalIdentityFlow(nonMember, membership.linearId, DummyIdentity("dummy-identity")) }
        }
    }

    @Test(timeout = 300_000)
    fun `modify additional identity flow should fail if invalid notary argument is provided`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId

        val membership = runRequestMembershipFlow(regularMember, authorisedMember, networkId).tx.outputStates.single() as MembershipState
        assertFailsWith<IllegalArgumentException> { runModifyAdditionalIdentityFlow(authorisedMember, membership.linearId, DummyIdentity("dummy-identity"), authorisedMember.identity()) }
    }

    @Test(timeout = 300_000)
    fun `modify additional identity flow happy path`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId

        val activatedMembership = runRequestAndActivateMembershipFlows(regularMember, authorisedMember, networkId).tx.outputStates.single() as MembershipState
        val (membership, command) = runModifyAdditionalIdentityFlow(authorisedMember, activatedMembership.linearId, DummyIdentity("dummy-identity")).run {
            assertEquals(1, tx.inputs.size)
            verifyRequiredSignatures()
            tx.outputs.single() to tx.commands.single()
        }

        membership.apply {
            assertEquals(MembershipContract.CONTRACT_NAME, contract)
            assertTrue(data is MembershipState)
            val data = data as MembershipState
            assertEquals(regularMember.identity(), data.identity.cordaIdentity)
            assertEquals(networkId, data.networkId)
            assertEquals(DummyIdentity("dummy-identity"), data.identity.additionalIdentity)
        }
        assertTrue(command.value is MembershipContract.Commands.ModifyAdditionalIdentity)

        // also check ledgers
        listOf(authorisedMember, regularMember).forEach { member ->
            getAllMembershipsFromVault(member, networkId).apply {
                assertEquals(2, size, "Vault size assertion failed for ${member.identity()}")
                assertTrue(any { it.identity.cordaIdentity == authorisedMember.identity() }, "Expected to have ${authorisedMember.identity()} in ${member.identity()} vault")
                assertTrue(any { it.identity.cordaIdentity == regularMember.identity() }, "Expected to have ${regularMember.identity()} in ${member.identity()} vault")
            }
        }
    }
}
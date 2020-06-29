package net.corda.bn.flows

import net.corda.bn.contracts.MembershipContract
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.contracts.UniqueIdentifier
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ActivateMembershipFlowTest : MembershipManagementFlowTest(numberOfAuthorisedMembers = 1, numberOfRegularMembers = 2) {

    @Test(timeout = 300_000)
    fun `activate membership flow should fail if membership with given ID doesn't exist`() {
        val authorisedMember = authorisedMembers.first()

        val invalidMembershipId = UniqueIdentifier()
        assertFailsWith<MembershipNotFoundException> { runActivateMembershipFlow(authorisedMember, invalidMembershipId) }
    }

    @Test(timeout = 300_000)
    fun `activate membership flow should fail if initiator is not part of the business network or if its membership is not active`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()
        val nonMember = regularMembers[1]

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId
        val membership = runRequestMembershipFlow(regularMember, authorisedMember, networkId).tx.outputStates.single() as MembershipState

        assertFailsWith<MembershipNotFoundException> { runActivateMembershipFlow(nonMember, membership.linearId) }

        runRequestMembershipFlow(nonMember, authorisedMember, networkId)
        // this ideally has to throw `IllegalMembershipStatusException` but this will only be available after we introduce flow for
        // membership suspension and then we'll be able to have authorised member which has all memberships but cannot activate them
        // since it is suspended.
        assertFailsWith<MembershipNotFoundException> { runActivateMembershipFlow(nonMember, membership.linearId) }
    }

    @Test(timeout = 300_000)
    fun `activate membership flow should fail if invalid notary argument is provided`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId

        val membership = runRequestMembershipFlow(regularMember, authorisedMember, networkId).tx.outputStates.single() as MembershipState
        assertFailsWith<IllegalArgumentException> { runActivateMembershipFlow(authorisedMember, membership.linearId, authorisedMember.identity()) }
    }

    @Test(timeout = 300_000)
    fun `activate membership flow happy path`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId

        val (membership, command) = runRequestAndActivateMembershipFlows(regularMember, authorisedMember, networkId).run {
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
            assertEquals(MembershipStatus.ACTIVE, data.status)
        }
        assertTrue(command.value is MembershipContract.Commands.Activate)

        // also check ledgers
        listOf(authorisedMember, regularMember).forEach { member ->
            getAllMembershipsFromVault(member, networkId).apply {
                assertEquals(2, size)
                assertTrue(any { it.identity == authorisedMember.identity() })
                assertTrue(any { it.identity == regularMember.identity() })
            }
        }
    }
}
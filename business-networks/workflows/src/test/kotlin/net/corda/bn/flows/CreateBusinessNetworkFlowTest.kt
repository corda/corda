package net.corda.bn.flows

import net.corda.bn.contracts.MembershipContract
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateBusinessNetworkFlowTest : MembershipManagementFlowTest(numberOfAuthorisedMembers = 1, numberOfRegularMembers = 0) {

    @Test(timeout = 300_000)
    fun `create business network flow happy path`() {
        val authorisedMember = authorisedMembers.first()
        val (membership, command) = runCreateBusinessNetworkFlow(authorisedMember).run {
            verifyRequiredSignatures()
            tx.outputs.single() to tx.commands.single()
        }

        val networkId = membership.run {
            assertEquals(MembershipContract.CONTRACT_NAME, contract)
            assertTrue(data is MembershipState)
            val data = data as MembershipState
            assertEquals(authorisedMember.identity(), data.identity)
            assertEquals(MembershipStatus.ACTIVE, data.status)

            data.networkId
        }
        assertTrue(command.value is MembershipContract.Commands.Activate)

        // also check ledger
        getAllMembershipsFromVault(authorisedMember, networkId).single().apply {
            assertEquals(authorisedMember.identity(), identity)
        }
    }
}
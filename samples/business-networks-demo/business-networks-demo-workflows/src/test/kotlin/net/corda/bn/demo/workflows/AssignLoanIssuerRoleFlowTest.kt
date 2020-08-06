package net.corda.bn.demo.workflows

import net.corda.bn.contracts.MembershipContract
import net.corda.bn.demo.contracts.LoanIssuerRole
import net.corda.bn.flows.MembershipNotFoundException
import net.corda.bn.states.MembershipState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AssignLoanIssuerRoleFlowTest : LoanFlowTest(numberOfLenders = 1, numberOfBorrowers = 0) {

    @Test(timeout = 300_000)
    fun `assign loan issuer role flow should fail if initiator is not member of business network`() {
        val lender = lenders.first()

        val networkId = "network-id"
        assertFailsWith<MembershipNotFoundException> { runAssignLoanIssuerRole(lender, networkId) }
    }

    @Test(timeout = 300_000)
    fun `assign loan issuer role flow happy path`() {
        val lender = lenders.first()

        val networkId = (runCreateBusinessNetworkFlow(lender).tx.outputStates.single() as MembershipState).networkId
        val (membership, command) = runAssignLoanIssuerRole(lender, networkId).run {
            assertEquals(1, tx.inputs.size)
            verifyRequiredSignatures()
            tx.outputs.single() to tx.commands.single()
        }

        membership.apply {
            assertEquals(MembershipContract.CONTRACT_NAME, membership.contract)
            assertTrue(data is MembershipState)
            val data = data as MembershipState
            assertEquals(lender.identity(), data.identity.cordaIdentity)
            assertNotNull(data.roles.find { it is LoanIssuerRole })
        }
        assertTrue(command.value is MembershipContract.Commands.ModifyRoles)
    }
}
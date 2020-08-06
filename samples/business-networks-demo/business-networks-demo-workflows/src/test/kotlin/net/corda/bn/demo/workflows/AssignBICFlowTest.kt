package net.corda.bn.demo.workflows

import net.corda.bn.contracts.MembershipContract
import net.corda.bn.demo.contracts.BankIdentity
import net.corda.bn.flows.IllegalFlowArgumentException
import net.corda.bn.states.MembershipState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AssignBICFlowTest : LoanFlowTest(numberOfLenders = 1, numberOfBorrowers = 0) {

    @Test(timeout = 300_000)
    fun `assign bic flow should fail if invalid bic is provided`() {
        val lender = lenders.first()

        val membershipId = (runCreateBusinessNetworkFlow(lender).tx.outputStates.single() as MembershipState).linearId
        val illegalBic = "ILLEGAL-BIC"
        assertFailsWith<IllegalFlowArgumentException> { runAssignBICFlow(lender, membershipId, illegalBic) }
    }

    @Test(timeout = 300_000)
    fun `assign bic flow happy path`() {
        val lender = lenders.first()

        val membershipId = (runCreateBusinessNetworkFlow(lender).tx.outputStates.single() as MembershipState).linearId
        val bic = "BANKGB00"
        val (membership, command) = runAssignBICFlow(lender, membershipId, bic).run {
            assertEquals(1, tx.inputs.size)
            verifyRequiredSignatures()
            tx.outputs.single() to tx.commands.single()
        }

        membership.apply {
            assertEquals(MembershipContract.CONTRACT_NAME, membership.contract)
            assertTrue(data is MembershipState)
            val data = data as MembershipState
            assertEquals(lender.identity(), data.identity.cordaIdentity)
            assertEquals(BankIdentity(bic), data.identity.businessIdentity)
        }
        assertTrue(command.value is MembershipContract.Commands.ModifyBusinessIdentity)
    }
}
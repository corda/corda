package net.corda.bn.demo.workflows

import net.corda.bn.demo.contracts.LoanContract
import net.corda.bn.demo.contracts.LoanState
import net.corda.bn.flows.DatabaseService
import net.corda.bn.flows.IllegalFlowArgumentException
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.testing.node.StartedMockNode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SettleLoanFlowTest : LoanFlowTest(numberOfLenders = 1, numberOfBorrowers = 1) {

    private fun issueLoan(lender: StartedMockNode, borrower: StartedMockNode, amount: Int): LoanState {
        val (networkId, lenderMembershipId) = (runCreateBusinessNetworkFlow(lender).tx.outputStates.single() as MembershipState).run { networkId to linearId }
        val borrowerMembershipId = runRequestMembershipFlow(borrower, lender.identity(), networkId).run {
            val membership = tx.outputStates.single() as MembershipState
            runActivateMembershipFlow(lender, membership.linearId)
            membership.linearId
        }
        val bic = "BANKGB00"
        listOf(lenderMembershipId, borrowerMembershipId).forEach { runAssignBICFlow(lender, it, bic) }
        runAssignLoanIssuerRole(lender, networkId)

        val groupId = lender.services.cordaService(DatabaseService::class.java).run {
            getAllBusinessNetworkGroups(networkId).single().state.data.linearId
        }
        runModifyGroupFlow(lender, groupId, setOf(lenderMembershipId, borrowerMembershipId))

        return runIssueLoanFlow(lender, networkId, borrower.identity(), amount).tx.outputStates.single() as LoanState
    }

    @Test(timeout = 300_000)
    fun `settle loan flow should fail if loan with given linear ID is not found`() {
        val borrower = borrowers.first()

        assertFailsWith<LoanNotFoundException> { runSettleLoanFlow(borrower, UniqueIdentifier(), 5) }
    }

    @Test(timeout = 300_000)
    fun `settle loan flow should fail if someone else than borrower initiates it`() {
        val lender = lenders.first()
        val borrower = borrowers.first()

        val loan = issueLoan(lender, borrower, 10)
        assertFailsWith<IllegalFlowInitiatorException> { runSettleLoanFlow(lender, loan.linearId, 5) }
    }

    @Test(timeout = 300_000)
    fun `settle loan flow should fail if settlement amount is illegal`() {
        val lender = lenders.first()
        val borrower = borrowers.first()

        val loan = issueLoan(lender, borrower, 10)
        assertFailsWith<IllegalFlowArgumentException> { runSettleLoanFlow(borrower, loan.linearId, -5) }
        assertFailsWith<IllegalFlowArgumentException> { runSettleLoanFlow(borrower, loan.linearId, 15) }
    }

    @Test(timeout = 300_000)
    fun `settle loan flow happy path`() {
        val lender = lenders.first()
        val borrower = borrowers.first()

        // first partially settle loan
        val loan = issueLoan(lender, borrower, 10)
        val (settledLoan, command) = runSettleLoanFlow(borrower, loan.linearId, 5).run {
            assertEquals(1, tx.inputs.size)
            verifyRequiredSignatures()
            tx.outputs.single() to tx.commands.single()
        }

        val settledLoanState = settledLoan.run {
            assertEquals(LoanContract.CONTRACT_NAME, contract)
            assertTrue(data is LoanState)
            val data = data as LoanState
            assertEquals(lender.identity(), data.lender)
            assertEquals(borrower.identity(), data.borrower)
            assertEquals(5, data.amount)

            data
        }
        assertTrue(command.value is LoanContract.Commands.Settle)

        // also check ledgers
        listOf(lender, borrower).forEach { node ->
            getAllLoansFromVault(node).apply {
                assertEquals(1, size, "Vault size assertion failed for ${node.identity()}")
                assertEquals(settledLoanState, single(), "Expected persisted LoanState mismatch for ${node.identity()}")
            }
        }

        // now fully settle remaining amount
        runSettleLoanFlow(borrower, loan.linearId, 5).run {
            assertEquals(1, tx.inputs.size)
            verifyRequiredSignatures()
            assertTrue(tx.outputs.isEmpty())
            assertTrue(tx.commands.single().value is LoanContract.Commands.Exit)
        }
    }
}
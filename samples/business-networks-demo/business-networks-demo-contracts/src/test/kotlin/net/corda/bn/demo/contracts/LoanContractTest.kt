package net.corda.bn.demo.contracts

import net.corda.bn.states.BNIdentity
import net.corda.bn.states.MembershipIdentity
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import net.corda.testing.node.makeTestIdentityService
import org.junit.Test

class DummyContract : Contract {

    companion object {
        const val CONTRACT_NAME = "net.corda.bn.demo.contracts.DummyContract"
    }

    override fun verify(tx: LedgerTransaction) {}
}

class DummyCommand : TypeOnlyCommandData()

@CordaSerializable
class DummyIdentity : BNIdentity

class LoanContractTest {

    private val ledgerServices = MockServices(
            cordappPackages = listOf("net.corda.bn.demo.contracts"),
            initialIdentityName = CordaX500Name.parse("O=Lender,L=London,C=GB"),
            identityService = makeTestIdentityService(),
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
    )

    private val lenderIdentity = TestIdentity(CordaX500Name.parse("O=Lender,L=London,C=GB")).party
    private val borrowerIdentity = TestIdentity(CordaX500Name.parse("O=Borrower,L=London,C=GB")).party

    private val lenderMembership = MembershipState(
            identity = MembershipIdentity(lenderIdentity, BankIdentity("BANKGB00")),
            networkId = "network-id",
            status = MembershipStatus.ACTIVE,
            participants = listOf(lenderIdentity, borrowerIdentity)
    )
    private val borrowerMembership = MembershipState(
            identity = MembershipIdentity(borrowerIdentity, BankIdentity("BANKGB01")),
            networkId = "network-id",
            status = MembershipStatus.ACTIVE,
            participants = listOf(lenderIdentity, borrowerIdentity)
    )

    private val loanState = LoanState(
            lender = lenderIdentity,
            borrower = borrowerIdentity,
            amount = 10,
            networkId = "network-id",
            participants = listOf(lenderIdentity, borrowerIdentity)
    )

    @Suppress("ComplexMethod")
    private fun testMembershipVerification(isExit: Boolean) {
        ledgerServices.ledger {
            val input = loanState
            val cmd = if (isExit) LoanContract.Commands.Exit::class.java else LoanContract.Commands.Settle::class.java
            val commandName = if (isExit) "exit" else "settlement"
            transaction {
                input(LoanContract.CONTRACT_NAME, input)
                if (!isExit) output(LoanContract.CONTRACT_NAME, input.run { copy(amount = amount - 1) })
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), cmd.getConstructor().newInstance())
                this `fails with` "Loan $commandName transaction should have 2 reference states"
            }
            transaction {
                input(LoanContract.CONTRACT_NAME, input)
                if (!isExit) output(LoanContract.CONTRACT_NAME, input.run { copy(amount = amount - 1) })
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), cmd.getConstructor().newInstance())
                reference(LoanContract.CONTRACT_NAME, input)
                reference(LoanContract.CONTRACT_NAME, input.copy(amount = 15))
                this `fails with` "Loan $commandName transaction should contain only reference MembershipStates"
            }
            transaction {
                input(LoanContract.CONTRACT_NAME, input)
                if (!isExit) output(LoanContract.CONTRACT_NAME, input.run { copy(amount = amount - 1) })
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), cmd.getConstructor().newInstance())
                reference(LoanContract.CONTRACT_NAME, lenderMembership)
                reference(LoanContract.CONTRACT_NAME, borrowerMembership.copy(networkId = "other-network-id"))
                this `fails with` "Loan $commandName transaction should contain only reference membership states from Business Network with ${input.networkId} ID"
            }
            transaction {
                input(LoanContract.CONTRACT_NAME, input)
                if (!isExit) output(LoanContract.CONTRACT_NAME, input.run { copy(amount = amount - 1) })
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), cmd.getConstructor().newInstance())
                reference(LoanContract.CONTRACT_NAME, borrowerMembership)
                reference(LoanContract.CONTRACT_NAME, borrowerMembership)
                this `fails with` "Loan $commandName transaction should have lender's reference membership state"
            }
            transaction {
                input(LoanContract.CONTRACT_NAME, input)
                if (!isExit) output(LoanContract.CONTRACT_NAME, input.run { copy(amount = amount - 1) })
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), cmd.getConstructor().newInstance())
                reference(LoanContract.CONTRACT_NAME, lenderMembership)
                reference(LoanContract.CONTRACT_NAME, lenderMembership)
                this `fails with` "Loan $commandName transaction should have borrowers's reference membership state"
            }
            transaction {
                input(LoanContract.CONTRACT_NAME, input)
                if (!isExit) output(LoanContract.CONTRACT_NAME, input.run { copy(amount = amount - 1) })
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), cmd.getConstructor().newInstance())
                reference(LoanContract.CONTRACT_NAME, borrowerMembership)
                reference(LoanContract.CONTRACT_NAME, lenderMembership.copy(status = MembershipStatus.SUSPENDED))
                this `fails with` "Lender should be active member of Business Network with ${input.networkId}"
            }
            transaction {
                input(LoanContract.CONTRACT_NAME, input)
                if (!isExit) output(LoanContract.CONTRACT_NAME, input.run { copy(amount = amount - 1) })
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), cmd.getConstructor().newInstance())
                reference(LoanContract.CONTRACT_NAME, borrowerMembership)
                reference(LoanContract.CONTRACT_NAME, lenderMembership.run { copy(identity = MembershipIdentity(identity.cordaIdentity)) })
                this `fails with` "Lender should have business identity of BankIdentity type"
            }
            transaction {
                input(LoanContract.CONTRACT_NAME, input)
                if (!isExit) output(LoanContract.CONTRACT_NAME, input.run { copy(amount = amount - 1) })
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), cmd.getConstructor().newInstance())
                reference(LoanContract.CONTRACT_NAME, borrowerMembership.copy(status = MembershipStatus.SUSPENDED))
                reference(LoanContract.CONTRACT_NAME, lenderMembership)
                this `fails with` "Borrower should be active member of Business Network with ${input.networkId}"
            }
            transaction {
                input(LoanContract.CONTRACT_NAME, input)
                if (!isExit) output(LoanContract.CONTRACT_NAME, input.run { copy(amount = amount - 1) })
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), cmd.getConstructor().newInstance())
                reference(LoanContract.CONTRACT_NAME, borrowerMembership)
                reference(LoanContract.CONTRACT_NAME, lenderMembership.run { copy(identity = MembershipIdentity(identity.cordaIdentity, DummyIdentity())) })
                this `fails with` "Lender should have business identity of BankIdentity type"
            }
            transaction {
                input(LoanContract.CONTRACT_NAME, input)
                if (!isExit) output(LoanContract.CONTRACT_NAME, input.run { copy(amount = amount - 1) })
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), cmd.getConstructor().newInstance())
                reference(LoanContract.CONTRACT_NAME, borrowerMembership)
                reference(LoanContract.CONTRACT_NAME, lenderMembership)
                verifies()
            }
        }
    }

    @Test(timeout = 300_000)
    fun `test common contract verification`() {
        ledgerServices.ledger {
            transaction {
                val input = loanState
                output(LoanContract.CONTRACT_NAME, input)
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), DummyCommand())
                fails()
            }
            transaction {
                val input = loanState
                input(DummyContract.CONTRACT_NAME, input)
                output(LoanContract.CONTRACT_NAME, input)
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), LoanContract.Commands.Settle())
                this `fails with` "Input state has to be validated by ${LoanContract.CONTRACT_NAME}"
            }
            transaction {
                val input = loanState
                input(LoanContract.CONTRACT_NAME, input)
                output(DummyContract.CONTRACT_NAME, input)
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), LoanContract.Commands.Settle())
                this `fails with` "Output state has to be validated by ${LoanContract.CONTRACT_NAME}"
            }
            transaction {
                val input = loanState.copy(amount = 0)
                input(LoanContract.CONTRACT_NAME, input)
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), LoanContract.Commands.Exit())
                this `fails with` "Input state should have positive amount"
            }
            transaction {
                val output = loanState.copy(amount = -1)
                output(LoanContract.CONTRACT_NAME, output)
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), LoanContract.Commands.Issue())
                this `fails with` "Output state should have positive amount"
            }
            transaction {
                val input = loanState.copy(participants = listOf(lenderIdentity))
                input(LoanContract.CONTRACT_NAME, input)
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), LoanContract.Commands.Exit())
                this `fails with` "Input state's participants list should contain lender and borrower only"
            }
            transaction {
                val output = loanState.copy(participants = emptyList())
                output(LoanContract.CONTRACT_NAME, output)
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), LoanContract.Commands.Issue())
                this `fails with` "Output state's participants list should contain lender and borrower only"
            }

            val input = loanState
            transaction {
                val output = loanState.copy(lender = borrowerIdentity, participants = listOf(borrowerIdentity))
                input(LoanContract.CONTRACT_NAME, input)
                output(LoanContract.CONTRACT_NAME, output)
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), LoanContract.Commands.Settle())
                this `fails with` "Input and output state should have same lender"
            }
            transaction {
                val output = loanState.copy(borrower = lenderIdentity, participants = listOf(lenderIdentity))
                input(LoanContract.CONTRACT_NAME, input)
                output(LoanContract.CONTRACT_NAME, output)
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), LoanContract.Commands.Settle())
                this `fails with` "Input and output state should have same borrower"
            }
            transaction {
                val output = loanState.copy(networkId = "new-network-id")
                input(LoanContract.CONTRACT_NAME, input)
                output(LoanContract.CONTRACT_NAME, output)
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), LoanContract.Commands.Settle())
                this `fails with` "Input and output state should have same network ID"
            }
            transaction {
                val output = loanState.copy(linearId = UniqueIdentifier())
                input(LoanContract.CONTRACT_NAME, input)
                output(LoanContract.CONTRACT_NAME, output)
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), LoanContract.Commands.Settle())
                this `fails with` "Input and output state should have same linear ID"
            }
            transaction {
                val output = loanState.run { copy(amount = amount + 1) }
                input(LoanContract.CONTRACT_NAME, input)
                output(LoanContract.CONTRACT_NAME, output)
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), LoanContract.Commands.Settle())
                this `fails with` "Output state should have lower amount than input state"
            }
            transaction {
                val output = loanState.run { copy(amount = amount - 1) }
                input(LoanContract.CONTRACT_NAME, input)
                output(LoanContract.CONTRACT_NAME, output)
                command(listOf(lenderIdentity.owningKey), LoanContract.Commands.Settle())
                this `fails with` "Transaction should be signed by all loan states' participants only"
            }
        }
    }

    @Test(timeout = 300_000)
    fun `test issue loan command contract verification`() {
        ledgerServices.ledger {
            val output = loanState
            transaction {
                input(LoanContract.CONTRACT_NAME, output.run { copy(amount = amount + 1) })
                output(LoanContract.CONTRACT_NAME, output)
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), LoanContract.Commands.Issue())
                this `fails with` "Loan issuance transaction shouldn't contain any inputs"
            }
            transaction {
                output(LoanContract.CONTRACT_NAME, output)
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), LoanContract.Commands.Issue())
                verifies()
            }
        }
    }

    @Test(timeout = 300_000)
    fun `test settle loan command contract verification`() {
        testMembershipVerification(false)
    }

    @Test(timeout = 300_000)
    fun `test exit load command contract verification`() {
        ledgerServices.ledger {
            val input = loanState
            transaction {
                input(LoanContract.CONTRACT_NAME, input)
                output(LoanContract.CONTRACT_NAME, input.run { copy(amount = amount - 1) })
                command(listOf(lenderIdentity.owningKey, borrowerIdentity.owningKey), LoanContract.Commands.Exit())
                this `fails with` "Loan exit transaction shouldn't contain any outputs"
            }
        }
        testMembershipVerification(true)
    }
}
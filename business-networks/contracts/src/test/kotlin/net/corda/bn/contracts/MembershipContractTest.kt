package net.corda.bn.contracts

import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.LedgerTransaction
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class DummyContract : Contract {

    companion object {
        const val CONTRACT_NAME = "net.corda.bn.contracts.DummyContract"
    }

    override fun verify(tx: LedgerTransaction) {}
}

private class DummyCommand : TypeOnlyCommandData()

class MembershipContractTest {

    private val ledgerServices = MockServices(listOf("net.corda.bn.contracts"))

    private val memberIdentity = TestIdentity(CordaX500Name.parse("O=Member,L=London,C=GB")).party
    private val bnoIdentity = TestIdentity(CordaX500Name.parse("O=BNO,L=London,C=GB")).party

    private fun membershipState() = MembershipState(
            identity = memberIdentity,
            networkId = "network-id",
            status = MembershipStatus.PENDING,
            participants = listOf(memberIdentity, bnoIdentity)
    )

    @Test(timeout = 300_000)
    fun `test common contract verification`() {
        ledgerServices.ledger {
            transaction {
                val input = membershipState()
                output(MembershipContract.CONTRACT_NAME, input)
                command(listOf(bnoIdentity.owningKey, memberIdentity.owningKey), DummyCommand())
                fails()
            }
            transaction {
                val input = membershipState()
                input(DummyContract.CONTRACT_NAME, input)
                output(MembershipContract.CONTRACT_NAME, input)
                command(memberIdentity.owningKey, MembershipContract.Commands.Revoke())
                this `fails with` "Input state has to be validated by ${MembershipContract.CONTRACT_NAME}"
            }
            transaction {
                val output = membershipState()
                input(MembershipContract.CONTRACT_NAME, output)
                output(DummyContract.CONTRACT_NAME, output)
                command(memberIdentity.owningKey, MembershipContract.Commands.Request())
                this `fails with` "Output state has to be validated by ${MembershipContract.CONTRACT_NAME}"
            }
            transaction {
                val input = membershipState().run { copy(modified = modified.minusSeconds(100)) }
                input(MembershipContract.CONTRACT_NAME, input)
                command(memberIdentity.owningKey, MembershipContract.Commands.Revoke())
                this `fails with` "Input state's modified timestamp should be greater or equal to issued timestamp"
            }
            transaction {
                val output = membershipState().run { copy(modified = modified.minusSeconds(100)) }
                output(MembershipContract.CONTRACT_NAME, output)
                command(memberIdentity.owningKey, MembershipContract.Commands.Request())
                this `fails with` "Output state's modified timestamp should be greater or equal to issued timestamp"
            }

            val input = membershipState()
            transaction {
                val output = input.copy(identity = bnoIdentity)
                input(MembershipContract.CONTRACT_NAME, input)
                output(MembershipContract.CONTRACT_NAME, output)
                command(listOf(bnoIdentity.owningKey), MembershipContract.Commands.Activate())
                this `fails with` "Input and output state should have same Corda identity"
            }
            transaction {
                val output = input.copy(networkId = "other-network-id")
                input(MembershipContract.CONTRACT_NAME, input)
                output(MembershipContract.CONTRACT_NAME, output)
                command(listOf(bnoIdentity.owningKey), MembershipContract.Commands.Activate())
                this `fails with` "Input and output state should have same network IDs"
            }
            transaction {
                val output = input.run { copy(issued = issued.minusSeconds(100)) }
                input(MembershipContract.CONTRACT_NAME, input)
                output(MembershipContract.CONTRACT_NAME, output)
                command(listOf(bnoIdentity.owningKey), MembershipContract.Commands.Activate())
                this `fails with` "Input and output state should have same issued timestamps"
            }
            transaction {
                val output = input.run { copy(modified = modified.plusSeconds(100)) }
                input(MembershipContract.CONTRACT_NAME, input.run { copy(modified = modified.plusSeconds(200)) })
                output(MembershipContract.CONTRACT_NAME, output)
                command(listOf(bnoIdentity.owningKey), MembershipContract.Commands.Activate())
                this `fails with` "Output state's modified timestamp should be greater or equal than input's"
            }
            transaction {
                val output = input.copy(linearId = UniqueIdentifier())
                input(MembershipContract.CONTRACT_NAME, input)
                output(MembershipContract.CONTRACT_NAME, output)
                command(listOf(bnoIdentity.owningKey), MembershipContract.Commands.Activate())
                this `fails with` "Input and output state should have same linear IDs"
            }
            transaction {
                val output = input.copy(participants = emptyList())
                input(MembershipContract.CONTRACT_NAME, input)
                output(MembershipContract.CONTRACT_NAME, output)
                command(listOf(bnoIdentity.owningKey), MembershipContract.Commands.Activate())
                this `fails with` "Input and output state should have same participants"
            }
        }
    }

    @Test(timeout = 300_000)
    fun `test request membership command contract verification`() {
        ledgerServices.ledger {
            val output = membershipState()
            transaction {
                input(MembershipContract.CONTRACT_NAME, output)
                output(MembershipContract.CONTRACT_NAME, output)
                command(listOf(bnoIdentity.owningKey, memberIdentity.owningKey), MembershipContract.Commands.Request())
                this `fails with` "Membership request transaction shouldn't contain any inputs"
            }
            transaction {
                output(MembershipContract.CONTRACT_NAME, output.copy(status = MembershipStatus.ACTIVE))
                command(listOf(bnoIdentity.owningKey, memberIdentity.owningKey), MembershipContract.Commands.Request())
                this `fails with` "Membership request transaction should contain output state in PENDING status"
            }
            transaction {
                output(MembershipContract.CONTRACT_NAME, output)
                command(listOf(bnoIdentity.owningKey, memberIdentity.owningKey), MembershipContract.Commands.Request())
                verifies()
            }
        }
    }

    @Test(timeout = 300_000)
    fun `test activate membership command contract verification`() {
        ledgerServices.ledger {
            val input = membershipState()
            transaction {
                input(MembershipContract.CONTRACT_NAME, input.copy(status = MembershipStatus.ACTIVE))
                output(MembershipContract.CONTRACT_NAME, input.copy(status = MembershipStatus.ACTIVE))
                command(bnoIdentity.owningKey, MembershipContract.Commands.Activate())
                this `fails with` "Input state of membership activation transaction shouldn't be already active"
            }
            transaction {
                input(MembershipContract.CONTRACT_NAME, input)
                output(MembershipContract.CONTRACT_NAME, input.copy(status = MembershipStatus.SUSPENDED))
                command(bnoIdentity.owningKey, MembershipContract.Commands.Activate())
                this `fails with` "Output state of membership activation transaction should be active"
            }
            transaction {
                input(MembershipContract.CONTRACT_NAME, input)
                output(MembershipContract.CONTRACT_NAME, input.copy(status = MembershipStatus.ACTIVE))
                command(bnoIdentity.owningKey, MembershipContract.Commands.Activate())
                verifies()
            }
        }
    }

    @Test(timeout = 300_000)
    fun `test suspend membership command contract verification`() {
        ledgerServices.ledger {
            val input = membershipState()
            transaction {
                input(MembershipContract.CONTRACT_NAME, input.copy(status = MembershipStatus.SUSPENDED))
                output(MembershipContract.CONTRACT_NAME, input.copy(status = MembershipStatus.SUSPENDED))
                command(bnoIdentity.owningKey, MembershipContract.Commands.Suspend())
                this `fails with` "Input state of membership suspension transaction shouldn't be already suspended"
            }
            transaction {
                input(MembershipContract.CONTRACT_NAME, input)
                output(MembershipContract.CONTRACT_NAME, input.copy(status = MembershipStatus.ACTIVE))
                command(bnoIdentity.owningKey, MembershipContract.Commands.Suspend())
                this `fails with` "Output state of membership suspension transaction should be suspended"
            }
            transaction {
                input(MembershipContract.CONTRACT_NAME, input)
                output(MembershipContract.CONTRACT_NAME, input.copy(status = MembershipStatus.SUSPENDED))
                command(bnoIdentity.owningKey, MembershipContract.Commands.Suspend())
                verifies()
            }
        }
    }

    @Test(timeout = 300_000)
    fun `test revoke membership command contract verification`() {
        ledgerServices.ledger {
            val input = membershipState()
            transaction {
                input(MembershipContract.CONTRACT_NAME, input)
                output(MembershipContract.CONTRACT_NAME, input)
                command(bnoIdentity.owningKey, MembershipContract.Commands.Revoke())
                this `fails with` "Membership revocation transaction shouldn't contain any outputs"
            }
            transaction {
                input(MembershipContract.CONTRACT_NAME, input)
                command(bnoIdentity.owningKey, MembershipContract.Commands.Revoke())
                verifies()
            }
        }
    }
}
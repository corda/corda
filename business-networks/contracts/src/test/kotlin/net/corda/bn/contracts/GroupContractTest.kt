package net.corda.bn.contracts

import net.corda.bn.states.GroupState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class GroupContractTest {

    private val ledgerServices = MockServices(listOf("net.corda.bn.contracts"))

    private val memberIdentity = TestIdentity(CordaX500Name.parse("O=Member,L=London,C=GB")).party
    private val bnoIdentity = TestIdentity(CordaX500Name.parse("O=BNO,L=London,C=GB")).party

    private val groupState = GroupState(networkId = "network-id", participants = listOf(memberIdentity, bnoIdentity))

    @Test(timeout = 300_000)
    fun `test common contract verification`() {
        ledgerServices.ledger {
            transaction {
                val input = groupState
                output(GroupContract.CONTRACT_NAME, input)
                command(bnoIdentity.owningKey, DummyCommand())
                fails()
            }
            transaction {
                val input = groupState
                input(DummyContract.CONTRACT_NAME, input)
                output(GroupContract.CONTRACT_NAME, input)
                command(bnoIdentity.owningKey, GroupContract.Commands.Modify(listOf(bnoIdentity.owningKey)))
                this `fails with` "Input state has to be validated by ${GroupContract.CONTRACT_NAME}"
            }
            transaction {
                val input = groupState
                input(GroupContract.CONTRACT_NAME, input)
                output(DummyContract.CONTRACT_NAME, input)
                command(bnoIdentity.owningKey, GroupContract.Commands.Modify(listOf(bnoIdentity.owningKey)))
                this `fails with` "Output state has to be validated by ${GroupContract.CONTRACT_NAME}"
            }
            transaction {
                val input = groupState.run { copy(modified = issued.minusSeconds(100)) }
                input(GroupContract.CONTRACT_NAME, input)
                command(bnoIdentity.owningKey, GroupContract.Commands.Exit(listOf(bnoIdentity.owningKey)))
                this `fails with` "Input state's modified timestamp should be greater or equal to issued timestamp"
            }
            transaction {
                val output = groupState.run { copy(modified = issued.minusSeconds(100)) }
                output(GroupContract.CONTRACT_NAME, output)
                command(bnoIdentity.owningKey, GroupContract.Commands.Create(listOf(bnoIdentity.owningKey)))
                this `fails with` "Output state's modified timestamp should be greater or equal to issued timestamp"
            }

            val input = groupState
            transaction {
                val output = input.copy(networkId = "other-network-id")
                input(GroupContract.CONTRACT_NAME, input)
                output(GroupContract.CONTRACT_NAME, output)
                command(bnoIdentity.owningKey, GroupContract.Commands.Modify(listOf(bnoIdentity.owningKey)))
                this `fails with` "Input and output state should have same network IDs"
            }
            transaction {
                val output = input.run { copy(issued = issued.minusSeconds(100)) }
                input(GroupContract.CONTRACT_NAME, input)
                output(GroupContract.CONTRACT_NAME, output)
                command(bnoIdentity.owningKey, GroupContract.Commands.Modify(listOf(bnoIdentity.owningKey)))
                this `fails with` "Input and output state should have same issued timestamps"
            }
            transaction {
                val output = input.run { copy(modified = modified.plusSeconds(100)) }
                input(GroupContract.CONTRACT_NAME, input.run { copy(modified = modified.plusSeconds(200)) })
                output(GroupContract.CONTRACT_NAME, output)
                command(bnoIdentity.owningKey, GroupContract.Commands.Modify(listOf(bnoIdentity.owningKey)))
                this `fails with` "Output state's modified timestamp should be greater or equal than input's"
            }
            transaction {
                val output = input.copy(linearId = UniqueIdentifier())
                input(GroupContract.CONTRACT_NAME, input)
                output(GroupContract.CONTRACT_NAME, output)
                command(bnoIdentity.owningKey, GroupContract.Commands.Modify(listOf(bnoIdentity.owningKey)))
                this `fails with` "Input and output state should have same linear IDs"
            }
            transaction {
                val output = input.copy(name = "new-name")
                input(GroupContract.CONTRACT_NAME, input)
                output(GroupContract.CONTRACT_NAME, output)
                command(bnoIdentity.owningKey, GroupContract.Commands.Modify(emptyList()))
                this `fails with` "Transaction must be signed by all signers specified inside command"
            }
        }
    }

    @Test(timeout = 300_000)
    fun `test create group command contract verification`() {
        ledgerServices.ledger {
            val output = groupState
            transaction {
                input(GroupContract.CONTRACT_NAME, output)
                output(GroupContract.CONTRACT_NAME, output)
                command(bnoIdentity.owningKey, GroupContract.Commands.Create(listOf(bnoIdentity.owningKey)))
                this `fails with` "Membership request transaction shouldn't contain any inputs"
            }
            transaction {
                output(GroupContract.CONTRACT_NAME, output)
                command(bnoIdentity.owningKey, GroupContract.Commands.Create(listOf(bnoIdentity.owningKey)))
                verifies()
            }
        }
    }

    @Test(timeout = 300_000)
    fun `test modify group command contract verification`() {
        ledgerServices.ledger {
            val input = groupState
            transaction {
                input(GroupContract.CONTRACT_NAME, input)
                output(GroupContract.CONTRACT_NAME, input)
                command(bnoIdentity.owningKey, GroupContract.Commands.Modify(listOf(bnoIdentity.owningKey)))
                this `fails with` "Input and output states of group modification transaction should have different name or participants field"
            }
            transaction {
                input(GroupContract.CONTRACT_NAME, input)
                output(GroupContract.CONTRACT_NAME, input.copy(name = "new-name"))
                command(bnoIdentity.owningKey, GroupContract.Commands.Modify(listOf(bnoIdentity.owningKey)))
                verifies()
            }
            transaction {
                input(GroupContract.CONTRACT_NAME, input)
                output(GroupContract.CONTRACT_NAME, input.copy(participants = listOf(bnoIdentity)))
                command(bnoIdentity.owningKey, GroupContract.Commands.Modify(listOf(bnoIdentity.owningKey)))
                verifies()
            }
            transaction {
                input(GroupContract.CONTRACT_NAME, input)
                output(GroupContract.CONTRACT_NAME, input.copy(name = "new-name", participants = listOf(bnoIdentity)))
                command(bnoIdentity.owningKey, GroupContract.Commands.Modify(listOf(bnoIdentity.owningKey)))
                verifies()
            }
        }
    }

    @Test(timeout = 300_000)
    fun `test delete group command contract verification`() {
        ledgerServices.ledger {
            val input = groupState
            transaction {
                input(GroupContract.CONTRACT_NAME, input)
                output(GroupContract.CONTRACT_NAME, input)
                command(bnoIdentity.owningKey, GroupContract.Commands.Exit(listOf(bnoIdentity.owningKey)))
                this `fails with` "Membership revocation transaction shouldn't contain any outputs"
            }
            transaction {
                input(GroupContract.CONTRACT_NAME, input)
                command(bnoIdentity.owningKey, GroupContract.Commands.Exit(listOf(bnoIdentity.owningKey)))
                verifies()
            }
        }
    }
}
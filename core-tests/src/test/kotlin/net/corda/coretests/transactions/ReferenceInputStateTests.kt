package net.corda.coretests.transactions

import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.using
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.IdentityService
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.DOLLARS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.Rule
import org.junit.Test

const val CONTRACT_ID = "net.corda.coretests.transactions.ReferenceStateTests\$ExampleContract"

class ReferenceStateTests {
    private companion object {
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        val ISSUER = TestIdentity(CordaX500Name("ISSUER", "London", "GB"))
        val ALICE = TestIdentity(CordaX500Name("ALICE", "London", "GB"))
        val ALICE_PARTY get() = ALICE.party
        val ALICE_PUBKEY get() = ALICE.publicKey
        val BOB = TestIdentity(CordaX500Name("BOB", "London", "GB"))
        val BOB_PARTY get() = BOB.party
        val BOB_PUBKEY get() = BOB.publicKey
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    val defaultIssuer = ISSUER.ref(1)
    val bobCash = Cash.State(amount = 1000.DOLLARS `issued by` defaultIssuer, owner = BOB_PARTY)
    private val ledgerServices = MockServices(
            cordappPackages = listOf("net.corda.coretests.transactions", "net.corda.finance.contracts.asset"),
            initialIdentity = ALICE,
            identityService = mock<IdentityService>().also {
                doReturn(ALICE_PARTY).whenever(it).partyFromKey(ALICE_PUBKEY)
                doReturn(BOB_PARTY).whenever(it).partyFromKey(BOB_PUBKEY)
            },
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4, notaries = listOf(NotaryInfo(DUMMY_NOTARY, true)))
    )

    // This state has only been created to serve reference data so it cannot ever be used as an input or
    // output when it is being referred to. However, we might want all states to be referable, so this
    // check might not be present in other contracts, like Cash, for example. Cash might have a command
    // called "Share" that allows a party to prove to another that they own over a certain amount of cash.
    // As such, cash can be added to the references list with a "Share" command.
    @BelongsToContract(ExampleContract::class)
    data class ExampleState(val creator: Party, val data: String) : ContractState {
        override val participants: List<AbstractParty> get() = listOf(creator)
    }

    // This state has only been created to serve reference data so it cannot ever be used as an input or
    // output when it is being referred to. However, we might want all states to be referable, so this
    // check might not be present in other contracts, like Cash, for example. Cash might have a command
    // called "Share" that allows a party to prove to another that they own over a certain amount of cash.
    // As such, cash can be added to the references list with a "Share" command.
    @BelongsToContract(Cash::class)
    data class ExampleCashState(val creator: Party, val data: String) : ContractState {
        override val participants: List<AbstractParty> get() = listOf(creator)
    }

    class ExampleContract : Contract {
        interface Commands : CommandData
        class Create : Commands
        class Update : Commands

        override fun verify(tx: LedgerTransaction) {
            val command = tx.commands.requireSingleCommand<Commands>()
            when (command.value) {
                is Create -> requireThat {
                    "Must have no inputs" using (tx.inputs.isEmpty())
                    "Must have only one output" using (tx.outputs.size == 1)
                    val output = tx.outputsOfType<ExampleState>().single()
                    val signedByCreator = command.signers.single() == output.participants.single().owningKey
                    "Must be signed by creator" using signedByCreator
                }
                is Update -> {
                    "Must have no inputs" using (tx.inputs.size == 1)
                    "Must have only one output" using (tx.outputs.size == 1)
                    val input = tx.inputsOfType<ExampleState>().single()
                    val output = tx.outputsOfType<ExampleState>().single()
                    "Must update the data" using (input.data != output.data)
                    val signedByCreator = command.signers.single() == output.participants.single().owningKey
                    "Must be signed by creator" using signedByCreator
                }
            }
        }
    }

    @Test(timeout=300_000)
	fun `create a reference state then refer to it multiple times`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            // Create a reference state. The reference state is created in the normal way. A transaction with one
            // or more outputs. It makes sense to create them one at a time, so the creator can have fine grained
            // control over who sees what.
            transaction {
                output(CONTRACT_ID, "REF DATA", ExampleState(ALICE_PARTY, "HELLO CORDA"))
                command(ALICE_PUBKEY, ExampleContract.Create())
                verifies()
            }
            // Somewhere down the line, Bob obtains the ExampleState and now refers to it as a reference state. As such,
            // it is added to the references list.
            transaction {
                reference("REF DATA")
                input(Cash.PROGRAM_ID, bobCash)
                output(Cash.PROGRAM_ID, "ALICE CASH", bobCash.withNewOwner(ALICE_PARTY).ownableState)
                command(BOB_PUBKEY, Cash.Commands.Move())
                verifies()
            }
            // Alice can use it too.
            transaction {
                reference("REF DATA")
                input("ALICE CASH")
                output(Cash.PROGRAM_ID, "BOB CASH 2", bobCash.withNewOwner(BOB_PARTY).ownableState)
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
            // Bob can use it again.
            transaction {
                reference("REF DATA")
                input("BOB CASH 2")
                output(Cash.PROGRAM_ID, bobCash.withNewOwner(ALICE_PARTY).ownableState)
                command(BOB_PUBKEY, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test(timeout=300_000)
	fun `Non-creator node cannot spend spend a reference state`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                output(CONTRACT_ID, "REF DATA", ExampleState(ALICE_PARTY, "HELLO CORDA"))
                command(ALICE_PUBKEY, ExampleContract.Create())
                verifies()
            }
            // Try to spend an unspendable input by accident. Opps! This should fail as per the contract above.
            transaction {
                input("REF DATA")
                input(Cash.PROGRAM_ID, bobCash)
                output(Cash.PROGRAM_ID, bobCash.withNewOwner(ALICE_PARTY).ownableState)
                command(BOB_PUBKEY, Cash.Commands.Move())
                fails()
            }
        }
    }

    @Test(timeout=300_000)
	fun `Can't use old reference states`() {
        val refData = ExampleState(ALICE_PARTY, "HELLO CORDA")
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                output(CONTRACT_ID, "REF DATA", refData)
                command(ALICE_PUBKEY, ExampleContract.Create())
                verifies()
            }
            // Refer to it. All OK.
            transaction {
                reference("REF DATA")
                input(Cash.PROGRAM_ID, bobCash)
                output(Cash.PROGRAM_ID, "ALICE CASH", bobCash.withNewOwner(ALICE_PARTY).ownableState)
                command(BOB_PUBKEY, Cash.Commands.Move())
                verifies()
            }
            // Update it.
            transaction {
                input("REF DATA")
                command(ALICE_PUBKEY, ExampleContract.Update())
                output(ExampleContract::class.java.typeName, "UPDATED REF DATA", "REF DATA".output<ExampleState>().copy(data = "NEW STUFF!"))
                verifies()
            }
            // Try to use the old one.
            transaction {
                reference("REF DATA")
                input("ALICE CASH")
                output(Cash.PROGRAM_ID, bobCash.withNewOwner(BOB_PARTY).ownableState)
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
            fails() // "double spend" of ExampleState!! Alice updated it in the 3rd transaction.
        }
    }

    @Test(timeout=300_000)
	fun `state ref cannot be a reference input and regular input in the same transaction`() {
        val state = ExampleState(ALICE_PARTY, "HELLO CORDA")
        val stateAndRef = StateAndRef(TransactionState(state, CONTRACT_ID, DUMMY_NOTARY, constraint = AlwaysAcceptAttachmentConstraint), StateRef(SecureHash.zeroHash, 0))
        assertThatIllegalArgumentException().isThrownBy {
            TransactionBuilder(notary = DUMMY_NOTARY)
                    .addInputState(stateAndRef).addReferenceState(stateAndRef.referenced())
        }.withMessage("A StateRef cannot be both an input and a reference input in the same transaction.")
    }
}

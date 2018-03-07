package net.corda.core.transactions

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.finance.DOLLARS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Rule
import org.junit.Test

val REF_DATA_CONTRACT_ID = "net.corda.core.transactions.UnspendableInputTests\$RefDataContract"

class UnspendableInputTests {
    private companion object {
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
        val MINI_CORP = TestIdentity(CordaX500Name("MiniCorp", "London", "GB")).party
        val MEGA_CORP get() = megaCorp.party
        val MEGA_CORP_PUBKEY get() = megaCorp.publicKey
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    val defaultIssuer = MEGA_CORP.ref(1)

    val cashState = Cash.State(
            amount = 1000.DOLLARS `issued by` defaultIssuer,
            owner = MEGA_CORP
    )
    val cashStateWithNewOwner = cashState.copy(owner = MINI_CORP)

    // TODO Move this into another module... RefData module??
    // Question: Could this verification logic be moved to LedgerTransaction.verify() as it's the same for all
    // unspendable states. We don't care about the actual data... As only one party can update this data it doesn't
    // make too much sense to use custom contracts that control how it can be added / updated.
    class RefDataContract : Contract {

        interface Commands : CommandData
        class Create : Commands
        class Update : Commands
        class Refer : Commands

        override fun verify(tx: LedgerTransaction) {
            val command = tx.commands.requireSingleCommand<Commands>()
            when (command.value) {
                is Create -> {
                    require(tx.inputs.isEmpty()) { "There must be no inputs." }
                    require(tx.outputs.size == 1) { "There must only be one input." }
                    val refStateOwner = tx.outRefsOfType<RefDataContract.State>().single().state.data.owner.owningKey
                    val signer = tx.commands.flatMap { it.signers }.single()
                    require(refStateOwner == signer) { "Creation transaction must be signed by owner." }
                }
                is Refer -> {
                    require(tx.inputsOfType<RefDataContract.State>().isEmpty()) { "You can't spent this." }
                    require(tx.outputsOfType<RefDataContract.State>().isEmpty()) { "You can't spent this." }
                    require(tx.referenceInputs.isNotEmpty()) { "Unspendable inputs must be in the correct list." }
                }
                is Update -> throw TODO("Later...")
            }
        }

        data class State(
                val data: String,
                override val owner: AbstractParty,
                override val linearId: UniqueIdentifier = UniqueIdentifier()
        ) : ReferenceState
    }

    private val ledgerServices = MockServices(listOf("net.corda.core.transactions", "net.corda.finance.contracts.asset"), MEGA_CORP.name,
            rigorousMock<IdentityServiceInternal>().also {
                doReturn(MEGA_CORP).whenever(it).partyFromKey(MEGA_CORP_PUBKEY)
            })

    @Test
    fun `create and use unspendable state`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            // Create an unspendable input.
            // The unspendable input is created in the normal way. A transaction with one or more outputs. It makes sense
            // to create them one at a time though, so the owner can have fine grained control over who sees what.
            transaction {
                output(REF_DATA_CONTRACT_ID, "REF DATA", RefDataContract.State("HELLO CORDA", megaCorp.party))
                command(megaCorp.publicKey, RefDataContract.Create())
                verifies()
            }
            // Refer to an unspendable input.
            // It is added to a different inputs list. The contract, above, determines how transactions containing these
            // unspendable inputs should be verified.
            // The logic to verify these unspendable states should be the same no matter what data they contain.
            transaction {
                unspendableInput("REF DATA")
                command(megaCorp.publicKey, RefDataContract.Refer())
                input(Cash.PROGRAM_ID, cashState)
                output(Cash.PROGRAM_ID, cashStateWithNewOwner)
                command(megaCorp.publicKey, Cash.Commands.Move())
                verifies()
            }
            // Try to spend an unspendable input by accident. Opps! This should fail as per the contract above.
            transaction {
                input("REF DATA")
                command(megaCorp.publicKey, RefDataContract.Refer())
                input(Cash.PROGRAM_ID, cashState)
                output(Cash.PROGRAM_ID, cashStateWithNewOwner)
                command(megaCorp.publicKey, Cash.Commands.Move())
                fails()
            }
        }
    }

}
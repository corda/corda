import contracts.Cash
import contracts.DummyContract
import contracts.InsufficientBalanceException
import core.*
import org.junit.Test
import testutils.*
import java.security.PublicKey
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CashTests {
    val inState = Cash.State(
            deposit = InstitutionReference(MEGA_CORP, OpaqueBytes.of(1)),
            amount = 1000.DOLLARS,
            owner = DUMMY_PUBKEY_1
    )
    val outState = inState.copy(owner = DUMMY_PUBKEY_2)

    fun Cash.State.editInstitution(institution: Institution) = copy(deposit = deposit.copy(institution = institution))
    fun Cash.State.editDepositRef(ref: Byte) = copy(deposit = deposit.copy(reference = OpaqueBytes.of(ref)))

    @Test
    fun trivial() {
        transaction {
            input { inState }
            this `fails requirement` "the amounts balance"

            transaction {
                output { outState.copy(amount = 2000.DOLLARS )}
                this `fails requirement` "the amounts balance"
            }
            transaction {
                output { outState }
                // No command arguments
                this `fails requirement` "required contracts.Cash.Commands.Move command"
            }
            transaction {
                output { outState }
                arg(DUMMY_PUBKEY_2) { Cash.Commands.Move }
                this `fails requirement` "the owning keys are the same as the signing keys"
            }
            transaction {
                output { outState }
                output { outState.editInstitution(MINI_CORP) }
                this `fails requirement` "no output states are unaccounted for"
            }
            // Simple reallocation works.
            transaction {
                output { outState }
                arg(DUMMY_PUBKEY_1) { Cash.Commands.Move }
                this.accepts()
            }
        }
    }

    @Test
    fun issueMoney() {
        // Check we can't "move" money into existence.
        transaction {
            input { DummyContract.State() }
            output { outState }
            arg { Cash.Commands.Move }

            this `fails requirement` "there is at least one cash input"
        }

        // Check we can issue money only as long as the issuer institution is a command signer, i.e. any recognised
        // institution is allowed to issue as much cash as they want.
        transaction {
            output { outState }
            arg { Cash.Commands.Issue() }
            this `fails requirement` "output deposits are owned by a command signer"
        }
        transaction {
            output {
                Cash.State(
                    amount = 1000.DOLLARS,
                    owner = DUMMY_PUBKEY_1,
                    deposit = InstitutionReference(MINI_CORP, OpaqueBytes.of(12, 34))
                )
            }
            transaction {
                arg(MINI_CORP_KEY) { Cash.Commands.Issue(0) }
                this `fails requirement` "has a nonce"
            }
            arg(MINI_CORP_KEY) { Cash.Commands.Issue() }
            this.accepts()
        }

        val ptx = PartialTransaction()
        Cash.craftIssue(ptx, 100.DOLLARS, InstitutionReference(MINI_CORP, OpaqueBytes.of(12, 34)), owner = DUMMY_PUBKEY_1)
        assertTrue(ptx.inputStates().isEmpty())
        val s = ptx.outputStates()[0] as Cash.State
        assertEquals(100.DOLLARS, s.amount)
        assertEquals(MINI_CORP, s.deposit.institution)
        assertEquals(DUMMY_PUBKEY_1, s.owner)
        assertTrue(ptx.args()[0].command is Cash.Commands.Issue)
        assertEquals(MINI_CORP_KEY, ptx.args()[0].pubkeys[0])
    }

    @Test
    fun testMergeSplit() {
        // Splitting value works.
        transaction {
            arg(DUMMY_PUBKEY_1) { Cash.Commands.Move }
            transaction {
                input { inState }
                for (i in 1..4) output { inState.copy(amount = inState.amount / 4) }
                this.accepts()
            }
            // Merging 4 inputs into 2 outputs works.
            transaction {
                for (i in 1..4) input { inState.copy(amount = inState.amount / 4) }
                output { inState.copy(amount = inState.amount / 2) }
                output { inState.copy(amount = inState.amount / 2) }
                this.accepts()
            }
            // Merging 2 inputs into 1 works.
            transaction {
                input { inState.copy(amount = inState.amount / 2) }
                input { inState.copy(amount = inState.amount / 2) }
                output { inState }
                this.accepts()
            }
        }

    }

    @Test
    fun zeroSizedInputs() {
        transaction {
            input { inState }
            input { inState.copy(amount = 0.DOLLARS) }
            this `fails requirement` "zero sized inputs"
        }
    }

    @Test
    fun trivialMismatches() {
        // Can't change issuer.
        transaction {
            input { inState }
            output { outState.editInstitution(MINI_CORP) }
            this `fails requirement` "at issuer MegaCorp the amounts balance"
        }
        // Can't change deposit reference when splitting.
        transaction {
            input { inState }
            output { outState.editDepositRef(0).copy(amount = inState.amount / 2) }
            output { outState.editDepositRef(1).copy(amount = inState.amount / 2) }
            this `fails requirement` "for deposit [01] at issuer MegaCorp the amounts balance"
        }
        // Can't mix currencies.
        transaction {
            input { inState }
            output { outState.copy(amount = 800.DOLLARS) }
            output { outState.copy(amount = 200.POUNDS) }
            this `fails requirement` "all outputs use the currency of the inputs"
        }
        transaction {
            input { inState }
            input {
                inState.copy(
                    amount = 150.POUNDS,
                    owner = DUMMY_PUBKEY_2
                )
            }
            output { outState.copy(amount = 1150.DOLLARS) }
            this `fails requirement` "all inputs use the same currency"
        }
        // Can't have superfluous input states from different issuers.
        transaction {
            input { inState }
            input { inState.editInstitution(MINI_CORP) }
            output { outState }
            this `fails requirement` "at issuer MiniCorp the amounts balance"
        }
        // Can't combine two different deposits at the same issuer.
        transaction {
            input { inState }
            input { inState.editDepositRef(3) }
            output { outState.copy(amount = inState.amount * 2).editDepositRef(3) }
            this `fails requirement` "for deposit [01]"
        }
    }

    @Test
    fun exitLedger() {
        // Single input/output straightforward case.
        transaction {
            input { inState }
            output { outState.copy(amount = inState.amount - 200.DOLLARS) }

            transaction {
                arg(MEGA_CORP_KEY) { Cash.Commands.Exit(100.DOLLARS) }
                arg(DUMMY_PUBKEY_1) { Cash.Commands.Move }
                this `fails requirement` "the amounts balance"
            }

            transaction {
                arg(MEGA_CORP_KEY) { Cash.Commands.Exit(200.DOLLARS) }
                this `fails requirement` "required contracts.Cash.Commands.Move command"

                transaction {
                    arg(DUMMY_PUBKEY_1) { Cash.Commands.Move }
                    this.accepts()
                }
            }
        }
        // Multi-issuer case.
        transaction {
            input { inState }
            input { inState.editInstitution(MINI_CORP) }

            output { inState.copy(amount = inState.amount - 200.DOLLARS).editInstitution(MINI_CORP) }
            output { inState.copy(amount = inState.amount - 200.DOLLARS) }

            arg(DUMMY_PUBKEY_1) { Cash.Commands.Move }

            this `fails requirement` "at issuer MegaCorp the amounts balance"

            arg(MEGA_CORP_KEY) { Cash.Commands.Exit(200.DOLLARS) }
            this `fails requirement` "at issuer MiniCorp the amounts balance"

            arg(MINI_CORP_KEY) { Cash.Commands.Exit(200.DOLLARS) }
            this.accepts()
        }
    }

    @Test
    fun multiIssuer() {
        transaction {
            // Gather 2000 dollars from two different issuers.
            input { inState }
            input { inState.editInstitution(MINI_CORP) }

            // Can't merge them together.
            transaction {
                output { inState.copy(owner = DUMMY_PUBKEY_2, amount = 2000.DOLLARS) }
                this `fails requirement` "at issuer MegaCorp the amounts balance"
            }
            // Missing MiniCorp deposit
            transaction {
                output { inState.copy(owner = DUMMY_PUBKEY_2) }
                output { inState.copy(owner = DUMMY_PUBKEY_2) }
                this `fails requirement` "at issuer MegaCorp the amounts balance"
            }

            // This works.
            output { inState.copy(owner = DUMMY_PUBKEY_2) }
            output { inState.copy(owner = DUMMY_PUBKEY_2).editInstitution(MINI_CORP) }
            arg(DUMMY_PUBKEY_1) { Cash.Commands.Move }
            this.accepts()
        }

        transaction {
            input { inState }
            input { inState }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Spend crafting

    val OUR_PUBKEY_1 = DUMMY_PUBKEY_1
    val THEIR_PUBKEY_1 = DUMMY_PUBKEY_2

    fun makeCash(amount: Amount, corp: Institution, depositRef: Byte = 1) =
            StateAndRef(
                    Cash.State(InstitutionReference(corp, OpaqueBytes.of(depositRef)), amount, OUR_PUBKEY_1),
                    ContractStateRef(SecureHash.randomSHA256(), Random().nextInt(32))
            )

    val WALLET = listOf(
            makeCash(100.DOLLARS, MEGA_CORP),
            makeCash(400.DOLLARS, MEGA_CORP),
            makeCash(80.DOLLARS, MINI_CORP),
            makeCash(80.SWISS_FRANCS, MINI_CORP, 2)
    )

    fun makeSpend(amount: Amount, dest: PublicKey): WireTransaction {
        val tx = PartialTransaction()
        Cash.craftSpend(tx, amount, dest, WALLET)
        return tx.toWireTransaction()
    }

    @Test
    fun craftSimpleDirectSpend() {
        val wtx = makeSpend(100.DOLLARS, THEIR_PUBKEY_1)
        assertEquals(WALLET[0].ref, wtx.inputStates[0])
        assertEquals(WALLET[0].state.copy(owner = THEIR_PUBKEY_1), wtx.outputStates[0])
        assertEquals(OUR_PUBKEY_1, wtx.args[0].pubkeys[0])
    }

    @Test
    fun craftSimpleSpendWithChange() {
        val wtx = makeSpend(10.DOLLARS, THEIR_PUBKEY_1)
        assertEquals(WALLET[0].ref, wtx.inputStates[0])
        assertEquals(WALLET[0].state.copy(owner = THEIR_PUBKEY_1, amount = 10.DOLLARS), wtx.outputStates[0])
        assertEquals(WALLET[0].state.copy(amount = 90.DOLLARS), wtx.outputStates[1])
        assertEquals(OUR_PUBKEY_1, wtx.args[0].pubkeys[0])
    }

    @Test
    fun craftSpendWithTwoInputs() {
        val wtx = makeSpend(500.DOLLARS, THEIR_PUBKEY_1)
        assertEquals(WALLET[0].ref, wtx.inputStates[0])
        assertEquals(WALLET[1].ref, wtx.inputStates[1])
        assertEquals(WALLET[0].state.copy(owner = THEIR_PUBKEY_1, amount = 500.DOLLARS), wtx.outputStates[0])
        assertEquals(OUR_PUBKEY_1, wtx.args[0].pubkeys[0])
    }

    @Test
    fun craftSpendMixedDeposits() {
        val wtx = makeSpend(580.DOLLARS, THEIR_PUBKEY_1)
        assertEquals(WALLET[0].ref, wtx.inputStates[0])
        assertEquals(WALLET[1].ref, wtx.inputStates[1])
        assertEquals(WALLET[2].ref, wtx.inputStates[2])
        assertEquals(WALLET[0].state.copy(owner = THEIR_PUBKEY_1, amount = 500.DOLLARS), wtx.outputStates[0])
        assertEquals(WALLET[2].state.copy(owner = THEIR_PUBKEY_1), wtx.outputStates[1])
        assertEquals(OUR_PUBKEY_1, wtx.args[0].pubkeys[0])
    }

    @Test
    fun craftSpendInsufficientBalance() {
        val e: InsufficientBalanceException = assertFailsWith("balance") {
            makeSpend(1000.DOLLARS, THEIR_PUBKEY_1)
        }
        assertEquals(1000 - 580, e.amountMissing.pennies / 100)

        assertFailsWith(InsufficientBalanceException::class) {
            makeSpend(81.SWISS_FRANCS, THEIR_PUBKEY_1)
        }
    }
}

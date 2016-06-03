package com.r3corda.contracts.cash

import com.r3corda.core.contracts.DummyContract
import com.r3corda.contracts.testing.`issued by`
import com.r3corda.contracts.testing.`owned by`
import com.r3corda.contracts.testing.`with deposit`
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.serialization.OpaqueBytes
import com.r3corda.core.testing.*
import org.junit.Test
import java.security.PublicKey
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CashTests {
    val defaultRef = OpaqueBytes(ByteArray(1, {1}))
    val defaultIssuer = MEGA_CORP.ref(defaultRef)
    val inState = Cash.State(
            amount = 1000.DOLLARS `issued by` defaultIssuer,
            owner = DUMMY_PUBKEY_1,
            notary = DUMMY_NOTARY
    )
    val outState = inState.copy(owner = DUMMY_PUBKEY_2)

    fun Cash.State.editDepositRef(ref: Byte) = copy(
            amount = Amount(amount.quantity, token = amount.token.copy(deposit.copy(reference = OpaqueBytes.of(ref))))
    )

    @Test
    fun trivial() {
        transaction {
            input { inState }
            this `fails requirement` "the amounts balance"

            tweak {
                output { outState.copy(amount = 2000.DOLLARS `issued by` defaultIssuer) }
                this `fails requirement` "the amounts balance"
            }
            tweak {
                output { outState }
                // No command arguments
                this `fails requirement` "required com.r3corda.contracts.cash.FungibleAsset.Commands.Move command"
            }
            tweak {
                output { outState }
                arg(DUMMY_PUBKEY_2) { Cash.Commands.Move() }
                this `fails requirement` "the owning keys are the same as the signing keys"
            }
            tweak {
                output { outState }
                output { outState `issued by` MINI_CORP }
                arg(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
                this `fails requirement` "at least one asset input"
            }
            // Simple reallocation works.
            tweak {
                output { outState }
                arg(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
                this.accepts()
            }
        }
    }

    @Test
    fun issueMoney() {
        // Check we can't "move" money into existence.
        transaction {
            input { DummyContract.State(notary = DUMMY_NOTARY) }
            output { outState }
            arg(MINI_CORP_PUBKEY) { Cash.Commands.Move() }

            this `fails requirement` "there is at least one asset input"
        }

        // Check we can issue money only as long as the issuer institution is a command signer, i.e. any recognised
        // institution is allowed to issue as much cash as they want.
        transaction {
            output { outState }
            arg(DUMMY_PUBKEY_1) { Cash.Commands.Issue() }
            this `fails requirement` "output deposits are owned by a command signer"
        }
        transaction {
            output {
                Cash.State(
                        amount = 1000.DOLLARS `issued by` MINI_CORP.ref(12, 34),
                        owner = DUMMY_PUBKEY_1,
                        notary = DUMMY_NOTARY
                )
            }
            tweak {
                arg(MINI_CORP_PUBKEY) { Cash.Commands.Issue(0) }
                this `fails requirement` "has a nonce"
            }
            arg(MINI_CORP_PUBKEY) { Cash.Commands.Issue() }
            this.accepts()
        }

        // Test generation works.
        val ptx = TransactionBuilder()
        Cash().generateIssue(ptx, 100.DOLLARS `issued by` MINI_CORP.ref(12, 34), owner = DUMMY_PUBKEY_1, notary = DUMMY_NOTARY)
        assertTrue(ptx.inputStates().isEmpty())
        val s = ptx.outputStates()[0] as Cash.State
        assertEquals(100.DOLLARS `issued by` MINI_CORP.ref(12, 34), s.amount)
        assertEquals(MINI_CORP, s.deposit.party)
        assertEquals(DUMMY_PUBKEY_1, s.owner)
        assertTrue(ptx.commands()[0].value is Cash.Commands.Issue)
        assertEquals(MINI_CORP_PUBKEY, ptx.commands()[0].signers[0])

        // Test issuance from the issuance definition
        val amount = 100.DOLLARS `issued by` MINI_CORP.ref(12, 34)
        val templatePtx = TransactionBuilder()
        Cash().generateIssue(templatePtx, amount, owner = DUMMY_PUBKEY_1, notary = DUMMY_NOTARY)
        assertTrue(templatePtx.inputStates().isEmpty())
        assertEquals(ptx.outputStates()[0], templatePtx.outputStates()[0])

        // We can consume $1000 in a transaction and output $2000 as long as it's signed by an issuer.
        transaction {
            input { inState }
            output { inState.copy(amount = inState.amount * 2) }

            // Move fails: not allowed to summon money.
            tweak {
                arg(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
                this `fails requirement` "at issuer MegaCorp the amounts balance"
            }

            // Issue works.
            tweak {
                arg(MEGA_CORP_PUBKEY) { Cash.Commands.Issue() }
                this.accepts()
            }
        }

        // Can't use an issue command to lower the amount.
        transaction {
            input { inState }
            output { inState.copy(amount = inState.amount / 2) }
            arg(MEGA_CORP_PUBKEY) { Cash.Commands.Issue() }
            this `fails requirement` "output values sum to more than the inputs"
        }

        // Can't have an issue command that doesn't actually issue money.
        transaction {
            input { inState }
            output { inState }
            arg(MEGA_CORP_PUBKEY) { Cash.Commands.Issue() }
            this `fails requirement` "output values sum to more than the inputs"
        }

        // Can't have any other commands if we have an issue command (because the issue command overrules them)
        transaction {
            input { inState }
            output { inState.copy(amount = inState.amount * 2) }
            arg(MEGA_CORP_PUBKEY) { Cash.Commands.Issue() }
            tweak {
                arg(MEGA_CORP_PUBKEY) { Cash.Commands.Issue() }
                this `fails requirement` "there is only a single issue command"
            }
            tweak {
                arg(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
                this `fails requirement` "there is only a single issue command"
            }
            tweak {
                arg(MEGA_CORP_PUBKEY) { Cash.Commands.Exit(inState.amount / 2) }
                this `fails requirement` "there is only a single issue command"
            }
            this.accepts()
        }
    }

    /**
     * Test that the issuance builder rejects building into a transaction with existing
     * cash inputs.
     */
    @Test(expected = IllegalStateException::class)
    fun `reject issuance with inputs`() {
        // Issue some cash
        var ptx = TransactionBuilder()

        Cash().generateIssue(ptx, 100.DOLLARS `issued by` MINI_CORP.ref(12, 34), owner = MINI_CORP_PUBKEY, notary = DUMMY_NOTARY)
        ptx.signWith(MINI_CORP_KEY)
        val tx = ptx.toSignedTransaction()

        // Include the previously issued cash in a new issuance command
        ptx = TransactionBuilder()
        ptx.addInputState(tx.tx.outRef<Cash.State>(0).ref)
        Cash().generateIssue(ptx, 100.DOLLARS `issued by`  MINI_CORP.ref(12, 34), owner = MINI_CORP_PUBKEY, notary = DUMMY_NOTARY)
    }

    @Test
    fun testMergeSplit() {
        // Splitting value works.
        transaction {
            arg(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
            tweak {
                input { inState }
                for (i in 1..4) output { inState.copy(amount = inState.amount / 4) }
                this.accepts()
            }
            // Merging 4 inputs into 2 outputs works.
            tweak {
                for (i in 1..4) input { inState.copy(amount = inState.amount / 4) }
                output { inState.copy(amount = inState.amount / 2) }
                output { inState.copy(amount = inState.amount / 2) }
                this.accepts()
            }
            // Merging 2 inputs into 1 works.
            tweak {
                input { inState.copy(amount = inState.amount / 2) }
                input { inState.copy(amount = inState.amount / 2) }
                output { inState }
                this.accepts()
            }
        }
    }

    @Test
    fun zeroSizedValues() {
        transaction {
            input { inState }
            input { inState.copy(amount = 0.DOLLARS `issued by` defaultIssuer) }
            this `fails requirement` "zero sized inputs"
        }
        transaction {
            input { inState }
            output { inState }
            output { inState.copy(amount = 0.DOLLARS `issued by` defaultIssuer) }
            this `fails requirement` "zero sized outputs"
        }
    }

    @Test
    fun trivialMismatches() {
        // Can't change issuer.
        transaction {
            input { inState }
            output { outState `issued by` MINI_CORP }
            this `fails requirement` "at issuer MegaCorp the amounts balance"
        }
        // Can't change deposit reference when splitting.
        transaction {
            input { inState }
            output { outState.copy(amount = inState.amount / 2).editDepositRef(0) }
            output { outState.copy(amount = inState.amount / 2).editDepositRef(1) }
            this `fails requirement` "for deposit [01] at issuer MegaCorp the amounts balance"
        }
        // Can't mix currencies.
        transaction {
            input { inState }
            output { outState.copy(amount = 800.DOLLARS `issued by` defaultIssuer) }
            output { outState.copy(amount = 200.POUNDS `issued by` defaultIssuer) }
            this `fails requirement` "the amounts balance"
        }
        transaction {
            input { inState }
            input {
                inState.copy(
                        amount = 150.POUNDS `issued by` defaultIssuer,
                        owner = DUMMY_PUBKEY_2
                )
            }
            output { outState.copy(amount = 1150.DOLLARS `issued by` defaultIssuer) }
            this `fails requirement` "the amounts balance"
        }
        // Can't have superfluous input states from different issuers.
        transaction {
            input { inState }
            input { inState `issued by` MINI_CORP }
            output { outState }
            arg(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
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
            output { outState.copy(amount = inState.amount - (200.DOLLARS `issued by` defaultIssuer)) }

            tweak {
                arg(MEGA_CORP_PUBKEY) { Cash.Commands.Exit(100.DOLLARS `issued by` defaultIssuer) }
                arg(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
                this `fails requirement` "the amounts balance"
            }

            tweak {
                arg(MEGA_CORP_PUBKEY) { Cash.Commands.Exit(200.DOLLARS `issued by` defaultIssuer) }
                this `fails requirement` "required com.r3corda.contracts.cash.FungibleAsset.Commands.Move command"

                tweak {
                    arg(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
                    this.accepts()
                }
            }
        }
        // Multi-issuer case.
        transaction {
            input { inState }
            input { inState `issued by` MINI_CORP }

            output { inState.copy(amount = inState.amount - (200.DOLLARS `issued by` defaultIssuer)) `issued by` MINI_CORP }
            output { inState.copy(amount = inState.amount - (200.DOLLARS `issued by` defaultIssuer)) }

            arg(DUMMY_PUBKEY_1) { Cash.Commands.Move() }

            this `fails requirement` "at issuer MegaCorp the amounts balance"

            arg(MEGA_CORP_PUBKEY) { Cash.Commands.Exit(200.DOLLARS `issued by` defaultIssuer) }
            this `fails requirement` "at issuer MiniCorp the amounts balance"

            arg(MINI_CORP_PUBKEY) { Cash.Commands.Exit(200.DOLLARS `issued by` MINI_CORP.ref(defaultRef)) }
            this.accepts()
        }
    }

    @Test
    fun multiIssuer() {
        transaction {
            // Gather 2000 dollars from two different issuers.
            input { inState }
            input { inState `issued by` MINI_CORP }

            // Can't merge them together.
            tweak {
                output { inState.copy(owner = DUMMY_PUBKEY_2, amount = 2000.DOLLARS `issued by` defaultIssuer) }
                this `fails requirement` "at issuer MegaCorp the amounts balance"
            }
            // Missing MiniCorp deposit
            tweak {
                output { inState.copy(owner = DUMMY_PUBKEY_2) }
                output { inState.copy(owner = DUMMY_PUBKEY_2) }
                this `fails requirement` "at issuer MegaCorp the amounts balance"
            }

            // This works.
            output { inState.copy(owner = DUMMY_PUBKEY_2) }
            output { inState.copy(owner = DUMMY_PUBKEY_2) `issued by` MINI_CORP }
            arg(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
            this.accepts()
        }
    }

    @Test
    fun multiCurrency() {
        // Check we can do an atomic currency trade tx.
        transaction {
            val pounds = Cash.State(658.POUNDS `issued by` MINI_CORP.ref(3, 4, 5), DUMMY_PUBKEY_2, DUMMY_NOTARY)
            input { inState `owned by` DUMMY_PUBKEY_1 }
            input { pounds }
            output { inState `owned by` DUMMY_PUBKEY_2 }
            output { pounds `owned by` DUMMY_PUBKEY_1 }
            arg(DUMMY_PUBKEY_1, DUMMY_PUBKEY_2) { Cash.Commands.Move() }

            this.accepts()
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Spend tx generation

    val OUR_PUBKEY_1 = DUMMY_PUBKEY_1
    val THEIR_PUBKEY_1 = DUMMY_PUBKEY_2

    fun makeCash(amount: Amount<Currency>, corp: Party, depositRef: Byte = 1) =
            StateAndRef(
                    Cash.State(amount `issued by` corp.ref(depositRef), OUR_PUBKEY_1, DUMMY_NOTARY),
                    StateRef(SecureHash.randomSHA256(), Random().nextInt(32))
            )

    val WALLET = listOf(
            makeCash(100.DOLLARS, MEGA_CORP),
            makeCash(400.DOLLARS, MEGA_CORP),
            makeCash(80.DOLLARS, MINI_CORP),
            makeCash(80.SWISS_FRANCS, MINI_CORP, 2)
    )

    fun makeSpend(amount: Amount<Currency>, dest: PublicKey, corp: Party, depositRef: OpaqueBytes = defaultRef): WireTransaction {
        val tx = TransactionBuilder()
        Cash().generateSpend(tx, amount, dest, WALLET)
        return tx.toWireTransaction()
    }

    @Test
    fun generateSimpleDirectSpend() {
        val wtx = makeSpend(100.DOLLARS, THEIR_PUBKEY_1, MEGA_CORP)
        assertEquals(WALLET[0].ref, wtx.inputs[0])
        assertEquals(WALLET[0].state.copy(owner = THEIR_PUBKEY_1), wtx.outputs[0])
        assertEquals(OUR_PUBKEY_1, wtx.commands.single { it.value is Cash.Commands.Move }.signers[0])
    }

    @Test
    fun generateSimpleSpendWithParties() {
        val tx = TransactionBuilder()
        Cash().generateSpend(tx, 80.DOLLARS, ALICE_PUBKEY, WALLET, setOf(MINI_CORP))
        assertEquals(WALLET[2].ref, tx.inputStates()[0])
    }

    @Test
    fun generateSimpleSpendWithChange() {
        val wtx = makeSpend(10.DOLLARS, THEIR_PUBKEY_1, MEGA_CORP)
        assertEquals(WALLET[0].ref, wtx.inputs[0])
        assertEquals(WALLET[0].state.copy(owner = THEIR_PUBKEY_1, amount = 10.DOLLARS `issued by` defaultIssuer), wtx.outputs[0])
        assertEquals(WALLET[0].state.copy(amount = 90.DOLLARS `issued by` defaultIssuer), wtx.outputs[1])
        assertEquals(OUR_PUBKEY_1, wtx.commands.single { it.value is Cash.Commands.Move }.signers[0])
    }

    @Test
    fun generateSpendWithTwoInputs() {
        val wtx = makeSpend(500.DOLLARS, THEIR_PUBKEY_1, MEGA_CORP)
        assertEquals(WALLET[0].ref, wtx.inputs[0])
        assertEquals(WALLET[1].ref, wtx.inputs[1])
        assertEquals(WALLET[0].state.copy(owner = THEIR_PUBKEY_1, amount = 500.DOLLARS `issued by` defaultIssuer), wtx.outputs[0])
        assertEquals(OUR_PUBKEY_1, wtx.commands.single { it.value is Cash.Commands.Move }.signers[0])
    }

    @Test
    fun generateSpendMixedDeposits() {
        val wtx = makeSpend(580.DOLLARS, THEIR_PUBKEY_1, MEGA_CORP)
        assertEquals(3, wtx.inputs.size)
        assertEquals(WALLET[0].ref, wtx.inputs[0])
        assertEquals(WALLET[1].ref, wtx.inputs[1])
        assertEquals(WALLET[2].ref, wtx.inputs[2])
        assertEquals(WALLET[0].state.copy(owner = THEIR_PUBKEY_1, amount = 500.DOLLARS `issued by` defaultIssuer), wtx.outputs[0])
        assertEquals(WALLET[2].state.copy(owner = THEIR_PUBKEY_1), wtx.outputs[1])
        assertEquals(OUR_PUBKEY_1, wtx.commands.single { it.value is Cash.Commands.Move }.signers[0])
    }

    @Test
    fun generateSpendInsufficientBalance() {
        val e: InsufficientBalanceException = assertFailsWith("balance") {
            makeSpend(1000.DOLLARS, THEIR_PUBKEY_1, MEGA_CORP)
        }
        assertEquals((1000 - 580).DOLLARS, e.amountMissing)

        assertFailsWith(InsufficientBalanceException::class) {
            makeSpend(81.SWISS_FRANCS, THEIR_PUBKEY_1, MEGA_CORP)
        }
    }

    /**
     * Confirm that aggregation of states is correctly modelled.
     */
    @Test
    fun aggregation() {
        val fiveThousandDollarsFromMega = Cash.State(5000.DOLLARS `issued by` MEGA_CORP.ref(2), MEGA_CORP_PUBKEY, DUMMY_NOTARY)
        val twoThousandDollarsFromMega = Cash.State(2000.DOLLARS `issued by` MEGA_CORP.ref(2), MINI_CORP_PUBKEY, DUMMY_NOTARY)
        val oneThousandDollarsFromMini = Cash.State(1000.DOLLARS `issued by` MINI_CORP.ref(3), MEGA_CORP_PUBKEY, DUMMY_NOTARY)

        // Obviously it must be possible to aggregate states with themselves
        assertEquals(fiveThousandDollarsFromMega.issuanceDef, fiveThousandDollarsFromMega.issuanceDef)

        // Owner is not considered when calculating whether it is possible to aggregate states
        assertEquals(fiveThousandDollarsFromMega.issuanceDef, twoThousandDollarsFromMega.issuanceDef)

        // States cannot be aggregated if the deposit differs
        assertNotEquals(fiveThousandDollarsFromMega.issuanceDef, oneThousandDollarsFromMini.issuanceDef)
        assertNotEquals(twoThousandDollarsFromMega.issuanceDef, oneThousandDollarsFromMini.issuanceDef)

        // States cannot be aggregated if the currency differs
        assertNotEquals(oneThousandDollarsFromMini.issuanceDef,
                Cash.State(1000.POUNDS `issued by` MINI_CORP.ref(3), MEGA_CORP_PUBKEY, DUMMY_NOTARY).issuanceDef)

        // States cannot be aggregated if the reference differs
        assertNotEquals(fiveThousandDollarsFromMega.issuanceDef, (fiveThousandDollarsFromMega `with deposit` defaultIssuer).issuanceDef)
        assertNotEquals((fiveThousandDollarsFromMega `with deposit` defaultIssuer).issuanceDef, fiveThousandDollarsFromMega.issuanceDef)
    }

    @Test
    fun `summing by owner`() {
        val states = listOf(
                Cash.State(1000.DOLLARS `issued by` defaultIssuer, MINI_CORP_PUBKEY, DUMMY_NOTARY),
                Cash.State(2000.DOLLARS `issued by` defaultIssuer, MEGA_CORP_PUBKEY, DUMMY_NOTARY),
                Cash.State(4000.DOLLARS `issued by` defaultIssuer, MEGA_CORP_PUBKEY, DUMMY_NOTARY)
        )
        assertEquals(6000.DOLLARS `issued by` defaultIssuer, states.sumCashBy(MEGA_CORP_PUBKEY))
    }

    @Test(expected = UnsupportedOperationException::class)
    fun `summing by owner throws`() {
        val states = listOf(
                Cash.State(2000.DOLLARS `issued by` defaultIssuer, MEGA_CORP_PUBKEY, DUMMY_NOTARY),
                Cash.State(4000.DOLLARS `issued by` defaultIssuer, MEGA_CORP_PUBKEY, DUMMY_NOTARY)
        )
        states.sumCashBy(MINI_CORP_PUBKEY)
    }

    @Test
    fun `summing no currencies`() {
        val states = emptyList<Cash.State>()
        assertEquals(0.POUNDS `issued by` defaultIssuer, states.sumCashOrZero(GBP `issued by` defaultIssuer))
        assertNull(states.sumCashOrNull())
    }

    @Test(expected = UnsupportedOperationException::class)
    fun `summing no currencies throws`() {
        val states = emptyList<Cash.State>()
        states.sumCash()
    }

    @Test
    fun `summing a single currency`() {
        val states = listOf(
                Cash.State(1000.DOLLARS `issued by` defaultIssuer, MEGA_CORP_PUBKEY, DUMMY_NOTARY),
                Cash.State(2000.DOLLARS `issued by` defaultIssuer, MEGA_CORP_PUBKEY, DUMMY_NOTARY),
                Cash.State(4000.DOLLARS `issued by` defaultIssuer, MEGA_CORP_PUBKEY, DUMMY_NOTARY)
        )
        // Test that summing everything produces the total number of dollars
        var expected = 7000.DOLLARS `issued by` defaultIssuer
        var actual = states.sumCash()
        assertEquals(expected, actual)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `summing multiple currencies`() {
        val states = listOf(
                Cash.State(1000.DOLLARS `issued by` defaultIssuer, MEGA_CORP_PUBKEY, DUMMY_NOTARY),
                Cash.State(4000.POUNDS `issued by` defaultIssuer, MEGA_CORP_PUBKEY, DUMMY_NOTARY)
        )
        // Test that summing everything fails because we're mixing units
        states.sumCash()
    }
}

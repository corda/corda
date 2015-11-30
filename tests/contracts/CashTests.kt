/*
 * Copyright 2015, R3 CEV. All rights reserved.
 */

import contracts.Cash
import contracts.DummyContract
import contracts.InsufficientBalanceException
import core.*
import core.testutils.*
import org.junit.Test
import java.security.PublicKey
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CashTests {
    val inState = Cash.State(
            deposit = MEGA_CORP.ref(1),
            amount = 1000.DOLLARS,
            owner = DUMMY_PUBKEY_1
    )
    val outState = inState.copy(owner = DUMMY_PUBKEY_2)

    fun Cash.State.editInstitution(party: Party) = copy(deposit = deposit.copy(party = party))
    fun Cash.State.editDepositRef(ref: Byte) = copy(deposit = deposit.copy(reference = OpaqueBytes.of(ref)))

    @Test
    fun trivial() {
        transaction {
            input { inState }
            this `fails requirement` "the amounts balance"

            tweak {
                output { outState.copy(amount = 2000.DOLLARS )}
                this `fails requirement` "the amounts balance"
            }
            tweak {
                output { outState }
                // No command arguments
                this `fails requirement` "required contracts.Cash.Commands.Move command"
            }
            tweak {
                output { outState }
                arg(DUMMY_PUBKEY_2) { Cash.Commands.Move() }
                this `fails requirement` "the owning keys are the same as the signing keys"
            }
            tweak {
                output { outState }
                output { outState.editInstitution(MINI_CORP) }
                arg(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
                this `fails requirement` "at least one cash input"
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
            input { DummyContract.State() }
            output { outState }
            arg { Cash.Commands.Move() }

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
                    deposit = MINI_CORP.ref(12, 34)
                )
            }
            tweak {
                arg(MINI_CORP_PUBKEY) { Cash.Commands.Issue(0) }
                this `fails requirement` "has a nonce"
            }
            arg(MINI_CORP_PUBKEY) { Cash.Commands.Issue() }
            this.accepts()
        }

        val ptx = PartialTransaction()
        Cash().craftIssue(ptx, 100.DOLLARS, MINI_CORP.ref(12,34), owner = DUMMY_PUBKEY_1)
        assertTrue(ptx.inputStates().isEmpty())
        val s = ptx.outputStates()[0] as Cash.State
        assertEquals(100.DOLLARS, s.amount)
        assertEquals(MINI_CORP, s.deposit.party)
        assertEquals(DUMMY_PUBKEY_1, s.owner)
        assertTrue(ptx.commands()[0].command is Cash.Commands.Issue)
        assertEquals(MINI_CORP_PUBKEY, ptx.commands()[0].pubkeys[0])
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
            this `fails requirement` "the amounts balance"
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
            this `fails requirement` "the amounts balance"
        }
        // Can't have superfluous input states from different issuers.
        transaction {
            input { inState }
            input { inState.editInstitution(MINI_CORP) }
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
            output { outState.copy(amount = inState.amount - 200.DOLLARS) }

            tweak {
                arg(MEGA_CORP_PUBKEY) { Cash.Commands.Exit(100.DOLLARS) }
                arg(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
                this `fails requirement` "the amounts balance"
            }

            tweak {
                arg(MEGA_CORP_PUBKEY) { Cash.Commands.Exit(200.DOLLARS) }
                this `fails requirement` "required contracts.Cash.Commands.Move command"

                tweak {
                    arg(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
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

            arg(DUMMY_PUBKEY_1) { Cash.Commands.Move() }

            this `fails requirement` "at issuer MegaCorp the amounts balance"

            arg(MEGA_CORP_PUBKEY) { Cash.Commands.Exit(200.DOLLARS) }
            this `fails requirement` "at issuer MiniCorp the amounts balance"

            arg(MINI_CORP_PUBKEY) { Cash.Commands.Exit(200.DOLLARS) }
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
            tweak {
                output { inState.copy(owner = DUMMY_PUBKEY_2, amount = 2000.DOLLARS) }
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
            output { inState.copy(owner = DUMMY_PUBKEY_2).editInstitution(MINI_CORP) }
            arg(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
            this.accepts()
        }

        transaction {
            input { inState }
            input { inState }
        }
    }

    @Test
    fun multiCurrency() {
        // Check we can do an atomic currency trade tx.
        transaction {
            val pounds = Cash.State(MINI_CORP.ref(3, 4, 5), 658.POUNDS, DUMMY_PUBKEY_2)
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
    // Spend crafting

    val OUR_PUBKEY_1 = DUMMY_PUBKEY_1
    val THEIR_PUBKEY_1 = DUMMY_PUBKEY_2

    fun makeCash(amount: Amount, corp: Party, depositRef: Byte = 1) =
            StateAndRef(
                    Cash.State(corp.ref(depositRef), amount, OUR_PUBKEY_1),
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
        Cash().craftSpend(tx, amount, dest, WALLET)
        return tx.toWireTransaction()
    }

    @Test
    fun craftSimpleDirectSpend() {
        val wtx = makeSpend(100.DOLLARS, THEIR_PUBKEY_1)
        assertEquals(WALLET[0].ref, wtx.inputStates[0])
        assertEquals(WALLET[0].state.copy(owner = THEIR_PUBKEY_1), wtx.outputStates[0])
        assertEquals(OUR_PUBKEY_1, wtx.commands[0].pubkeys[0])
    }

    @Test
    fun craftSimpleSpendWithChange() {
        val wtx = makeSpend(10.DOLLARS, THEIR_PUBKEY_1)
        assertEquals(WALLET[0].ref, wtx.inputStates[0])
        assertEquals(WALLET[0].state.copy(owner = THEIR_PUBKEY_1, amount = 10.DOLLARS), wtx.outputStates[0])
        assertEquals(WALLET[0].state.copy(amount = 90.DOLLARS), wtx.outputStates[1])
        assertEquals(OUR_PUBKEY_1, wtx.commands[0].pubkeys[0])
    }

    @Test
    fun craftSpendWithTwoInputs() {
        val wtx = makeSpend(500.DOLLARS, THEIR_PUBKEY_1)
        assertEquals(WALLET[0].ref, wtx.inputStates[0])
        assertEquals(WALLET[1].ref, wtx.inputStates[1])
        assertEquals(WALLET[0].state.copy(owner = THEIR_PUBKEY_1, amount = 500.DOLLARS), wtx.outputStates[0])
        assertEquals(OUR_PUBKEY_1, wtx.commands[0].pubkeys[0])
    }

    @Test
    fun craftSpendMixedDeposits() {
        val wtx = makeSpend(580.DOLLARS, THEIR_PUBKEY_1)
        assertEquals(WALLET[0].ref, wtx.inputStates[0])
        assertEquals(WALLET[1].ref, wtx.inputStates[1])
        assertEquals(WALLET[2].ref, wtx.inputStates[2])
        assertEquals(WALLET[0].state.copy(owner = THEIR_PUBKEY_1, amount = 500.DOLLARS), wtx.outputStates[0])
        assertEquals(WALLET[2].state.copy(owner = THEIR_PUBKEY_1), wtx.outputStates[1])
        assertEquals(OUR_PUBKEY_1, wtx.commands[0].pubkeys[0])
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

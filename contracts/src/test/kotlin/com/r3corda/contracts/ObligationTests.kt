package com.r3corda.contracts

import com.r3corda.contracts.cash.Cash
import com.r3corda.contracts.Obligation.Lifecycle
import com.r3corda.contracts.testing.*
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.testing.*
import com.r3corda.core.utilities.nonEmptySetOf
import org.junit.Test
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.test.*

class ObligationTests {
    val defaultIssuer = MEGA_CORP.ref(1)
    val defaultUsd = USD `issued by` defaultIssuer
    val oneMillionDollars = 1000000.DOLLARS `issued by` defaultIssuer
    val trustedCashContract = nonEmptySetOf(SecureHash.randomSHA256() as SecureHash)
    val megaIssuedDollars = nonEmptySetOf(Issued<Currency>(defaultIssuer, USD))
    val megaIssuedPounds = nonEmptySetOf(Issued<Currency>(defaultIssuer, GBP))
    val fivePm = Instant.parse("2016-01-01T17:00:00.00Z")
    val sixPm = Instant.parse("2016-01-01T18:00:00.00Z")
    val notary = MEGA_CORP
    val megaCorpDollarSettlement = Obligation.StateTemplate(trustedCashContract, megaIssuedDollars, fivePm)
    val megaCorpPoundSettlement = megaCorpDollarSettlement.copy(acceptableIssuedProducts = megaIssuedPounds)
    val inState = Obligation.State(
            lifecycle = Lifecycle.NORMAL,
            obligor = MEGA_CORP,
            template = megaCorpDollarSettlement,
            quantity = 1000.DOLLARS.quantity,
            beneficiary = DUMMY_PUBKEY_1
    )
    val outState = inState.copy(beneficiary = DUMMY_PUBKEY_2)

    private fun obligationTestRoots(group: TransactionGroupDSL<Obligation.State<Currency>>) = group.Roots()
            .transaction(oneMillionDollars.OBLIGATION `between` Pair(ALICE, BOB_PUBKEY) `with notary` DUMMY_NOTARY label "Alice's $1,000,000 obligation to Bob")
            .transaction(oneMillionDollars.OBLIGATION `between` Pair(BOB, ALICE_PUBKEY) `with notary` DUMMY_NOTARY label "Bob's $1,000,000 obligation to Alice")
            .transaction(oneMillionDollars.OBLIGATION `between` Pair(MEGA_CORP, BOB_PUBKEY) `with notary` DUMMY_NOTARY label "MegaCorp's $1,000,000 obligation to Bob")
            .transaction(1000000.DOLLARS.CASH `issued by` defaultIssuer `owned by` ALICE_PUBKEY `with notary` DUMMY_NOTARY label "Alice's $1,000,000")

    @Test
    fun trivial() {
        transaction {
            input { inState }
            this `fails requirement` "the amounts balance"

            tweak {
                output { outState.copy(quantity = 2000.DOLLARS.quantity) }
                this `fails requirement` "the amounts balance"
            }
            tweak {
                output { outState }
                // No command arguments
                this `fails requirement` "required com.r3corda.contracts.Obligation.Commands.Move command"
            }
            tweak {
                output { outState }
                arg(DUMMY_PUBKEY_2) { Obligation.Commands.Move(inState.issuanceDef) }
                this `fails requirement` "the owning keys are the same as the signing keys"
            }
            tweak {
                output { outState }
                output { outState `issued by` MINI_CORP }
                arg(DUMMY_PUBKEY_1) { Obligation.Commands.Move(inState.issuanceDef) }
                this `fails requirement` "at least one obligation input"
            }
            // Simple reallocation works.
            tweak {
                output { outState }
                arg(DUMMY_PUBKEY_1) { Obligation.Commands.Move(inState.issuanceDef) }
                this.accepts()
            }
        }
    }

    @Test
    fun `issue debt`() {
        // Check we can't "move" debt into existence.
        transaction {
            input { DummyState() }
            output { outState }
            arg(MINI_CORP_PUBKEY) { Obligation.Commands.Move(outState.issuanceDef) }

            this `fails requirement` "there is at least one obligation input"
        }

        // Check we can issue money only as long as the issuer institution is a command signer, i.e. any recognised
        // institution is allowed to issue as much cash as they want.
        transaction {
            output { outState }
            arg(DUMMY_PUBKEY_1) { Obligation.Commands.Issue(outState.issuanceDef) }
            this `fails requirement` "output deposits are owned by a command signer"
        }
        transaction {
            output {
                Obligation.State(
                        obligor = MINI_CORP,
                        quantity = 1000.DOLLARS.quantity,
                        beneficiary = DUMMY_PUBKEY_1,
                        template = megaCorpDollarSettlement
                )
            }
            tweak {
                arg(MINI_CORP_PUBKEY) { Obligation.Commands.Issue(Obligation.IssuanceDefinition(MINI_CORP, megaCorpDollarSettlement), 0) }
                this `fails requirement` "has a nonce"
            }
            arg(MINI_CORP_PUBKEY) { Obligation.Commands.Issue(Obligation.IssuanceDefinition(MINI_CORP, megaCorpDollarSettlement)) }
            this.accepts()
        }

        // Test generation works.
        val ptx = TransactionType.General.Builder(DUMMY_NOTARY)
        Obligation<Currency>().generateIssue(ptx, MINI_CORP, megaCorpDollarSettlement, 100.DOLLARS.quantity,
                beneficiary = DUMMY_PUBKEY_1, notary = DUMMY_NOTARY)
        assertTrue(ptx.inputStates().isEmpty())
        val expected = Obligation.State(
                obligor = MINI_CORP,
                quantity = 100.DOLLARS.quantity,
                beneficiary = DUMMY_PUBKEY_1,
                template = megaCorpDollarSettlement
        )
        assertEquals(ptx.outputStates()[0].data, expected)
        assertTrue(ptx.commands()[0].value is Obligation.Commands.Issue<*>)
        assertEquals(MINI_CORP_PUBKEY, ptx.commands()[0].signers[0])

        // We can consume $1000 in a transaction and output $2000 as long as it's signed by an issuer.
        transaction {
            input { inState }
            output { inState.copy(quantity = inState.amount.quantity * 2) }

            // Move fails: not allowed to summon money.
            tweak {
                arg(DUMMY_PUBKEY_1) { Obligation.Commands.Move(inState.issuanceDef) }
                this `fails requirement` "at obligor MegaCorp the amounts balance"
            }

            // Issue works.
            tweak {
                arg(MEGA_CORP_PUBKEY) { Obligation.Commands.Issue(inState.issuanceDef) }
                this.accepts()
            }
        }

        // Can't use an issue command to lower the amount.
        transaction {
            input { inState }
            output { inState.copy(quantity = inState.amount.quantity / 2) }
            arg(MEGA_CORP_PUBKEY) { Obligation.Commands.Issue(inState.issuanceDef) }
            this `fails requirement` "output values sum to more than the inputs"
        }

        // Can't have an issue command that doesn't actually issue money.
        transaction {
            input { inState }
            output { inState }
            arg(MEGA_CORP_PUBKEY) { Obligation.Commands.Issue(inState.issuanceDef) }
            this `fails requirement` "output values sum to more than the inputs"
        }

        // Can't have any other commands if we have an issue command (because the issue command overrules them)
        transaction {
            input { inState }
            output { inState.copy(quantity = inState.amount.quantity * 2) }
            arg(MEGA_CORP_PUBKEY) { Obligation.Commands.Issue(inState.issuanceDef) }
            tweak {
                arg(MEGA_CORP_PUBKEY) { Obligation.Commands.Issue(inState.issuanceDef) }
                this `fails requirement` "only move/exit commands can be present along with other obligation commands"
            }
            tweak {
                arg(MEGA_CORP_PUBKEY) { Obligation.Commands.Move(inState.issuanceDef) }
                this `fails requirement` "only move/exit commands can be present along with other obligation commands"
            }
            tweak {
                arg(MEGA_CORP_PUBKEY) { Obligation.Commands.SetLifecycle(inState.issuanceDef, Lifecycle.DEFAULTED) }
                this `fails requirement` "only move/exit commands can be present along with other obligation commands"
            }
            tweak {
                arg(MEGA_CORP_PUBKEY) { Obligation.Commands.Exit<Currency>(inState.issuanceDef, inState.amount / 2) }
                this `fails requirement` "only move/exit commands can be present along with other obligation commands"
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
        // Issue some obligation
        val tx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateIssue(this, MINI_CORP, megaCorpDollarSettlement, 100.DOLLARS.quantity,
                    beneficiary = MINI_CORP_PUBKEY, notary = DUMMY_NOTARY)
            signWith(MINI_CORP_KEY)
        }.toSignedTransaction()

        // Include the previously issued obligation in a new issuance command
        val ptx = TransactionType.General.Builder(DUMMY_NOTARY)
        ptx.addInputState(tx.tx.outRef<Obligation.State<Currency>>(0))
        Obligation<Currency>().generateIssue(ptx, MINI_CORP, megaCorpDollarSettlement, 100.DOLLARS.quantity,
                beneficiary = MINI_CORP_PUBKEY, notary = DUMMY_NOTARY)
    }

    /** Test generating a transaction to net two obligations of the same size, and therefore there are no outputs. */
    @Test
    fun `generate close-out net transaction`() {
        val obligationAliceToBob = oneMillionDollars.OBLIGATION `between` Pair(ALICE, BOB_PUBKEY)
        val obligationBobToAlice = oneMillionDollars.OBLIGATION `between` Pair(BOB, ALICE_PUBKEY)
        val tx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateCloseOutNetting(this, ALICE_PUBKEY, obligationAliceToBob, obligationBobToAlice)
            signWith(ALICE_KEY)
            signWith(DUMMY_NOTARY_KEY)
        }.toSignedTransaction().tx
        assertEquals(0, tx.outputs.size)
    }

    /** Test generating a transaction to net two obligations of the different sizes, and confirm the balance is correct. */
    @Test
    fun `generate close-out net transaction with remainder`() {
        val obligationAliceToBob = (2000000.DOLLARS `issued by` defaultIssuer).OBLIGATION `between` Pair(ALICE, BOB_PUBKEY)
        val obligationBobToAlice = oneMillionDollars.OBLIGATION `between` Pair(BOB, ALICE_PUBKEY)
        val tx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateCloseOutNetting(this, ALICE_PUBKEY, obligationAliceToBob, obligationBobToAlice)
            signWith(ALICE_KEY)
            signWith(DUMMY_NOTARY_KEY)
        }.toSignedTransaction().tx
        assertEquals(1, tx.outputs.size)

        val actual = tx.outputs[0].data
        assertEquals((1000000.DOLLARS `issued by` defaultIssuer).OBLIGATION `between` Pair(ALICE, BOB_PUBKEY), actual)
    }

    /** Test generating a transaction to net two obligations of the same size, and therefore there are no outputs. */
    @Test
    fun `generate payment net transaction`() {
        val obligationAliceToBob = oneMillionDollars.OBLIGATION `between` Pair(ALICE, BOB_PUBKEY)
        val obligationBobToAlice = oneMillionDollars.OBLIGATION `between` Pair(BOB, ALICE_PUBKEY)
        val tx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generatePaymentNetting(this, defaultUsd, DUMMY_NOTARY, obligationAliceToBob, obligationBobToAlice)
            signWith(ALICE_KEY)
            signWith(BOB_KEY)
            signWith(DUMMY_NOTARY_KEY)
        }.toSignedTransaction().tx
        assertEquals(0, tx.outputs.size)
    }

    /** Test generating a transaction to two obligations, where one is bigger than the other and therefore there is a remainder. */
    @Test
    fun `generate payment net transaction with remainder`() {
        val obligationAliceToBob = oneMillionDollars.OBLIGATION `between` Pair(ALICE, BOB_PUBKEY)
        val obligationBobToAlice = (2000000.DOLLARS `issued by` defaultIssuer).OBLIGATION `between` Pair(BOB, ALICE_PUBKEY)
        val tx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generatePaymentNetting(this, defaultUsd, DUMMY_NOTARY, obligationAliceToBob, obligationBobToAlice)
            signWith(ALICE_KEY)
            signWith(BOB_KEY)
        }.toSignedTransaction().tx
        assertEquals(1, tx.outputs.size)
        val expected = obligationBobToAlice.copy(quantity = obligationBobToAlice.quantity - obligationAliceToBob.quantity)
        val actual = tx.outputs[0].data
        assertEquals(expected, actual)
    }

    /** Test generating a transaction to mark outputs as having defaulted. */
    @Test
    fun `generate set lifecycle`() {
        // We don't actually verify the states, this is just here to make things look sensible
        val dueBefore = TEST_TX_TIME - Duration.ofDays(7)

        // Generate a transaction issuing the obligation
        var tx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateIssue(this, MINI_CORP, megaCorpDollarSettlement.copy(dueBefore = dueBefore), 100.DOLLARS.quantity,
                    beneficiary = MINI_CORP_PUBKEY, notary = DUMMY_NOTARY)
            signWith(MINI_CORP_KEY)
        }.toSignedTransaction()
        var stateAndRef = tx.tx.outRef<Obligation.State<Currency>>(0)

        // Now generate a transaction marking the obligation as having defaulted
        tx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateSetLifecycle(this, listOf(stateAndRef), Obligation.Lifecycle.DEFAULTED, DUMMY_NOTARY)
            signWith(MINI_CORP_KEY)
            signWith(DUMMY_NOTARY_KEY)
        }.toSignedTransaction()
        assertEquals(1, tx.tx.outputs.size)
        assertEquals(stateAndRef.state.data.copy(lifecycle = Obligation.Lifecycle.DEFAULTED), tx.tx.outputs[0].data)
        assertTrue(tx.verify().isEmpty())

        // And set it back
        stateAndRef = tx.tx.outRef<Obligation.State<Currency>>(0)
        tx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateSetLifecycle(this, listOf(stateAndRef), Obligation.Lifecycle.NORMAL, DUMMY_NOTARY)
            signWith(MINI_CORP_KEY)
            signWith(DUMMY_NOTARY_KEY)
        }.toSignedTransaction()
        assertEquals(1, tx.tx.outputs.size)
        assertEquals(stateAndRef.state.data.copy(lifecycle = Obligation.Lifecycle.NORMAL), tx.tx.outputs[0].data)
        assertTrue(tx.verify().isEmpty())
    }

    /** Test generating a transaction to settle an obligation. */
    @Test
    fun `generate settlement transaction`() {
        val cashTx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Cash().generateIssue(this, 100.DOLLARS `issued by` defaultIssuer, MINI_CORP_PUBKEY, DUMMY_NOTARY)
            signWith(MEGA_CORP_KEY)
        }.toSignedTransaction().tx

        // Generate a transaction issuing the obligation
        val obligationTx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateIssue(this, MINI_CORP, megaCorpDollarSettlement, 100.DOLLARS.quantity,
                    beneficiary = MINI_CORP_PUBKEY, notary = DUMMY_NOTARY)
            signWith(MINI_CORP_KEY)
        }.toSignedTransaction().tx

        // Now generate a transaction settling the obligation
        val settleTx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateSettle(this, listOf(obligationTx.outRef(0)), listOf(cashTx.outRef(0)), Cash.Commands.Move(), DUMMY_NOTARY)
            signWith(DUMMY_NOTARY_KEY)
            signWith(MINI_CORP_KEY)
        }.toSignedTransaction().tx
        assertEquals(2, settleTx.inputs.size)
        assertEquals(1, settleTx.outputs.size)
    }

    @Test
    fun `close-out netting`() {
        // Try netting out two obligations
        transactionGroupFor<Obligation.State<Currency>>() {
            obligationTestRoots(this)
            transaction("Issuance") {
                input("Alice's $1,000,000 obligation to Bob")
                input("Bob's $1,000,000 obligation to Alice")
                // Note we can sign with either key here
                arg(ALICE_PUBKEY) { Obligation.Commands.Net(NetType.CLOSE_OUT) }
                timestamp(TEST_TX_TIME)
            }
        }.verify()

        // Try netting out two obligations, with the third uninvolved obligation left
        // as-is
        transactionGroupFor<Obligation.State<Currency>>() {
            obligationTestRoots(this)
            transaction("Issuance") {
                input("Alice's $1,000,000 obligation to Bob")
                input("Bob's $1,000,000 obligation to Alice")
                input("MegaCorp's $1,000,000 obligation to Bob")
                output("change") { oneMillionDollars.OBLIGATION `between` Pair(MEGA_CORP, BOB_PUBKEY) }
                arg(BOB_PUBKEY, MEGA_CORP_PUBKEY) { Obligation.Commands.Net(NetType.CLOSE_OUT) }
                timestamp(TEST_TX_TIME)
            }
        }.verify()

        // Try having outputs mis-match the inputs
        transactionGroupFor<Obligation.State<Currency>>() {
            obligationTestRoots(this)
            transaction("Issuance") {
                input("Alice's $1,000,000 obligation to Bob")
                input("Bob's $1,000,000 obligation to Alice")
                output("change") { (oneMillionDollars / 2).OBLIGATION `between` Pair(ALICE, BOB_PUBKEY) }
                arg(BOB_PUBKEY) { Obligation.Commands.Net(NetType.CLOSE_OUT) }
                timestamp(TEST_TX_TIME)
            }
        }.expectFailureOfTx(1, "amounts owed on input and output must match")

        // Have the wrong signature on the transaction
        transactionGroupFor<Obligation.State<Currency>>() {
            obligationTestRoots(this)
            transaction("Issuance") {
                input("Alice's $1,000,000 obligation to Bob")
                input("Bob's $1,000,000 obligation to Alice")
                arg(MEGA_CORP_PUBKEY) { Obligation.Commands.Net(NetType.CLOSE_OUT) }
                timestamp(TEST_TX_TIME)
            }
        }.expectFailureOfTx(1, "any involved party has signed")

    }

    @Test
    fun `payment netting`() {
        // Try netting out two obligations
        transactionGroupFor<Obligation.State<Currency>>() {
            obligationTestRoots(this)
            transaction("Issuance") {
                input("Alice's $1,000,000 obligation to Bob")
                input("Bob's $1,000,000 obligation to Alice")
                arg(ALICE_PUBKEY, BOB_PUBKEY) { Obligation.Commands.Net(NetType.PAYMENT) }
                timestamp(TEST_TX_TIME)
            }
        }.verify()

        // Try netting out two obligations, but only provide one signature. Unlike close-out netting, we need both
        // signatures for payment netting
        transactionGroupFor<Obligation.State<Currency>>() {
            obligationTestRoots(this)
            transaction("Issuance") {
                input("Alice's $1,000,000 obligation to Bob")
                input("Bob's $1,000,000 obligation to Alice")
                arg(BOB_PUBKEY) { Obligation.Commands.Net(NetType.PAYMENT) }
                timestamp(TEST_TX_TIME)
            }
        }.expectFailureOfTx(1, "all involved parties have signed")

        // Multilateral netting, A -> B -> C which can net down to A -> C
        transactionGroupFor<Obligation.State<Currency>>() {
            obligationTestRoots(this)
            transaction("Issuance") {
                input("Bob's $1,000,000 obligation to Alice")
                input("MegaCorp's $1,000,000 obligation to Bob")
                output("MegaCorp's $1,000,000 obligation to Alice") { oneMillionDollars.OBLIGATION `between` Pair(MEGA_CORP, ALICE_PUBKEY) }
                arg(ALICE_PUBKEY, BOB_PUBKEY, MEGA_CORP_PUBKEY) { Obligation.Commands.Net(NetType.PAYMENT) }
                timestamp(TEST_TX_TIME)
            }
        }.verify()

        // Multilateral netting without the key of the receiving party
        transactionGroupFor<Obligation.State<Currency>>() {
            obligationTestRoots(this)
            transaction("Issuance") {
                input("Bob's $1,000,000 obligation to Alice")
                input("MegaCorp's $1,000,000 obligation to Bob")
                output("MegaCorp's $1,000,000 obligation to Alice") { oneMillionDollars.OBLIGATION `between` Pair(MEGA_CORP, ALICE_PUBKEY) }
                arg(ALICE_PUBKEY, BOB_PUBKEY) { Obligation.Commands.Net(NetType.PAYMENT) }
                timestamp(TEST_TX_TIME)
            }
        }.expectFailureOfTx(1, "all involved parties have signed")
    }

    @Test
    fun `settlement`() {
        // Try netting out two obligations
        transactionGroupFor<Obligation.State<Currency>>() {
            obligationTestRoots(this)
            transaction("Settlement") {
                input("Alice's $1,000,000 obligation to Bob")
                input("Alice's $1,000,000")
                output("Bob's $1,000,000") { 1000000.DOLLARS.CASH `issued by` defaultIssuer `owned by` BOB_PUBKEY }
                arg(ALICE_PUBKEY) { Obligation.Commands.Settle<Currency>(Obligation.IssuanceDefinition(ALICE, defaultUsd.OBLIGATION_DEF), Amount(oneMillionDollars.quantity, USD)) }
                arg(ALICE_PUBKEY) { Cash.Commands.Move(Obligation<Currency>().legalContractReference) }
            }
        }.verify()
    }

    @Test
    fun `payment default`() {
        // Try defaulting an obligation without a timestamp
        transactionGroupFor<Obligation.State<Currency>>() {
            obligationTestRoots(this)
            transaction("Settlement") {
                input("Alice's $1,000,000 obligation to Bob")
                output("Alice's defaulted $1,000,000 obligation to Bob") { (oneMillionDollars.OBLIGATION `between` Pair(ALICE, BOB_PUBKEY)).copy(lifecycle = Obligation.Lifecycle.DEFAULTED)  }
                arg(BOB_PUBKEY) { Obligation.Commands.SetLifecycle<Currency>(Obligation.IssuanceDefinition(ALICE, defaultUsd.OBLIGATION_DEF), Obligation.Lifecycle.DEFAULTED) }
            }
        }.expectFailureOfTx(1, "there is a timestamp from the authority")

        // Try defaulting an obligation due in the future
        val pastTestTime = TEST_TX_TIME - Duration.ofDays(7)
        val futureTestTime = TEST_TX_TIME + Duration.ofDays(7)
        transactionGroupFor<Obligation.State<Currency>>() {
            roots {
                transaction(oneMillionDollars.OBLIGATION `between` Pair(ALICE, BOB_PUBKEY) `at` futureTestTime `with notary` DUMMY_NOTARY label "Alice's $1,000,000 obligation to Bob")
            }
            transaction("Settlement") {
                input("Alice's $1,000,000 obligation to Bob")
                output("Alice's defaulted $1,000,000 obligation to Bob") { (oneMillionDollars.OBLIGATION `between` Pair(ALICE, BOB_PUBKEY) `at` futureTestTime).copy(lifecycle = Obligation.Lifecycle.DEFAULTED)  }
                arg(BOB_PUBKEY) { Obligation.Commands.SetLifecycle<Currency>(Obligation.IssuanceDefinition(ALICE, defaultUsd.OBLIGATION_DEF) `at` futureTestTime, Obligation.Lifecycle.DEFAULTED) }
                timestamp(TEST_TX_TIME)
            }
        }.expectFailureOfTx(1, "the due date has passed")

        // Try defaulting an obligation that is now in the past
        transactionGroupFor<Obligation.State<Currency>>() {
            roots {
                transaction(oneMillionDollars.OBLIGATION `between` Pair(ALICE, BOB_PUBKEY) `at` pastTestTime `with notary` DUMMY_NOTARY label "Alice's $1,000,000 obligation to Bob")
            }
            transaction("Settlement") {
                input("Alice's $1,000,000 obligation to Bob")
                output("Alice's defaulted $1,000,000 obligation to Bob") { (oneMillionDollars.OBLIGATION `between` Pair(ALICE, BOB_PUBKEY) `at` pastTestTime).copy(lifecycle = Obligation.Lifecycle.DEFAULTED)  }
                arg(BOB_PUBKEY) { Obligation.Commands.SetLifecycle<Currency>(Obligation.IssuanceDefinition(ALICE, defaultUsd.OBLIGATION_DEF) `at` pastTestTime, Obligation.Lifecycle.DEFAULTED) }
                timestamp(TEST_TX_TIME)
            }
        }.verify()
    }

    @Test
    fun testMergeSplit() {
        // Splitting value works.
        transaction {
            arg(DUMMY_PUBKEY_1) { Obligation.Commands.Move(inState.issuanceDef) }
            tweak {
                input { inState }
                repeat(4) { output { inState.copy(quantity = inState.quantity / 4) } }
                this.accepts()
            }
            // Merging 4 inputs into 2 outputs works.
            tweak {
                repeat(4) { input { inState.copy(quantity = inState.quantity / 4) } }
                output { inState.copy(quantity = inState.quantity / 2) }
                output { inState.copy(quantity = inState.quantity / 2) }
                this.accepts()
            }
            // Merging 2 inputs into 1 works.
            tweak {
                input { inState.copy(quantity = inState.quantity / 2) }
                input { inState.copy(quantity = inState.quantity / 2) }
                output { inState }
                this.accepts()
            }
        }
    }

    @Test
    fun zeroSizedValues() {
        transaction {
            input { inState }
            input { inState.copy(quantity = 0L) }
            this `fails requirement` "zero sized inputs"
        }
        transaction {
            input { inState }
            output { inState }
            output { inState.copy(quantity = 0L) }
            this `fails requirement` "zero sized outputs"
        }
    }
    @Test
    fun trivialMismatches() {
        // Can't change issuer.
        transaction {
            input { inState }
            output { outState `issued by` MINI_CORP }
            this `fails requirement` "at obligor MegaCorp the amounts balance"
        }
        // Can't mix currencies.
        transaction {
            input { inState }
            output { outState.copy(quantity = 80000, template = megaCorpDollarSettlement) }
            output { outState.copy(quantity = 20000, template = megaCorpPoundSettlement) }
            this `fails requirement` "the amounts balance"
        }
        transaction {
            input { inState }
            input {
                inState.copy(
                        quantity = 15000,
                        template = megaCorpPoundSettlement,
                        beneficiary = DUMMY_PUBKEY_2
                )
            }
            output { outState.copy(quantity = 115000) }
            this `fails requirement` "the amounts balance"
        }
        // Can't have superfluous input states from different issuers.
        transaction {
            input { inState }
            input { inState `issued by` MINI_CORP }
            output { outState }
            arg(DUMMY_PUBKEY_1) {Obligation.Commands.Move(inState.issuanceDef) }
            arg(DUMMY_PUBKEY_1) {Obligation.Commands.Move((inState `issued by` MINI_CORP).issuanceDef) }
            this `fails requirement` "at obligor MiniCorp the amounts balance"
        }
    }

    @Test
    fun exitLedger() {
        // Single input/output straightforward case.
        transaction {
            input { inState }
            output { outState.copy(quantity = inState.quantity - 200.DOLLARS.quantity) }

            tweak {
                arg(MEGA_CORP_PUBKEY) { Obligation.Commands.Exit<Currency>(inState.issuanceDef, 100.DOLLARS) }
                arg(DUMMY_PUBKEY_1) { Obligation.Commands.Move(inState.issuanceDef) }
                this `fails requirement` "the amounts balance"
            }

            tweak {
                arg(MEGA_CORP_PUBKEY) { Obligation.Commands.Exit<Currency>(inState.issuanceDef, 200.DOLLARS) }
                this `fails requirement` "required com.r3corda.contracts.Obligation.Commands.Move command"

                tweak {
                    arg(DUMMY_PUBKEY_1) { Obligation.Commands.Move(inState.issuanceDef) }
                    this.accepts()
                }
            }
        }
        // Multi-issuer case.
        transaction {
            input { inState }
            input { inState `issued by` MINI_CORP }

            output { inState.copy(quantity = inState.quantity - 200.DOLLARS.quantity) `issued by` MINI_CORP }
            output { inState.copy(quantity = inState.quantity - 200.DOLLARS.quantity) }

            arg(DUMMY_PUBKEY_1) { Obligation.Commands.Move(inState.issuanceDef) }

            this `fails requirement` "at obligor MegaCorp the amounts balance"

            arg(MEGA_CORP_PUBKEY) { Obligation.Commands.Exit<Currency>(inState.issuanceDef, 200.DOLLARS) }
            tweak {
                arg(MINI_CORP_PUBKEY) { Obligation.Commands.Exit<Currency>((inState `issued by` MINI_CORP).issuanceDef, 0.DOLLARS) }
                arg(DUMMY_PUBKEY_1) { Obligation.Commands.Move((inState `issued by` MINI_CORP).issuanceDef) }
                this `fails requirement` "at obligor MiniCorp the amounts balance"
            }
            arg(MINI_CORP_PUBKEY) { Obligation.Commands.Exit<Currency>((inState `issued by` MINI_CORP).issuanceDef, 200.DOLLARS) }
            arg(DUMMY_PUBKEY_1) { Obligation.Commands.Move((inState `issued by` MINI_CORP).issuanceDef) }
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
                output { inState.copy(beneficiary = DUMMY_PUBKEY_2, quantity = 200000L) }
                this `fails requirement` "at obligor MegaCorp the amounts balance"
            }
            // Missing MiniCorp deposit
            tweak {
                output { inState.copy(beneficiary = DUMMY_PUBKEY_2) }
                output { inState.copy(beneficiary = DUMMY_PUBKEY_2) }
                this `fails requirement` "at obligor MegaCorp the amounts balance"
            }

            // This works.
            output { inState.copy(beneficiary = DUMMY_PUBKEY_2) }
            output { inState.copy(beneficiary = DUMMY_PUBKEY_2) `issued by` MINI_CORP }
            arg(DUMMY_PUBKEY_1) { Obligation.Commands.Move(inState.issuanceDef) }
            arg(DUMMY_PUBKEY_1) { Obligation.Commands.Move((inState `issued by` MINI_CORP).issuanceDef) }
            this.accepts()
        }
    }

    @Test
    fun multiCurrency() {
        // Check we can do an atomic currency trade tx.
        transaction {
            val pounds = Obligation.State(Lifecycle.NORMAL, MINI_CORP, megaCorpPoundSettlement, 658.POUNDS.quantity, DUMMY_PUBKEY_2)
            input { inState `owned by` DUMMY_PUBKEY_1 }
            input { pounds }
            output { inState `owned by` DUMMY_PUBKEY_2 }
            output { pounds `owned by` DUMMY_PUBKEY_1 }
            arg(DUMMY_PUBKEY_1, DUMMY_PUBKEY_2) { Obligation.Commands.Move(inState.issuanceDef) }
            arg(DUMMY_PUBKEY_1, DUMMY_PUBKEY_2) { Obligation.Commands.Move(pounds.issuanceDef) }

            this.accepts()
        }
    }

    @Test
    fun `nettability of settlement contracts`() {
        val fiveKDollarsFromMegaToMega = Obligation.State(Lifecycle.NORMAL, MEGA_CORP, megaCorpDollarSettlement,
                5000.DOLLARS.quantity, MEGA_CORP_PUBKEY)
        val twoKDollarsFromMegaToMini = Obligation.State(Lifecycle.NORMAL, MEGA_CORP, megaCorpDollarSettlement,
                2000.DOLLARS.quantity, MINI_CORP_PUBKEY)
        val oneKDollarsFromMiniToMega = Obligation.State(Lifecycle.NORMAL, MINI_CORP, megaCorpDollarSettlement,
                1000.DOLLARS.quantity, MEGA_CORP_PUBKEY)

        // Obviously states must be nettable with themselves
        assertEquals(fiveKDollarsFromMegaToMega.bilateralNetState, fiveKDollarsFromMegaToMega.bilateralNetState)
        assertEquals(oneKDollarsFromMiniToMega.bilateralNetState, oneKDollarsFromMiniToMega.bilateralNetState)

        // States must be nettable if the two involved parties are the same, irrespective of which way around
        assertEquals(twoKDollarsFromMegaToMini.bilateralNetState, oneKDollarsFromMiniToMega.bilateralNetState)

        // States must not be nettable if they do not have the same pair of parties
        assertNotEquals(fiveKDollarsFromMegaToMega.bilateralNetState, twoKDollarsFromMegaToMini.bilateralNetState)
        assertNotEquals(fiveKDollarsFromMegaToMega.bilateralNetState, oneKDollarsFromMiniToMega.bilateralNetState)

        // States must not be nettable if the currency differs
        assertNotEquals(oneKDollarsFromMiniToMega.bilateralNetState, oneKDollarsFromMiniToMega.copy(template = megaCorpPoundSettlement).bilateralNetState)

        // States must not be nettable if the settlement time differs
        assertNotEquals(fiveKDollarsFromMegaToMega.bilateralNetState,
                fiveKDollarsFromMegaToMega.copy(template = megaCorpDollarSettlement.copy(dueBefore = sixPm)).bilateralNetState)

        // States must not be nettable if the cash contract differs
        assertNotEquals(fiveKDollarsFromMegaToMega.bilateralNetState,
                fiveKDollarsFromMegaToMega.copy(template = megaCorpDollarSettlement.copy(acceptableContracts = nonEmptySetOf(SecureHash.randomSHA256()))).bilateralNetState)

        // States must not be nettable if the trusted issuers differ
        val miniCorpIssuer = nonEmptySetOf(Issued<Currency>(MINI_CORP.ref(1), USD))
        assertNotEquals(fiveKDollarsFromMegaToMega.bilateralNetState,
                fiveKDollarsFromMegaToMega.copy(template = megaCorpDollarSettlement.copy(acceptableIssuedProducts = miniCorpIssuer)).bilateralNetState)
    }

    @Test(expected = IllegalStateException::class)
    fun `states cannot be netted if not in the normal state`() {
        inState.copy(lifecycle = Lifecycle.DEFAULTED).bilateralNetState
    }

    /**
     * Confirm that extraction of issuance definition works correctly.
     */
    @Test
    fun `extraction of issuance defintion`() {
        val fiveKDollarsFromMegaToMega = Obligation.State(Lifecycle.NORMAL, MEGA_CORP, megaCorpDollarSettlement,
                5000.DOLLARS.quantity, MEGA_CORP_PUBKEY)
        val oneKDollarsFromMiniToMega = Obligation.State(Lifecycle.NORMAL, MINI_CORP, megaCorpDollarSettlement,
                1000.DOLLARS.quantity, MEGA_CORP_PUBKEY)

        // Issuance definitions must match the input
        assertEquals(fiveKDollarsFromMegaToMega.template, megaCorpDollarSettlement)
        assertEquals(oneKDollarsFromMiniToMega.template, megaCorpDollarSettlement)
    }

    @Test
    fun `adding two settlement contracts nets them`() {
        val megaCorpDollarSettlement = Obligation.StateTemplate(trustedCashContract, megaIssuedDollars, fivePm)
        val fiveKDollarsFromMegaToMini = Obligation.State(Lifecycle.NORMAL, MEGA_CORP, megaCorpDollarSettlement,
                5000.DOLLARS.quantity, MINI_CORP_PUBKEY)
        val oneKDollarsFromMiniToMega = Obligation.State(Lifecycle.NORMAL, MINI_CORP, megaCorpDollarSettlement,
                1000.DOLLARS.quantity, MEGA_CORP_PUBKEY)

        var actual = fiveKDollarsFromMegaToMini.net(fiveKDollarsFromMegaToMini.copy(quantity = 2000.DOLLARS.quantity))
        // Both pay from mega to mini, so we add directly
        var expected = Obligation.State(Lifecycle.NORMAL, MEGA_CORP, megaCorpDollarSettlement, 7000.DOLLARS.quantity,
                MINI_CORP_PUBKEY)
        assertEquals(expected, actual)

        // Reversing the direction should mean adding the second state subtracts from the first
        actual = fiveKDollarsFromMegaToMini.net(oneKDollarsFromMiniToMega)
        expected = fiveKDollarsFromMegaToMini.copy(quantity = 4000.DOLLARS.quantity)
        assertEquals(expected, actual)

        // Trying to add an incompatible state must throw an error
        assertFailsWith(IllegalArgumentException::class) {
            fiveKDollarsFromMegaToMini.net(Obligation.State(Lifecycle.NORMAL, MINI_CORP, megaCorpDollarSettlement, 1000.DOLLARS.quantity,
                    MINI_CORP_PUBKEY))
        }
    }

    @Test
    fun `extracting amounts due between parties from a list of states`() {
        val megaCorpDollarSettlement = Obligation.StateTemplate(trustedCashContract, megaIssuedDollars, fivePm)
        val fiveKDollarsFromMegaToMini = Obligation.State(Lifecycle.NORMAL, MEGA_CORP, megaCorpDollarSettlement,
                5000.DOLLARS.quantity, MINI_CORP_PUBKEY)
        val expected = mapOf(Pair(Pair(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY), fiveKDollarsFromMegaToMini.amount))
        val actual = extractAmountsDue<Currency>(USD, listOf(fiveKDollarsFromMegaToMini))
        assertEquals(expected, actual)
    }

    @Test
    fun `netting equal balances due between parties`() {
        // Now try it with two balances, which cancel each other out
        val balanced = mapOf(
                Pair(Pair(ALICE_PUBKEY, BOB_PUBKEY), Amount(100000000, GBP)),
                Pair(Pair(BOB_PUBKEY, ALICE_PUBKEY), Amount(100000000, GBP))
        )
        val expected: Map<Pair<PublicKey, PublicKey>, Amount<Currency>> = emptyMap() // Zero balances are stripped before returning
        val actual = netAmountsDue<Currency>(balanced)
        assertEquals(expected, actual)
    }

    @Test
    fun `netting difference balances due between parties`() {
        // Now try it with two balances, which cancel each other out
        val balanced = mapOf(
                Pair(Pair(ALICE_PUBKEY, BOB_PUBKEY), Amount(100000000, GBP)),
                Pair(Pair(BOB_PUBKEY, ALICE_PUBKEY), Amount(200000000, GBP))
        )
        val expected = mapOf(
                Pair(Pair(BOB_PUBKEY, ALICE_PUBKEY), Amount(100000000, GBP))
        )
        var actual = netAmountsDue<Currency>(balanced)
        assertEquals(expected, actual)
    }

    @Test
    fun `summing empty balances due between parties`() {
        val empty = emptyMap<Pair<PublicKey, PublicKey>, Amount<Currency>>()
        val expected = emptyMap<PublicKey, Long>()
        val actual = sumAmountsDue(empty)
        assertEquals(expected, actual)
    }

    @Test
    fun `summing balances due between parties`() {
        val simple = mapOf(Pair(Pair(ALICE_PUBKEY, BOB_PUBKEY), Amount(100000000, GBP)))
        val expected = mapOf(Pair(ALICE_PUBKEY, -100000000L), Pair(BOB_PUBKEY, 100000000L))
        val actual = sumAmountsDue(simple)
        assertEquals(expected, actual)
    }

    @Test
    fun `summing balances due between parties which net to zero`() {
        // Now try it with two balances, which cancel each other out
        val balanced = mapOf(
                Pair(Pair(ALICE_PUBKEY, BOB_PUBKEY), Amount(100000000, GBP)),
                Pair(Pair(BOB_PUBKEY, ALICE_PUBKEY), Amount(100000000, GBP))
        )
        val expected: Map<PublicKey, Long> = emptyMap() // Zero balances are stripped before returning
        val actual = sumAmountsDue(balanced)
        assertEquals(expected, actual)
    }
}

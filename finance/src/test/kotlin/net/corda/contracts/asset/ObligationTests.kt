package net.corda.contracts.asset

import net.corda.contracts.asset.Obligation.Lifecycle
import net.corda.core.contracts.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.NullCompositeKey
import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.utilities.*
import net.corda.testing.*
import org.junit.Test
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ObligationTests {
    val defaultRef = OpaqueBytes(ByteArray(1, { 1 }))
    val defaultIssuer = MEGA_CORP.ref(defaultRef)
    val oneMillionDollars = 1000000.DOLLARS `issued by` defaultIssuer
    val trustedCashContract = nonEmptySetOf(SecureHash.Companion.randomSHA256() as SecureHash)
    val megaIssuedDollars = nonEmptySetOf(Issued(defaultIssuer, USD))
    val megaIssuedPounds = nonEmptySetOf(Issued(defaultIssuer, GBP))
    val fivePm = TEST_TX_TIME.truncatedTo(ChronoUnit.DAYS).plus(17, ChronoUnit.HOURS)
    val sixPm = fivePm.plus(1, ChronoUnit.HOURS)
    val megaCorpDollarSettlement = Obligation.Terms(trustedCashContract, megaIssuedDollars, fivePm)
    val megaCorpPoundSettlement = megaCorpDollarSettlement.copy(acceptableIssuedProducts = megaIssuedPounds)
    val inState = Obligation.State(
            lifecycle = Lifecycle.NORMAL,
            obligor = MEGA_CORP,
            template = megaCorpDollarSettlement,
            quantity = 1000.DOLLARS.quantity,
            beneficiary = DUMMY_PUBKEY_1
    )
    val outState = inState.copy(beneficiary = DUMMY_PUBKEY_2)

    private fun cashObligationTestRoots(
            group: LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>
    ) = group.apply {
        unverifiedTransaction {
            output("Alice's $1,000,000 obligation to Bob", oneMillionDollars.OBLIGATION between Pair(ALICE, BOB_PUBKEY))
            output("Bob's $1,000,000 obligation to Alice", oneMillionDollars.OBLIGATION between Pair(BOB, ALICE_PUBKEY))
            output("MegaCorp's $1,000,000 obligation to Bob", oneMillionDollars.OBLIGATION between Pair(MEGA_CORP, BOB_PUBKEY))
            output("Alice's $1,000,000", 1000000.DOLLARS.CASH `issued by` defaultIssuer `owned by` ALICE_PUBKEY)
        }
    }

    @Test
    fun trivial() {
        transaction {
            input { inState }
            this `fails with` "the amounts balance"

            tweak {
                output { outState.copy(quantity = 2000.DOLLARS.quantity) }
                this `fails with` "the amounts balance"
            }
            tweak {
                output { outState }
                // No command arguments
                this `fails with` "required net.corda.core.contracts.FungibleAsset.Commands.Move command"
            }
            tweak {
                output { outState }
                command(DUMMY_PUBKEY_2) { Obligation.Commands.Move() }
                this `fails with` "the owning keys are a subset of the signing keys"
            }
            tweak {
                output { outState }
                output { outState `issued by` MINI_CORP }
                command(DUMMY_PUBKEY_1) { Obligation.Commands.Move() }
                this `fails with` "at least one asset input"
            }
            // Simple reallocation works.
            tweak {
                output { outState }
                command(DUMMY_PUBKEY_1) { Obligation.Commands.Move() }
                this.verifies()
            }
        }
    }

    @Test
    fun `issue debt`() {
        // Check we can't "move" debt into existence.
        transaction {
            input { DummyState() }
            output { outState }
            command(MINI_CORP_PUBKEY) { Obligation.Commands.Move() }

            this `fails with` "there is at least one asset input"
        }

        // Check we can issue money only as long as the issuer institution is a command signer, i.e. any recognised
        // institution is allowed to issue as much cash as they want.
        transaction {
            output { outState }
            command(DUMMY_PUBKEY_1) { Obligation.Commands.Issue() }
            this `fails with` "output states are issued by a command signer"
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
                command(MINI_CORP_PUBKEY) { Obligation.Commands.Issue(0) }
                this `fails with` "has a nonce"
            }
            command(MINI_CORP_PUBKEY) { Obligation.Commands.Issue() }
            this.verifies()
        }

        // Test generation works.
        val tx = TransactionType.General.Builder(notary = null).apply {
            Obligation<Currency>().generateIssue(this, MINI_CORP, megaCorpDollarSettlement, 100.DOLLARS.quantity,
                    beneficiary = DUMMY_PUBKEY_1, notary = DUMMY_NOTARY)
            signWith(MINI_CORP_KEY)
        }.toSignedTransaction().tx
        assertTrue(tx.inputs.isEmpty())
        val expected = Obligation.State(
                obligor = MINI_CORP,
                quantity = 100.DOLLARS.quantity,
                beneficiary = DUMMY_PUBKEY_1,
                template = megaCorpDollarSettlement
        )
        assertEquals(tx.outputs[0].data, expected)
        assertTrue(tx.commands[0].value is Obligation.Commands.Issue)
        assertEquals(MINI_CORP_PUBKEY, tx.commands[0].signers[0])

        // We can consume $1000 in a transaction and output $2000 as long as it's signed by an issuer.
        transaction {
            input { inState }
            output { inState.copy(quantity = inState.amount.quantity * 2) }

            // Move fails: not allowed to summon money.
            tweak {
                command(DUMMY_PUBKEY_1) { Obligation.Commands.Move() }
                this `fails with` "the amounts balance"
            }

            // Issue works.
            tweak {
                command(MEGA_CORP_PUBKEY) { Obligation.Commands.Issue() }
                this.verifies()
            }
        }

        // Can't use an issue command to lower the amount.
        transaction {
            input { inState }
            output { inState.copy(quantity = inState.amount.quantity / 2) }
            command(MEGA_CORP_PUBKEY) { Obligation.Commands.Issue() }
            this `fails with` "output values sum to more than the inputs"
        }

        // Can't have an issue command that doesn't actually issue money.
        transaction {
            input { inState }
            output { inState }
            command(MEGA_CORP_PUBKEY) { Obligation.Commands.Issue() }
            this `fails with` ""
        }

        // Can't have any other commands if we have an issue command (because the issue command overrules them)
        transaction {
            input { inState }
            output { inState.copy(quantity = inState.amount.quantity * 2) }
            command(MEGA_CORP_PUBKEY) { Obligation.Commands.Issue() }
            tweak {
                command(MEGA_CORP_PUBKEY) { Obligation.Commands.Issue() }
                this `fails with` "List has more than one element."
            }
            tweak {
                command(MEGA_CORP_PUBKEY) { Obligation.Commands.Move() }
                this `fails with` "The following commands were not matched at the end of execution"
            }
            tweak {
                command(MEGA_CORP_PUBKEY) { Obligation.Commands.Exit(inState.amount / 2) }
                this `fails with` "The following commands were not matched at the end of execution"
            }
            this.verifies()
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
        val obligationAliceToBob = oneMillionDollars.OBLIGATION between Pair(ALICE, BOB_PUBKEY)
        val obligationBobToAlice = oneMillionDollars.OBLIGATION between Pair(BOB, ALICE_PUBKEY)
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
        val obligationAliceToBob = (2000000.DOLLARS `issued by` defaultIssuer).OBLIGATION between Pair(ALICE, BOB_PUBKEY)
        val obligationBobToAlice = oneMillionDollars.OBLIGATION between Pair(BOB, ALICE_PUBKEY)
        val tx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateCloseOutNetting(this, ALICE_PUBKEY, obligationAliceToBob, obligationBobToAlice)
            signWith(ALICE_KEY)
            signWith(DUMMY_NOTARY_KEY)
        }.toSignedTransaction().tx
        assertEquals(1, tx.outputs.size)

        val actual = tx.outputs[0].data
        assertEquals((1000000.DOLLARS `issued by` defaultIssuer).OBLIGATION between Pair(ALICE, BOB_PUBKEY), actual)
    }

    /** Test generating a transaction to net two obligations of the same size, and therefore there are no outputs. */
    @Test
    fun `generate payment net transaction`() {
        val obligationAliceToBob = oneMillionDollars.OBLIGATION between Pair(ALICE, BOB_PUBKEY)
        val obligationBobToAlice = oneMillionDollars.OBLIGATION between Pair(BOB, ALICE_PUBKEY)
        val tx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generatePaymentNetting(this, obligationAliceToBob.amount.token, DUMMY_NOTARY, obligationAliceToBob, obligationBobToAlice)
            signWith(ALICE_KEY)
            signWith(BOB_KEY)
            signWith(DUMMY_NOTARY_KEY)
        }.toSignedTransaction().tx
        assertEquals(0, tx.outputs.size)
    }

    /** Test generating a transaction to two obligations, where one is bigger than the other and therefore there is a remainder. */
    @Test
    fun `generate payment net transaction with remainder`() {
        val obligationAliceToBob = oneMillionDollars.OBLIGATION between Pair(ALICE, BOB_PUBKEY)
        val obligationBobToAlice = (2000000.DOLLARS `issued by` defaultIssuer).OBLIGATION between Pair(BOB, ALICE_PUBKEY)
        val tx = TransactionType.General.Builder(null).apply {
            Obligation<Currency>().generatePaymentNetting(this, obligationAliceToBob.amount.token, DUMMY_NOTARY, obligationAliceToBob, obligationBobToAlice)
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
        var tx = TransactionType.General.Builder(null).apply {
            Obligation<Currency>().generateIssue(this, MINI_CORP, megaCorpDollarSettlement.copy(dueBefore = dueBefore), 100.DOLLARS.quantity,
                    beneficiary = MINI_CORP_PUBKEY, notary = DUMMY_NOTARY)
            signWith(MINI_CORP_KEY)
        }.toSignedTransaction()
        var stateAndRef = tx.tx.outRef<Obligation.State<Currency>>(0)

        // Now generate a transaction marking the obligation as having defaulted
        tx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateSetLifecycle(this, listOf(stateAndRef), Lifecycle.DEFAULTED, DUMMY_NOTARY)
            signWith(MINI_CORP_KEY)
            signWith(DUMMY_NOTARY_KEY)
        }.toSignedTransaction()
        assertEquals(1, tx.tx.outputs.size)
        assertEquals(stateAndRef.state.data.copy(lifecycle = Lifecycle.DEFAULTED), tx.tx.outputs[0].data)
        tx.verifySignatures()

        // And set it back
        stateAndRef = tx.tx.outRef<Obligation.State<Currency>>(0)
        tx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateSetLifecycle(this, listOf(stateAndRef), Lifecycle.NORMAL, DUMMY_NOTARY)
            signWith(MINI_CORP_KEY)
            signWith(DUMMY_NOTARY_KEY)
        }.toSignedTransaction()
        assertEquals(1, tx.tx.outputs.size)
        assertEquals(stateAndRef.state.data.copy(lifecycle = Lifecycle.NORMAL), tx.tx.outputs[0].data)
        tx.verifySignatures()
    }

    /** Test generating a transaction to settle an obligation. */
    @Test
    fun `generate settlement transaction`() {
        val cashTx = TransactionType.General.Builder(null).apply {
            Cash().generateIssue(this, 100.DOLLARS `issued by` defaultIssuer, MINI_CORP_PUBKEY, DUMMY_NOTARY)
            signWith(MEGA_CORP_KEY)
        }.toSignedTransaction().tx

        // Generate a transaction issuing the obligation
        val obligationTx = TransactionType.General.Builder(null).apply {
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
        ledger {
            cashObligationTestRoots(this)
            transaction("Issuance") {
                input("Alice's $1,000,000 obligation to Bob")
                input("Bob's $1,000,000 obligation to Alice")
                // Note we can sign with either key here
                command(ALICE_PUBKEY) { Obligation.Commands.Net(NetType.CLOSE_OUT) }
                timestamp(TEST_TX_TIME)
                this.verifies()
            }
            this.verifies()
        }

        // Try netting out two obligations, with the third uninvolved obligation left
        // as-is
        ledger {
            cashObligationTestRoots(this)
            transaction("Issuance") {
                input("Alice's $1,000,000 obligation to Bob")
                input("Bob's $1,000,000 obligation to Alice")
                input("MegaCorp's $1,000,000 obligation to Bob")
                output("change") { oneMillionDollars.OBLIGATION between Pair(MEGA_CORP, BOB_PUBKEY) }
                command(BOB_PUBKEY, MEGA_CORP_PUBKEY) { Obligation.Commands.Net(NetType.CLOSE_OUT) }
                timestamp(TEST_TX_TIME)
                this.verifies()
            }
            this.verifies()
        }

        // Try having outputs mis-match the inputs
        ledger {
            cashObligationTestRoots(this)
            transaction("Issuance") {
                input("Alice's $1,000,000 obligation to Bob")
                input("Bob's $1,000,000 obligation to Alice")
                output("change") { (oneMillionDollars / 2).OBLIGATION between Pair(ALICE, BOB_PUBKEY) }
                command(BOB_PUBKEY) { Obligation.Commands.Net(NetType.CLOSE_OUT) }
                timestamp(TEST_TX_TIME)
                this `fails with` "amounts owed on input and output must match"
            }
        }

        // Have the wrong signature on the transaction
        ledger {
            cashObligationTestRoots(this)
            transaction("Issuance") {
                input("Alice's $1,000,000 obligation to Bob")
                input("Bob's $1,000,000 obligation to Alice")
                command(MEGA_CORP_PUBKEY) { Obligation.Commands.Net(NetType.CLOSE_OUT) }
                timestamp(TEST_TX_TIME)
                this `fails with` "any involved party has signed"
            }
        }
    }

    @Test
    fun `payment netting`() {
        // Try netting out two obligations
        ledger {
            cashObligationTestRoots(this)
            transaction("Issuance") {
                input("Alice's $1,000,000 obligation to Bob")
                input("Bob's $1,000,000 obligation to Alice")
                command(ALICE_PUBKEY, BOB_PUBKEY) { Obligation.Commands.Net(NetType.PAYMENT) }
                timestamp(TEST_TX_TIME)
                this.verifies()
            }
            this.verifies()
        }

        // Try netting out two obligations, but only provide one signature. Unlike close-out netting, we need both
        // signatures for payment netting
        ledger {
            cashObligationTestRoots(this)
            transaction("Issuance") {
                input("Alice's $1,000,000 obligation to Bob")
                input("Bob's $1,000,000 obligation to Alice")
                command(BOB_PUBKEY) { Obligation.Commands.Net(NetType.PAYMENT) }
                timestamp(TEST_TX_TIME)
                this `fails with` "all involved parties have signed"
            }
        }

        // Multilateral netting, A -> B -> C which can net down to A -> C
        ledger {
            cashObligationTestRoots(this)
            transaction("Issuance") {
                input("Bob's $1,000,000 obligation to Alice")
                input("MegaCorp's $1,000,000 obligation to Bob")
                output("MegaCorp's $1,000,000 obligation to Alice") { oneMillionDollars.OBLIGATION between Pair(MEGA_CORP, ALICE_PUBKEY) }
                command(ALICE_PUBKEY, BOB_PUBKEY, MEGA_CORP_PUBKEY) { Obligation.Commands.Net(NetType.PAYMENT) }
                timestamp(TEST_TX_TIME)
                this.verifies()
            }
            this.verifies()
        }

        // Multilateral netting without the key of the receiving party
        ledger {
            cashObligationTestRoots(this)
            transaction("Issuance") {
                input("Bob's $1,000,000 obligation to Alice")
                input("MegaCorp's $1,000,000 obligation to Bob")
                output("MegaCorp's $1,000,000 obligation to Alice") { oneMillionDollars.OBLIGATION between Pair(MEGA_CORP, ALICE_PUBKEY) }
                command(ALICE_PUBKEY, BOB_PUBKEY) { Obligation.Commands.Net(NetType.PAYMENT) }
                timestamp(TEST_TX_TIME)
                this `fails with` "all involved parties have signed"
            }
        }
    }

    @Test
    fun `cash settlement`() {
        // Try settling an obligation
        ledger {
            cashObligationTestRoots(this)
            transaction("Settlement") {
                input("Alice's $1,000,000 obligation to Bob")
                input("Alice's $1,000,000")
                output("Bob's $1,000,000") { 1000000.DOLLARS.CASH `issued by` defaultIssuer `owned by` BOB_PUBKEY }
                command(ALICE_PUBKEY) { Obligation.Commands.Settle(Amount(oneMillionDollars.quantity, inState.amount.token)) }
                command(ALICE_PUBKEY) { Cash.Commands.Move(Obligation<Currency>().legalContractReference) }
                this.verifies()
            }
        }

        // Try partial settling of an obligation
        val halfAMillionDollars = 500000.DOLLARS `issued by` defaultIssuer
        ledger {
            transaction("Settlement") {
                input(oneMillionDollars.OBLIGATION between Pair(ALICE, BOB_PUBKEY))
                input(500000.DOLLARS.CASH `issued by` defaultIssuer `owned by` ALICE_PUBKEY)
                output("Alice's $500,000 obligation to Bob") { halfAMillionDollars.OBLIGATION between Pair(ALICE, BOB_PUBKEY) }
                output("Bob's $500,000") { 500000.DOLLARS.CASH `issued by` defaultIssuer `owned by` BOB_PUBKEY }
                command(ALICE_PUBKEY) { Obligation.Commands.Settle(Amount(oneMillionDollars.quantity / 2, inState.amount.token)) }
                command(ALICE_PUBKEY) { Cash.Commands.Move(Obligation<Currency>().legalContractReference) }
                this.verifies()
            }
        }

        // Make sure we can't settle an obligation that's defaulted
        val defaultedObligation: Obligation.State<Currency> = (oneMillionDollars.OBLIGATION between Pair(ALICE, BOB_PUBKEY)).copy(lifecycle = Lifecycle.DEFAULTED)
        ledger {
            transaction("Settlement") {
                input(defaultedObligation) // Alice's defaulted $1,000,000 obligation to Bob
                input(1000000.DOLLARS.CASH `issued by` defaultIssuer `owned by` ALICE_PUBKEY)
                output("Bob's $1,000,000") { 1000000.DOLLARS.CASH `issued by` defaultIssuer `owned by` BOB_PUBKEY }
                command(ALICE_PUBKEY) { Obligation.Commands.Settle(Amount(oneMillionDollars.quantity, inState.amount.token)) }
                command(ALICE_PUBKEY) { Cash.Commands.Move(Obligation<Currency>().legalContractReference) }
                this `fails with` "all inputs are in the normal state"
            }
        }

        // Make sure settlement amount must match the amount leaving the ledger
        ledger {
            cashObligationTestRoots(this)
            transaction("Settlement") {
                input("Alice's $1,000,000 obligation to Bob")
                input("Alice's $1,000,000")
                output("Bob's $1,000,000") { 1000000.DOLLARS.CASH `issued by` defaultIssuer `owned by` BOB_PUBKEY }
                command(ALICE_PUBKEY) { Obligation.Commands.Settle(Amount(oneMillionDollars.quantity / 2, inState.amount.token)) }
                command(ALICE_PUBKEY) { Cash.Commands.Move(Obligation<Currency>().legalContractReference) }
                this `fails with` "amount in settle command"
            }
        }
    }

    @Test
    fun `commodity settlement`() {
        val defaultFcoj = FCOJ `issued by` defaultIssuer
        val oneUnitFcoj = Amount(1, defaultFcoj)
        val obligationDef = Obligation.Terms(nonEmptySetOf(CommodityContract().legalContractReference), nonEmptySetOf(defaultFcoj), TEST_TX_TIME)
        val oneUnitFcojObligation = Obligation.State(Obligation.Lifecycle.NORMAL, ALICE,
                obligationDef, oneUnitFcoj.quantity, NullCompositeKey)
        // Try settling a simple commodity obligation
        ledger {
            unverifiedTransaction {
                output("Alice's 1 FCOJ obligation to Bob", oneUnitFcojObligation between Pair(ALICE, BOB_PUBKEY))
                output("Alice's 1 FCOJ", CommodityContract.State(oneUnitFcoj, ALICE_PUBKEY))
            }
            transaction("Settlement") {
                input("Alice's 1 FCOJ obligation to Bob")
                input("Alice's 1 FCOJ")
                output("Bob's 1 FCOJ") { CommodityContract.State(oneUnitFcoj, BOB_PUBKEY) }
                command(ALICE_PUBKEY) { Obligation.Commands.Settle(Amount(oneUnitFcoj.quantity, oneUnitFcojObligation.amount.token)) }
                command(ALICE_PUBKEY) { CommodityContract.Commands.Move(Obligation<Commodity>().legalContractReference) }
                verifies()
            }
        }
    }

    @Test
    fun `payment default`() {
        // Try defaulting an obligation without a timestamp
        ledger {
            cashObligationTestRoots(this)
            transaction("Settlement") {
                input("Alice's $1,000,000 obligation to Bob")
                output("Alice's defaulted $1,000,000 obligation to Bob") { (oneMillionDollars.OBLIGATION between Pair(ALICE, BOB_PUBKEY)).copy(lifecycle = Lifecycle.DEFAULTED) }
                command(BOB_PUBKEY) { Obligation.Commands.SetLifecycle(Lifecycle.DEFAULTED) }
                this `fails with` "there is a timestamp from the authority"
            }
        }

        // Try defaulting an obligation due in the future
        val pastTestTime = TEST_TX_TIME - Duration.ofDays(7)
        val futureTestTime = TEST_TX_TIME + Duration.ofDays(7)
        transaction("Settlement") {
            input(oneMillionDollars.OBLIGATION between Pair(ALICE, BOB_PUBKEY) `at` futureTestTime)
            output("Alice's defaulted $1,000,000 obligation to Bob") { (oneMillionDollars.OBLIGATION between Pair(ALICE, BOB_PUBKEY) `at` futureTestTime).copy(lifecycle = Lifecycle.DEFAULTED) }
            command(BOB_PUBKEY) { Obligation.Commands.SetLifecycle(Lifecycle.DEFAULTED) }
            timestamp(TEST_TX_TIME)
            this `fails with` "the due date has passed"
        }

        // Try defaulting an obligation that is now in the past
        ledger {
            transaction("Settlement") {
                input(oneMillionDollars.OBLIGATION between Pair(ALICE, BOB_PUBKEY) `at` pastTestTime)
                output("Alice's defaulted $1,000,000 obligation to Bob") { (oneMillionDollars.OBLIGATION between Pair(ALICE, BOB_PUBKEY) `at` pastTestTime).copy(lifecycle = Lifecycle.DEFAULTED) }
                command(BOB_PUBKEY) { Obligation.Commands.SetLifecycle(Lifecycle.DEFAULTED) }
                timestamp(TEST_TX_TIME)
                this.verifies()
            }
            this.verifies()
        }
    }

    @Test
    fun testMergeSplit() {
        // Splitting value works.
        transaction {
            command(DUMMY_PUBKEY_1) { Obligation.Commands.Move() }
            tweak {
                input { inState }
                repeat(4) { output { inState.copy(quantity = inState.quantity / 4) } }
                this.verifies()
            }
            // Merging 4 inputs into 2 outputs works.
            tweak {
                repeat(4) { input { inState.copy(quantity = inState.quantity / 4) } }
                output { inState.copy(quantity = inState.quantity / 2) }
                output { inState.copy(quantity = inState.quantity / 2) }
                this.verifies()
            }
            // Merging 2 inputs into 1 works.
            tweak {
                input { inState.copy(quantity = inState.quantity / 2) }
                input { inState.copy(quantity = inState.quantity / 2) }
                output { inState }
                this.verifies()
            }
        }
    }

    @Test
    fun zeroSizedValues() {
        transaction {
            input { inState }
            input { inState.copy(quantity = 0L) }
            this `fails with` "zero sized inputs"
        }
        transaction {
            input { inState }
            output { inState }
            output { inState.copy(quantity = 0L) }
            this `fails with` "zero sized outputs"
        }
    }

    @Test
    fun trivialMismatches() {
        // Can't change issuer.
        transaction {
            input { inState }
            output { outState `issued by` MINI_CORP }
            this `fails with` "the amounts balance"
        }
        // Can't mix currencies.
        transaction {
            input { inState }
            output { outState.copy(quantity = 80000, template = megaCorpDollarSettlement) }
            output { outState.copy(quantity = 20000, template = megaCorpPoundSettlement) }
            this `fails with` "the amounts balance"
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
            this `fails with` "the amounts balance"
        }
        // Can't have superfluous input states from different issuers.
        transaction {
            input { inState }
            input { inState `issued by` MINI_CORP }
            output { outState }
            command(DUMMY_PUBKEY_1) { Obligation.Commands.Move() }
            this `fails with` "the amounts balance"
        }
    }

    @Test
    fun `exit single product obligation`() {
        // Single input/output straightforward case.
        transaction {
            input { inState }
            output { outState.copy(quantity = inState.quantity - 200.DOLLARS.quantity) }

            tweak {
                command(DUMMY_PUBKEY_1) { Obligation.Commands.Exit(Amount(100.DOLLARS.quantity, inState.amount.token)) }
                command(DUMMY_PUBKEY_1) { Obligation.Commands.Move() }
                this `fails with` "the amounts balance"
            }

            tweak {
                command(DUMMY_PUBKEY_1) { Obligation.Commands.Exit(Amount(200.DOLLARS.quantity, inState.amount.token)) }
                this `fails with` "required net.corda.core.contracts.FungibleAsset.Commands.Move command"

                tweak {
                    command(DUMMY_PUBKEY_1) { Obligation.Commands.Move() }
                    this.verifies()
                }
            }
        }

    }

    @Test
    fun `exit multiple product obligations`() {
        // Multi-product case.
        transaction {
            input { inState.copy(template = inState.template.copy(acceptableIssuedProducts = megaIssuedPounds)) }
            input { inState.copy(template = inState.template.copy(acceptableIssuedProducts = megaIssuedDollars)) }

            output { inState.copy(template = inState.template.copy(acceptableIssuedProducts = megaIssuedPounds), quantity = inState.quantity - 200.POUNDS.quantity) }
            output { inState.copy(template = inState.template.copy(acceptableIssuedProducts = megaIssuedDollars), quantity = inState.quantity - 200.DOLLARS.quantity) }

            command(DUMMY_PUBKEY_1) { Obligation.Commands.Move() }

            this `fails with` "the amounts balance"

            command(DUMMY_PUBKEY_1) { Obligation.Commands.Exit(Amount(200.DOLLARS.quantity, inState.amount.token.copy(product = megaCorpDollarSettlement))) }
            this `fails with` "the amounts balance"

            command(DUMMY_PUBKEY_1) { Obligation.Commands.Exit(Amount(200.POUNDS.quantity, inState.amount.token.copy(product = megaCorpPoundSettlement))) }
            this.verifies()
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
                this `fails with` "the amounts balance"
            }
            // Missing MiniCorp deposit
            tweak {
                output { inState.copy(beneficiary = DUMMY_PUBKEY_2) }
                output { inState.copy(beneficiary = DUMMY_PUBKEY_2) }
                this `fails with` "the amounts balance"
            }

            // This works.
            output { inState.copy(beneficiary = DUMMY_PUBKEY_2) }
            output { inState.copy(beneficiary = DUMMY_PUBKEY_2) `issued by` MINI_CORP }
            command(DUMMY_PUBKEY_1) { Obligation.Commands.Move() }
            this.verifies()
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
            command(DUMMY_PUBKEY_1, DUMMY_PUBKEY_2) { Obligation.Commands.Move() }

            this.verifies()
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
                fiveKDollarsFromMegaToMega.copy(template = megaCorpDollarSettlement.copy(acceptableContracts = nonEmptySetOf(SecureHash.Companion.randomSHA256()))).bilateralNetState)

        // States must not be nettable if the trusted issuers differ
        val miniCorpIssuer = nonEmptySetOf(Issued(MINI_CORP.ref(1), USD))
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
        val megaCorpDollarSettlement = Obligation.Terms(trustedCashContract, megaIssuedDollars, fivePm)
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
        val megaCorpDollarSettlement = Obligation.Terms(trustedCashContract, megaIssuedDollars, fivePm)
        val fiveKDollarsFromMegaToMini = Obligation.State(Lifecycle.NORMAL, MEGA_CORP, megaCorpDollarSettlement,
                5000.DOLLARS.quantity, MINI_CORP_PUBKEY)
        val amount = fiveKDollarsFromMegaToMini.amount
        val expected = mapOf(Pair(Pair(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY), Amount(amount.quantity, amount.token.product)))
        val actual = extractAmountsDue(megaCorpDollarSettlement, listOf(fiveKDollarsFromMegaToMini))
        assertEquals(expected, actual)
    }

    @Test
    fun `netting equal balances due between parties`() {
        // Now try it with two balances, which cancel each other out
        val balanced = mapOf(
                Pair(Pair(ALICE_PUBKEY, BOB_PUBKEY), Amount(100000000, GBP)),
                Pair(Pair(BOB_PUBKEY, ALICE_PUBKEY), Amount(100000000, GBP))
        )
        val expected: Map<Pair<CompositeKey, CompositeKey>, Amount<Currency>> = emptyMap() // Zero balances are stripped before returning
        val actual = netAmountsDue(balanced)
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
        val actual = netAmountsDue(balanced)
        assertEquals(expected, actual)
    }

    @Test
    fun `summing empty balances due between parties`() {
        val empty = emptyMap<Pair<CompositeKey, CompositeKey>, Amount<Currency>>()
        val expected = emptyMap<CompositeKey, Long>()
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
        val expected: Map<CompositeKey, Long> = emptyMap() // Zero balances are stripped before returning
        val actual = sumAmountsDue(balanced)
        assertEquals(expected, actual)
    }
}

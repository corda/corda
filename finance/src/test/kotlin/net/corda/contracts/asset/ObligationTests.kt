package net.corda.contracts.asset

import net.corda.contracts.Commodity
import net.corda.contracts.NetType
import net.corda.contracts.asset.Obligation.Lifecycle
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.testing.NULL_PARTY
import net.corda.core.hours
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.OpaqueBytes
import net.corda.testing.*
import net.corda.testing.contracts.DummyState
import net.corda.testing.node.MockServices
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ObligationTests {
    val defaultRef = OpaqueBytes.of(1)
    val defaultIssuer = MEGA_CORP.ref(defaultRef)
    val oneMillionDollars = 1000000.DOLLARS `issued by` defaultIssuer
    val trustedCashContract = NonEmptySet.of(SecureHash.randomSHA256() as SecureHash)
    val megaIssuedDollars = NonEmptySet.of(Issued(defaultIssuer, USD))
    val megaIssuedPounds = NonEmptySet.of(Issued(defaultIssuer, GBP))
    val fivePm: Instant = TEST_TX_TIME.truncatedTo(ChronoUnit.DAYS) + 17.hours
    val sixPm: Instant = fivePm + 1.hours
    val megaCorpDollarSettlement = Obligation.Terms(trustedCashContract, megaIssuedDollars, fivePm)
    val megaCorpPoundSettlement = megaCorpDollarSettlement.copy(acceptableIssuedProducts = megaIssuedPounds)
    val inState = Obligation.State(
            lifecycle = Lifecycle.NORMAL,
            obligor = MEGA_CORP,
            template = megaCorpDollarSettlement,
            quantity = 1000.DOLLARS.quantity,
            beneficiary = CHARLIE
    )
    val outState = inState.copy(beneficiary = AnonymousParty(DUMMY_PUBKEY_2))
    val miniCorpServices = MockServices(MINI_CORP_KEY)
    val notaryServices = MockServices(DUMMY_NOTARY_KEY)

    private fun cashObligationTestRoots(
            group: LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>
    ) = group.apply {
        unverifiedTransaction {
            output("Alice's $1,000,000 obligation to Bob", oneMillionDollars.OBLIGATION between Pair(ALICE, BOB))
            output("Bob's $1,000,000 obligation to Alice", oneMillionDollars.OBLIGATION between Pair(BOB, ALICE))
            output("MegaCorp's $1,000,000 obligation to Bob", oneMillionDollars.OBLIGATION between Pair(MEGA_CORP, BOB))
            output("Alice's $1,000,000", 1000000.DOLLARS.CASH `issued by` defaultIssuer `owned by` ALICE)
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
                command(CHARLIE.owningKey) { Obligation.Commands.Move() }
                this `fails with` "at least one asset input"
            }
            // Simple reallocation works.
            tweak {
                output { outState }
                command(CHARLIE.owningKey) { Obligation.Commands.Move() }
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
            command(CHARLIE.owningKey) { Obligation.Commands.Issue() }
            this `fails with` "output states are issued by a command signer"
        }
        transaction {
            output {
                Obligation.State(
                        obligor = MINI_CORP,
                        quantity = 1000.DOLLARS.quantity,
                        beneficiary = CHARLIE,
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
                    beneficiary = CHARLIE, notary = DUMMY_NOTARY)
        }.toWireTransaction()
        assertTrue(tx.inputs.isEmpty())
        val expected = Obligation.State(
                obligor = MINI_CORP,
                quantity = 100.DOLLARS.quantity,
                beneficiary = CHARLIE,
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
                command(CHARLIE.owningKey) { Obligation.Commands.Move() }
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
                command(MEGA_CORP_PUBKEY) { Obligation.Commands.Exit(inState.amount.splitEvenly(2).first()) }
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
                    beneficiary = MINI_CORP, notary = DUMMY_NOTARY)
        }.toWireTransaction()


        // Include the previously issued obligation in a new issuance command
        val ptx = TransactionType.General.Builder(DUMMY_NOTARY)
        ptx.addInputState(tx.outRef<Obligation.State<Currency>>(0))
        Obligation<Currency>().generateIssue(ptx, MINI_CORP, megaCorpDollarSettlement, 100.DOLLARS.quantity,
                beneficiary = MINI_CORP, notary = DUMMY_NOTARY)
    }

    /** Test generating a transaction to net two obligations of the same size, and therefore there are no outputs. */
    @Test
    fun `generate close-out net transaction`() {
        val obligationAliceToBob = oneMillionDollars.OBLIGATION between Pair(ALICE, BOB)
        val obligationBobToAlice = oneMillionDollars.OBLIGATION between Pair(BOB, ALICE)
        val tx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateCloseOutNetting(this, ALICE, obligationAliceToBob, obligationBobToAlice)
        }.toWireTransaction()
        assertEquals(0, tx.outputs.size)
    }

    /** Test generating a transaction to net two obligations of the different sizes, and confirm the balance is correct. */
    @Test
    fun `generate close-out net transaction with remainder`() {
        val obligationAliceToBob = (2000000.DOLLARS `issued by` defaultIssuer).OBLIGATION between Pair(ALICE, BOB)
        val obligationBobToAlice = oneMillionDollars.OBLIGATION between Pair(BOB, ALICE)
        val tx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateCloseOutNetting(this, ALICE, obligationAliceToBob, obligationBobToAlice)
        }.toWireTransaction()
        assertEquals(1, tx.outputs.size)

        val actual = tx.outputs[0].data
        assertEquals((1000000.DOLLARS `issued by` defaultIssuer).OBLIGATION between Pair(ALICE, BOB), actual)
    }

    /** Test generating a transaction to net two obligations of the same size, and therefore there are no outputs. */
    @Test
    fun `generate payment net transaction`() {
        val obligationAliceToBob = oneMillionDollars.OBLIGATION between Pair(ALICE, BOB)
        val obligationBobToAlice = oneMillionDollars.OBLIGATION between Pair(BOB, ALICE)
        val tx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generatePaymentNetting(this, obligationAliceToBob.amount.token, DUMMY_NOTARY, obligationAliceToBob, obligationBobToAlice)
        }.toWireTransaction()
        assertEquals(0, tx.outputs.size)
    }

    /** Test generating a transaction to two obligations, where one is bigger than the other and therefore there is a remainder. */
    @Test
    fun `generate payment net transaction with remainder`() {
        val obligationAliceToBob = oneMillionDollars.OBLIGATION between Pair(ALICE, BOB)
        val obligationBobToAlice = (2000000.DOLLARS `issued by` defaultIssuer).OBLIGATION between Pair(BOB, ALICE)
        val tx = TransactionType.General.Builder(null).apply {
            Obligation<Currency>().generatePaymentNetting(this, obligationAliceToBob.amount.token, DUMMY_NOTARY, obligationAliceToBob, obligationBobToAlice)
        }.toWireTransaction()
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
                    beneficiary = MINI_CORP, notary = DUMMY_NOTARY)
        }
        var stx = miniCorpServices.signInitialTransaction(tx)
        var stateAndRef = stx.tx.outRef<Obligation.State<Currency>>(0)

        // Now generate a transaction marking the obligation as having defaulted
        tx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateSetLifecycle(this, listOf(stateAndRef), Lifecycle.DEFAULTED, DUMMY_NOTARY)
        }
        var ptx = miniCorpServices.signInitialTransaction(tx, MINI_CORP_PUBKEY)
        stx = notaryServices.addSignature(ptx)

        assertEquals(1, stx.tx.outputs.size)
        assertEquals(stateAndRef.state.data.copy(lifecycle = Lifecycle.DEFAULTED), stx.tx.outputs[0].data)
        stx.verifySignatures()

        // And set it back
        stateAndRef = stx.tx.outRef<Obligation.State<Currency>>(0)
        tx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateSetLifecycle(this, listOf(stateAndRef), Lifecycle.NORMAL, DUMMY_NOTARY)
        }
        ptx = miniCorpServices.signInitialTransaction(tx)
        stx = notaryServices.addSignature(ptx)
        assertEquals(1, stx.tx.outputs.size)
        assertEquals(stateAndRef.state.data.copy(lifecycle = Lifecycle.NORMAL), stx.tx.outputs[0].data)
        stx.verifySignatures()
    }

    /** Test generating a transaction to settle an obligation. */
    @Test
    fun `generate settlement transaction`() {
        val cashTx = TransactionType.General.Builder(null).apply {
            Cash().generateIssue(this, 100.DOLLARS `issued by` defaultIssuer, MINI_CORP, DUMMY_NOTARY)
        }.toWireTransaction()

        // Generate a transaction issuing the obligation
        val obligationTx = TransactionType.General.Builder(null).apply {
            Obligation<Currency>().generateIssue(this, MINI_CORP, megaCorpDollarSettlement, 100.DOLLARS.quantity,
                    beneficiary = MINI_CORP, notary = DUMMY_NOTARY)
        }.toWireTransaction()

        // Now generate a transaction settling the obligation
        val settleTx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateSettle(this, listOf(obligationTx.outRef(0)), listOf(cashTx.outRef(0)), Cash.Commands.Move(), DUMMY_NOTARY)
        }.toWireTransaction()
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
                timeWindow(TEST_TX_TIME)
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
                output("change") { oneMillionDollars.OBLIGATION between Pair(MEGA_CORP, BOB) }
                command(BOB_PUBKEY, MEGA_CORP_PUBKEY) { Obligation.Commands.Net(NetType.CLOSE_OUT) }
                timeWindow(TEST_TX_TIME)
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
                output("change") { (oneMillionDollars.splitEvenly(2).first()).OBLIGATION between Pair(ALICE, BOB) }
                command(BOB_PUBKEY) { Obligation.Commands.Net(NetType.CLOSE_OUT) }
                timeWindow(TEST_TX_TIME)
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
                timeWindow(TEST_TX_TIME)
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
                timeWindow(TEST_TX_TIME)
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
                timeWindow(TEST_TX_TIME)
                this `fails with` "all involved parties have signed"
            }
        }

        // Multilateral netting, A -> B -> C which can net down to A -> C
        ledger {
            cashObligationTestRoots(this)
            transaction("Issuance") {
                input("Bob's $1,000,000 obligation to Alice")
                input("MegaCorp's $1,000,000 obligation to Bob")
                output("MegaCorp's $1,000,000 obligation to Alice") { oneMillionDollars.OBLIGATION between Pair(MEGA_CORP, ALICE) }
                command(ALICE_PUBKEY, BOB_PUBKEY, MEGA_CORP_PUBKEY) { Obligation.Commands.Net(NetType.PAYMENT) }
                timeWindow(TEST_TX_TIME)
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
                output("MegaCorp's $1,000,000 obligation to Alice") { oneMillionDollars.OBLIGATION between Pair(MEGA_CORP, ALICE) }
                command(ALICE_PUBKEY, BOB_PUBKEY) { Obligation.Commands.Net(NetType.PAYMENT) }
                timeWindow(TEST_TX_TIME)
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
                output("Bob's $1,000,000") { 1000000.DOLLARS.CASH `issued by` defaultIssuer `owned by` BOB }
                command(ALICE_PUBKEY) { Obligation.Commands.Settle(Amount(oneMillionDollars.quantity, inState.amount.token)) }
                command(ALICE_PUBKEY) { Cash.Commands.Move(Obligation<Currency>().legalContractReference) }
                this.verifies()
            }
        }

        // Try partial settling of an obligation
        val halfAMillionDollars = 500000.DOLLARS `issued by` defaultIssuer
        ledger {
            transaction("Settlement") {
                input(oneMillionDollars.OBLIGATION between Pair(ALICE, BOB))
                input(500000.DOLLARS.CASH `issued by` defaultIssuer `owned by` ALICE)
                output("Alice's $500,000 obligation to Bob") { halfAMillionDollars.OBLIGATION between Pair(ALICE, BOB) }
                output("Bob's $500,000") { 500000.DOLLARS.CASH `issued by` defaultIssuer `owned by` BOB }
                command(ALICE_PUBKEY) { Obligation.Commands.Settle(Amount(oneMillionDollars.quantity / 2, inState.amount.token)) }
                command(ALICE_PUBKEY) { Cash.Commands.Move(Obligation<Currency>().legalContractReference) }
                this.verifies()
            }
        }

        // Make sure we can't settle an obligation that's defaulted
        val defaultedObligation: Obligation.State<Currency> = (oneMillionDollars.OBLIGATION between Pair(ALICE, BOB)).copy(lifecycle = Lifecycle.DEFAULTED)
        ledger {
            transaction("Settlement") {
                input(defaultedObligation) // Alice's defaulted $1,000,000 obligation to Bob
                input(1000000.DOLLARS.CASH `issued by` defaultIssuer `owned by` ALICE)
                output("Bob's $1,000,000") { 1000000.DOLLARS.CASH `issued by` defaultIssuer `owned by` BOB }
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
                output("Bob's $1,000,000") { 1000000.DOLLARS.CASH `issued by` defaultIssuer `owned by` BOB }
                command(ALICE_PUBKEY) { Obligation.Commands.Settle(Amount(oneMillionDollars.quantity / 2, inState.amount.token)) }
                command(ALICE_PUBKEY) { Cash.Commands.Move(Obligation<Currency>().legalContractReference) }
                this `fails with` "amount in settle command"
            }
        }
    }

    @Test
    fun `commodity settlement`() {
        val defaultFcoj = Issued(defaultIssuer, Commodity.getInstance("FCOJ")!!)
        val oneUnitFcoj = Amount(1, defaultFcoj)
        val obligationDef = Obligation.Terms(NonEmptySet.of(CommodityContract().legalContractReference), NonEmptySet.of(defaultFcoj), TEST_TX_TIME)
        val oneUnitFcojObligation = Obligation.State(Obligation.Lifecycle.NORMAL, ALICE,
                obligationDef, oneUnitFcoj.quantity, NULL_PARTY)
        // Try settling a simple commodity obligation
        ledger {
            unverifiedTransaction {
                output("Alice's 1 FCOJ obligation to Bob", oneUnitFcojObligation between Pair(ALICE, BOB))
                output("Alice's 1 FCOJ", CommodityContract.State(oneUnitFcoj, ALICE))
            }
            transaction("Settlement") {
                input("Alice's 1 FCOJ obligation to Bob")
                input("Alice's 1 FCOJ")
                output("Bob's 1 FCOJ") { CommodityContract.State(oneUnitFcoj, BOB) }
                command(ALICE_PUBKEY) { Obligation.Commands.Settle(Amount(oneUnitFcoj.quantity, oneUnitFcojObligation.amount.token)) }
                command(ALICE_PUBKEY) { CommodityContract.Commands.Move(Obligation<Commodity>().legalContractReference) }
                verifies()
            }
        }
    }

    @Test
    fun `payment default`() {
        // Try defaulting an obligation without a time-window.
        ledger {
            cashObligationTestRoots(this)
            transaction("Settlement") {
                input("Alice's $1,000,000 obligation to Bob")
                output("Alice's defaulted $1,000,000 obligation to Bob") { (oneMillionDollars.OBLIGATION between Pair(ALICE, BOB)).copy(lifecycle = Lifecycle.DEFAULTED) }
                command(BOB_PUBKEY) { Obligation.Commands.SetLifecycle(Lifecycle.DEFAULTED) }
                this `fails with` "there is a time-window from the authority"
            }
        }

        // Try defaulting an obligation due in the future
        val pastTestTime = TEST_TX_TIME - Duration.ofDays(7)
        val futureTestTime = TEST_TX_TIME + Duration.ofDays(7)
        transaction("Settlement") {
            input(oneMillionDollars.OBLIGATION between Pair(ALICE, BOB) `at` futureTestTime)
            output("Alice's defaulted $1,000,000 obligation to Bob") { (oneMillionDollars.OBLIGATION between Pair(ALICE, BOB) `at` futureTestTime).copy(lifecycle = Lifecycle.DEFAULTED) }
            command(BOB_PUBKEY) { Obligation.Commands.SetLifecycle(Lifecycle.DEFAULTED) }
            timeWindow(TEST_TX_TIME)
            this `fails with` "the due date has passed"
        }

        // Try defaulting an obligation that is now in the past
        ledger {
            transaction("Settlement") {
                input(oneMillionDollars.OBLIGATION between Pair(ALICE, BOB) `at` pastTestTime)
                output("Alice's defaulted $1,000,000 obligation to Bob") { (oneMillionDollars.OBLIGATION between Pair(ALICE, BOB) `at` pastTestTime).copy(lifecycle = Lifecycle.DEFAULTED) }
                command(BOB_PUBKEY) { Obligation.Commands.SetLifecycle(Lifecycle.DEFAULTED) }
                timeWindow(TEST_TX_TIME)
                this.verifies()
            }
            this.verifies()
        }
    }

    @Test
    fun testMergeSplit() {
        // Splitting value works.
        transaction {
            command(CHARLIE.owningKey) { Obligation.Commands.Move() }
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
                        beneficiary = AnonymousParty(DUMMY_PUBKEY_2)
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
            command(CHARLIE.owningKey) { Obligation.Commands.Move() }
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
                command(CHARLIE.owningKey) { Obligation.Commands.Exit(Amount(100.DOLLARS.quantity, inState.amount.token)) }
                command(CHARLIE.owningKey) { Obligation.Commands.Move() }
                this `fails with` "the amounts balance"
            }

            tweak {
                command(CHARLIE.owningKey) { Obligation.Commands.Exit(Amount(200.DOLLARS.quantity, inState.amount.token)) }
                this `fails with` "required net.corda.core.contracts.FungibleAsset.Commands.Move command"

                tweak {
                    command(CHARLIE.owningKey) { Obligation.Commands.Move() }
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

            command(CHARLIE.owningKey) { Obligation.Commands.Move() }

            this `fails with` "the amounts balance"

            command(CHARLIE.owningKey) { Obligation.Commands.Exit(Amount(200.DOLLARS.quantity, inState.amount.token.copy(product = megaCorpDollarSettlement))) }
            this `fails with` "the amounts balance"

            command(CHARLIE.owningKey) { Obligation.Commands.Exit(Amount(200.POUNDS.quantity, inState.amount.token.copy(product = megaCorpPoundSettlement))) }
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
                output { inState.copy(beneficiary = AnonymousParty(DUMMY_PUBKEY_2), quantity = 200000L) }
                this `fails with` "the amounts balance"
            }
            // Missing MiniCorp deposit
            tweak {
                output { inState.copy(beneficiary = AnonymousParty(DUMMY_PUBKEY_2)) }
                output { inState.copy(beneficiary = AnonymousParty(DUMMY_PUBKEY_2)) }
                this `fails with` "the amounts balance"
            }

            // This works.
            output { inState.copy(beneficiary = AnonymousParty(DUMMY_PUBKEY_2)) }
            output { inState.copy(beneficiary = AnonymousParty(DUMMY_PUBKEY_2)) `issued by` MINI_CORP }
            command(CHARLIE.owningKey) { Obligation.Commands.Move() }
            this.verifies()
        }
    }

    @Test
    fun multiCurrency() {
        // Check we can do an atomic currency trade tx.
        transaction {
            val pounds = Obligation.State(Lifecycle.NORMAL, MINI_CORP, megaCorpPoundSettlement, 658.POUNDS.quantity, AnonymousParty(DUMMY_PUBKEY_2))
            input { inState `owned by` CHARLIE }
            input { pounds }
            output { inState `owned by` AnonymousParty(DUMMY_PUBKEY_2) }
            output { pounds `owned by` CHARLIE }
            command(CHARLIE.owningKey, DUMMY_PUBKEY_2) { Obligation.Commands.Move() }

            this.verifies()
        }
    }

    @Test
    fun `nettability of settlement contracts`() {
        val fiveKDollarsFromMegaToMega = Obligation.State(Lifecycle.NORMAL, MEGA_CORP, megaCorpDollarSettlement,
                5000.DOLLARS.quantity, MEGA_CORP)
        val twoKDollarsFromMegaToMini = Obligation.State(Lifecycle.NORMAL, MEGA_CORP, megaCorpDollarSettlement,
                2000.DOLLARS.quantity, MINI_CORP)
        val oneKDollarsFromMiniToMega = Obligation.State(Lifecycle.NORMAL, MINI_CORP, megaCorpDollarSettlement,
                1000.DOLLARS.quantity, MEGA_CORP)

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
                fiveKDollarsFromMegaToMega.copy(template = megaCorpDollarSettlement.copy(acceptableContracts = NonEmptySet.of(SecureHash.randomSHA256()))).bilateralNetState)

        // States must not be nettable if the trusted issuers differ
        val miniCorpIssuer = NonEmptySet.of(Issued(MINI_CORP.ref(1), USD))
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
                5000.DOLLARS.quantity, MEGA_CORP)
        val oneKDollarsFromMiniToMega = Obligation.State(Lifecycle.NORMAL, MINI_CORP, megaCorpDollarSettlement,
                1000.DOLLARS.quantity, MEGA_CORP)

        // Issuance definitions must match the input
        assertEquals(fiveKDollarsFromMegaToMega.template, megaCorpDollarSettlement)
        assertEquals(oneKDollarsFromMiniToMega.template, megaCorpDollarSettlement)
    }

    @Test
    fun `adding two settlement contracts nets them`() {
        val megaCorpDollarSettlement = Obligation.Terms(trustedCashContract, megaIssuedDollars, fivePm)
        val fiveKDollarsFromMegaToMini = Obligation.State(Lifecycle.NORMAL, MEGA_CORP, megaCorpDollarSettlement,
                5000.DOLLARS.quantity, MINI_CORP)
        val oneKDollarsFromMiniToMega = Obligation.State(Lifecycle.NORMAL, MINI_CORP, megaCorpDollarSettlement,
                1000.DOLLARS.quantity, MEGA_CORP)

        var actual = fiveKDollarsFromMegaToMini.net(fiveKDollarsFromMegaToMini.copy(quantity = 2000.DOLLARS.quantity))
        // Both pay from mega to mini, so we add directly
        var expected = Obligation.State(Lifecycle.NORMAL, MEGA_CORP, megaCorpDollarSettlement, 7000.DOLLARS.quantity,
                MINI_CORP)
        assertEquals(expected, actual)

        // Reversing the direction should mean adding the second state subtracts from the first
        actual = fiveKDollarsFromMegaToMini.net(oneKDollarsFromMiniToMega)
        expected = fiveKDollarsFromMegaToMini.copy(quantity = 4000.DOLLARS.quantity)
        assertEquals(expected, actual)

        // Trying to add an incompatible state must throw an error
        assertFailsWith(IllegalArgumentException::class) {
            fiveKDollarsFromMegaToMini.net(Obligation.State(Lifecycle.NORMAL, MINI_CORP, megaCorpDollarSettlement, 1000.DOLLARS.quantity,
                    MINI_CORP))
        }
    }

    @Test
    fun `extracting amounts due between parties from a list of states`() {
        val megaCorpDollarSettlement = Obligation.Terms(trustedCashContract, megaIssuedDollars, fivePm)
        val fiveKDollarsFromMegaToMini = Obligation.State(Lifecycle.NORMAL, MEGA_CORP, megaCorpDollarSettlement,
                5000.DOLLARS.quantity, MINI_CORP)
        val amount = fiveKDollarsFromMegaToMini.amount
        val expected: Map<Pair<AbstractParty, AbstractParty>, Amount<Obligation.Terms<Currency>>> = mapOf(Pair(Pair(MEGA_CORP, MINI_CORP), Amount(amount.quantity, amount.token.product)))
        val actual = extractAmountsDue(megaCorpDollarSettlement, listOf(fiveKDollarsFromMegaToMini))
        assertEquals(expected, actual)
    }

    @Test
    fun `netting equal balances due between parties`() {
        // Now try it with two balances, which cancel each other out
        val balanced: Map<Pair<AbstractParty, AbstractParty>, Amount<Currency>> = mapOf(
                Pair(Pair(ALICE, BOB), Amount(100000000, GBP)),
                Pair(Pair(BOB, ALICE), Amount(100000000, GBP))
        )
        val expected: Map<Pair<AbstractParty, AbstractParty>, Amount<Currency>> = emptyMap() // Zero balances are stripped before returning
        val actual: Map<Pair<AbstractParty, AbstractParty>, Amount<Currency>> = netAmountsDue(balanced)
        assertEquals(expected, actual)
    }

    @Test
    fun `netting difference balances due between parties`() {
        // Now try it with two balances, which cancel each other out
        val balanced: Map<Pair<AbstractParty, AbstractParty>, Amount<Currency>> = mapOf(
                Pair(Pair(ALICE, BOB), Amount(100000000, GBP)),
                Pair(Pair(BOB, ALICE), Amount(200000000, GBP))
        )
        val expected: Map<Pair<AbstractParty, AbstractParty>, Amount<Currency>> = mapOf(
                Pair(Pair(BOB, ALICE), Amount(100000000, GBP))
        )
        val actual = netAmountsDue(balanced)
        assertEquals(expected, actual)
    }

    @Test
    fun `summing empty balances due between parties`() {
        val empty = emptyMap<Pair<AbstractParty, AbstractParty>, Amount<Currency>>()
        val expected = emptyMap<AbstractParty, Long>()
        val actual = sumAmountsDue(empty)
        assertEquals(expected, actual)
    }

    @Test
    fun `summing balances due between parties`() {
        val simple: Map<Pair<AbstractParty, AbstractParty>, Amount<Currency>> = mapOf(Pair(Pair(ALICE, BOB), Amount(100000000, GBP)))
        val expected: Map<AbstractParty, Long> = mapOf(Pair(ALICE, -100000000L), Pair(BOB, 100000000L))
        val actual = sumAmountsDue(simple)
        assertEquals(expected, actual)
    }

    @Test
    fun `summing balances due between parties which net to zero`() {
        // Now try it with two balances, which cancel each other out
        val balanced: Map<Pair<AbstractParty, AbstractParty>, Amount<Currency>> = mapOf(
                Pair(Pair(ALICE, BOB), Amount(100000000, GBP)),
                Pair(Pair(BOB, ALICE), Amount(100000000, GBP))
        )
        val expected: Map<AbstractParty, Long> = emptyMap() // Zero balances are stripped before returning
        val actual = sumAmountsDue(balanced)
        assertEquals(expected, actual)
    }

    val Issued<Currency>.OBLIGATION_DEF: Obligation.Terms<Currency>
        get() = Obligation.Terms(NonEmptySet.of(Cash().legalContractReference), NonEmptySet.of(this), TEST_TX_TIME)
    val Amount<Issued<Currency>>.OBLIGATION: Obligation.State<Currency>
        get() = Obligation.State(Obligation.Lifecycle.NORMAL, DUMMY_OBLIGATION_ISSUER, token.OBLIGATION_DEF, quantity, NULL_PARTY)
}

package net.corda.finance.contracts.asset

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.*
import net.corda.core.crypto.NullKeys.NULL_PARTY
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.days
import net.corda.core.utilities.hours
import net.corda.finance.*
import net.corda.finance.contracts.Commodity
import net.corda.finance.contracts.NetType
import net.corda.finance.contracts.asset.Obligation.Lifecycle
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.*
import net.corda.testing.dsl.*
import net.corda.testing.internal.TEST_TX_TIME
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.vault.CommodityState
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import net.corda.testing.node.transaction
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ObligationTests {
    private companion object {
        val alice = TestIdentity(ALICE_NAME, 70)
        val bob = TestIdentity(BOB_NAME, 80)
        val CHARLIE = TestIdentity(CHARLIE_NAME, 90).party
        val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val DUMMY_OBLIGATION_ISSUER = TestIdentity(CordaX500Name("Snake Oil Issuer", "London", "GB"), 10).party
        val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
        val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))
        val ALICE get() = alice.party
        val ALICE_PUBKEY get() = alice.publicKey
        val BOB get() = bob.party
        val BOB_PUBKEY get() = bob.publicKey
        val DUMMY_NOTARY get() = dummyNotary.party
        val MEGA_CORP get() = megaCorp.party
        val MEGA_CORP_PUBKEY get() = megaCorp.publicKey
        val MINI_CORP get() = miniCorp.party
        val MINI_CORP_PUBKEY get() = miniCorp.publicKey
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    private val defaultRef = OpaqueBytes.of(1)
    private val defaultIssuer = MEGA_CORP.ref(defaultRef)
    private val oneMillionDollars = 1000000.DOLLARS `issued by` defaultIssuer
    private val trustedCashContract = NonEmptySet.of(SecureHash.randomSHA256() as SecureHash)
    private val megaIssuedDollars = NonEmptySet.of(Issued(defaultIssuer, USD))
    private val megaIssuedPounds = NonEmptySet.of(Issued(defaultIssuer, GBP))
    private val fivePm: Instant = TEST_TX_TIME.truncatedTo(ChronoUnit.DAYS) + 17.hours
    private val sixPm: Instant = fivePm + 1.hours
    private val megaCorpDollarSettlement = Obligation.Terms(trustedCashContract, megaIssuedDollars, fivePm)
    private val megaCorpPoundSettlement = megaCorpDollarSettlement.copy(acceptableIssuedProducts = megaIssuedPounds)
    private val inState = Obligation.State(
            lifecycle = Lifecycle.NORMAL,
            obligor = MEGA_CORP,
            template = megaCorpDollarSettlement,
            quantity = 1000.DOLLARS.quantity,
            beneficiary = CHARLIE
    )
    private val outState = inState.copy(beneficiary = AnonymousParty(BOB_PUBKEY))
    private val miniCorpServices = MockServices(listOf("net.corda.finance.contracts.asset"), miniCorp, rigorousMock())
    private val notaryServices = MockServices(emptyList(), MEGA_CORP.name, rigorousMock(), dummyNotary.keyPair)
    private val identityService = rigorousMock<IdentityServiceInternal>().also {
        doReturn(null).whenever(it).partyFromKey(ALICE_PUBKEY)
        doReturn(null).whenever(it).partyFromKey(BOB_PUBKEY)
        doReturn(null).whenever(it).partyFromKey(CHARLIE.owningKey)
        doReturn(MEGA_CORP).whenever(it).partyFromKey(MEGA_CORP_PUBKEY)
        doReturn(MINI_CORP).whenever(it).partyFromKey(MINI_CORP_PUBKEY)
    }
    private val mockService = MockServices(listOf("net.corda.finance.contracts.asset"), MEGA_CORP.name, identityService)
    private val ledgerServices get() = MockServices(listOf("net.corda.finance.contracts.asset", "net.corda.testing.contracts"), MEGA_CORP.name, identityService)
    private fun cashObligationTestRoots(
            group: LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>
    ) = group.apply {
        unverifiedTransaction {
            attachments(Obligation.PROGRAM_ID)
            output(Obligation.PROGRAM_ID, "Alice's $1,000,000 obligation to Bob", oneMillionDollars.OBLIGATION between Pair(ALICE, BOB))
            output(Obligation.PROGRAM_ID, "Bob's $1,000,000 obligation to Alice", oneMillionDollars.OBLIGATION between Pair(BOB, ALICE))
            output(Obligation.PROGRAM_ID, "MegaCorp's $1,000,000 obligation to Bob", oneMillionDollars.OBLIGATION between Pair(MEGA_CORP, BOB))
            output(Obligation.PROGRAM_ID, "Alice's $1,000,000", 1000000.DOLLARS.CASH issuedBy defaultIssuer ownedBy ALICE)
        }
    }

    private fun transaction(script: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail) = run {
        ledgerServices.transaction(DUMMY_NOTARY, script)
    }

    @Test
    fun trivial() {
        transaction {
            attachments(Obligation.PROGRAM_ID)
            input(Obligation.PROGRAM_ID, inState)
            tweak {
                output(Obligation.PROGRAM_ID, outState.copy(quantity = 2000.DOLLARS.quantity))
                command(CHARLIE.owningKey, Obligation.Commands.Move())
                this `fails with` "the amounts balance"
            }
            tweak {
                output(Obligation.PROGRAM_ID, outState)
                command(CHARLIE.owningKey, DummyCommandData)
                // Invalid command
                this `fails with` "required net.corda.finance.contracts.asset.Obligation.Commands.Move command"
            }
            tweak {
                output(Obligation.PROGRAM_ID, outState)
                command(BOB_PUBKEY, Obligation.Commands.Move())
                this `fails with` "the owning keys are a subset of the signing keys"
            }
            tweak {
                output(Obligation.PROGRAM_ID, outState)
                output(Obligation.PROGRAM_ID, outState `issued by` MINI_CORP)
                command(CHARLIE.owningKey, Obligation.Commands.Move())
                this `fails with` "at least one obligation input"
            }
            // Simple reallocation works.
            tweak {
                output(Obligation.PROGRAM_ID, outState)
                command(CHARLIE.owningKey, Obligation.Commands.Move())
                this.verifies()
            }
        }
    }

    @Test
    fun `issue debt`() {
        // Check we can't "move" debt into existence.
        transaction {
            attachments(DummyContract.PROGRAM_ID, Obligation.PROGRAM_ID)
            input(DummyContract.PROGRAM_ID, DummyState())
            output(Obligation.PROGRAM_ID, outState)
            command(MINI_CORP_PUBKEY, Obligation.Commands.Move())
            this `fails with` "at least one obligation input"
        }

        // Check we can issue money only as long as the issuer institution is a command signer, i.e. any recognised
        // institution is allowed to issue as much cash as they want.
        transaction {
            attachments(Obligation.PROGRAM_ID)
            output(Obligation.PROGRAM_ID, outState)
            command(CHARLIE.owningKey, Obligation.Commands.Issue())
            this `fails with` "output states are issued by a command signer"
        }
        transaction {
            attachments(Obligation.PROGRAM_ID)
            output(Obligation.PROGRAM_ID,
                Obligation.State(
                        obligor = MINI_CORP,
                        quantity = 1000.DOLLARS.quantity,
                        beneficiary = CHARLIE,
                        template = megaCorpDollarSettlement))
            command(MINI_CORP_PUBKEY, Obligation.Commands.Issue())
            this.verifies()
        }
        run {
            // Test generation works.
            val tx = TransactionBuilder(notary = null).apply {
                Obligation<Currency>().generateIssue(this, MINI_CORP, megaCorpDollarSettlement, 100.DOLLARS.quantity,
                        beneficiary = CHARLIE, notary = DUMMY_NOTARY)
            }.toWireTransaction(miniCorpServices)
            assertTrue(tx.inputs.isEmpty())
            val expected = Obligation.State(
                    obligor = MINI_CORP,
                    quantity = 100.DOLLARS.quantity,
                    beneficiary = CHARLIE,
                    template = megaCorpDollarSettlement
            )
            assertEquals(tx.getOutput(0), expected)
            assertTrue(tx.commands[0].value is Obligation.Commands.Issue)
            assertEquals(MINI_CORP_PUBKEY, tx.commands[0].signers[0])
        }
        // We can consume $1000 in a transaction and output $2000 as long as it's signed by an issuer.
        transaction {
            attachments(Obligation.PROGRAM_ID)
            input(Obligation.PROGRAM_ID, inState)
            output(Obligation.PROGRAM_ID, inState.copy(quantity = inState.amount.quantity * 2))
            // Move fails: not allowed to summon money.
            tweak {
                command(CHARLIE.owningKey, Obligation.Commands.Move())
                this `fails with` "the amounts balance"
            }

            // Issue works.
            tweak {
                command(MEGA_CORP_PUBKEY, Obligation.Commands.Issue())
                this.verifies()
            }
        }

        // Can't use an issue command to lower the amount.
        transaction {
            attachments(Obligation.PROGRAM_ID)
            input(Obligation.PROGRAM_ID, inState)
            output(Obligation.PROGRAM_ID, inState.copy(quantity = inState.amount.quantity / 2))
            command(MEGA_CORP_PUBKEY, Obligation.Commands.Issue())
            this `fails with` "output values sum to more than the inputs"
        }

        // Can't have an issue command that doesn't actually issue money.
        transaction {
            attachments(Obligation.PROGRAM_ID)
            input(Obligation.PROGRAM_ID, inState)
            output(Obligation.PROGRAM_ID, inState)
            command(MEGA_CORP_PUBKEY, Obligation.Commands.Issue())
            this `fails with` ""
        }

        // Can't have any other commands if we have an issue command (because the issue command overrules them).
        transaction {
            attachments(Obligation.PROGRAM_ID)
            input(Obligation.PROGRAM_ID, inState)
            output(Obligation.PROGRAM_ID, inState.copy(quantity = inState.amount.quantity * 2))
            command(MEGA_CORP_PUBKEY, Obligation.Commands.Issue())
            tweak {
                command(MEGA_CORP_PUBKEY, Obligation.Commands.Issue())
                this `fails with` "there is only a single issue command"
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
        val tx = TransactionBuilder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateIssue(this, MINI_CORP, megaCorpDollarSettlement, 100.DOLLARS.quantity,
                    beneficiary = MINI_CORP, notary = DUMMY_NOTARY)
        }.toWireTransaction(miniCorpServices)


        // Include the previously issued obligation in a new issuance command
        val ptx = TransactionBuilder(DUMMY_NOTARY)
        ptx.addInputState(tx.outRef<Obligation.State<Currency>>(0))
        Obligation<Currency>().generateIssue(ptx, MINI_CORP, megaCorpDollarSettlement, 100.DOLLARS.quantity,
                beneficiary = MINI_CORP, notary = DUMMY_NOTARY)
    }

    /** Test generating a transaction to net two obligations of the same size, and therefore there are no outputs. */
    @Test
    fun `generate close-out net transaction`() {
        val obligationAliceToBob = getStateAndRef(oneMillionDollars.OBLIGATION between Pair(ALICE, BOB), Obligation.PROGRAM_ID)
        val obligationBobToAlice = getStateAndRef(oneMillionDollars.OBLIGATION between Pair(BOB, ALICE), Obligation.PROGRAM_ID)
        val tx = TransactionBuilder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateCloseOutNetting(this, ALICE, obligationAliceToBob, obligationBobToAlice)
        }.toWireTransaction(miniCorpServices)
        assertEquals(0, tx.outputs.size)
    }

    /** Test generating a transaction to net two obligations of the different sizes, and confirm the balance is correct. */
    @Test
    fun `generate close-out net transaction with remainder`() {
        val obligationAliceToBob = getStateAndRef((2000000.DOLLARS `issued by` defaultIssuer).OBLIGATION between Pair(ALICE, BOB), Obligation.PROGRAM_ID)
        val obligationBobToAlice = getStateAndRef(oneMillionDollars.OBLIGATION between Pair(BOB, ALICE), Obligation.PROGRAM_ID)
        val tx = TransactionBuilder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateCloseOutNetting(this, ALICE, obligationAliceToBob, obligationBobToAlice)
        }.toWireTransaction(miniCorpServices)
        assertEquals(1, tx.outputs.size)

        val actual = tx.getOutput(0)
        assertEquals((1000000.DOLLARS `issued by` defaultIssuer).OBLIGATION between Pair(ALICE, BOB), actual)
    }

    /** Test generating a transaction to net two obligations of the same size, and therefore there are no outputs. */
    @Test
    fun `generate payment net transaction`() {
        val obligationAliceToBob = getStateAndRef(oneMillionDollars.OBLIGATION between Pair(ALICE, BOB), Obligation.PROGRAM_ID)
        val obligationBobToAlice = getStateAndRef(oneMillionDollars.OBLIGATION between Pair(BOB, ALICE), Obligation.PROGRAM_ID)
        val tx = TransactionBuilder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generatePaymentNetting(this, obligationAliceToBob.state.data.amount.token, DUMMY_NOTARY, obligationAliceToBob, obligationBobToAlice)
        }.toWireTransaction(miniCorpServices)
        assertEquals(0, tx.outputs.size)
    }

    /** Test generating a transaction to two obligations, where one is bigger than the other and therefore there is a remainder. */
    @Test
    fun `generate payment net transaction with remainder`() {
        val obligationAliceToBob = getStateAndRef(oneMillionDollars.OBLIGATION between Pair(ALICE, BOB), Obligation.PROGRAM_ID)
        val obligationAliceToBobState = obligationAliceToBob.state.data
        val obligationBobToAlice = getStateAndRef((2000000.DOLLARS `issued by` defaultIssuer).OBLIGATION between Pair(BOB, ALICE), Obligation.PROGRAM_ID)
        val obligationBobToAliceState = obligationBobToAlice.state.data
        val tx = TransactionBuilder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generatePaymentNetting(this, obligationAliceToBobState.amount.token, DUMMY_NOTARY, obligationAliceToBob, obligationBobToAlice)
        }.toWireTransaction(miniCorpServices)
        assertEquals(1, tx.outputs.size)
        val expected = obligationBobToAliceState.copy(quantity = obligationBobToAliceState.quantity - obligationAliceToBobState.quantity)
        val actual = tx.getOutput(0)
        assertEquals(expected, actual)
    }

    private inline fun <reified T : ContractState> getStateAndRef(state: T, contractClassName: ContractClassName): StateAndRef<T> {
        val txState = TransactionState(state, contractClassName, DUMMY_NOTARY)
        return StateAndRef(txState, StateRef(SecureHash.randomSHA256(), 0))

    }

    /** Test generating a transaction to mark outputs as having defaulted. */
    @Test
    fun `generate set lifecycle`() {
        // We don't actually verify the states, this is just here to make things look sensible
        val dueBefore = TEST_TX_TIME - 7.days

        // Generate a transaction issuing the obligation.
        var tx = TransactionBuilder(null).apply {
            val amount = Amount(100, Issued(defaultIssuer, USD))
            Obligation<Currency>().generateCashIssue(this, ALICE, cashContractBytes.sha256(), amount, dueBefore,
                    beneficiary = MINI_CORP, notary = DUMMY_NOTARY)
        }
        var stx = miniCorpServices.signInitialTransaction(tx)
        var stateAndRef = stx.tx.outRef<Obligation.State<Currency>>(0)

        // Now generate a transaction marking the obligation as having defaulted
        tx = TransactionBuilder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateSetLifecycle(this, listOf(stateAndRef), Lifecycle.DEFAULTED, DUMMY_NOTARY)
        }
        var ptx = miniCorpServices.signInitialTransaction(tx, MINI_CORP_PUBKEY)
        stx = notaryServices.addSignature(ptx)

        assertEquals(1, stx.tx.outputs.size)
        assertEquals(stateAndRef.state.data.copy(lifecycle = Lifecycle.DEFAULTED), stx.tx.getOutput(0))
        stx.verifyRequiredSignatures()

        // And set it back
        stateAndRef = stx.tx.outRef(0)
        tx = TransactionBuilder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateSetLifecycle(this, listOf(stateAndRef), Lifecycle.NORMAL, DUMMY_NOTARY)
        }
        ptx = miniCorpServices.signInitialTransaction(tx)
        stx = notaryServices.addSignature(ptx)
        assertEquals(1, stx.tx.outputs.size)
        assertEquals(stateAndRef.state.data.copy(lifecycle = Lifecycle.NORMAL), stx.tx.getOutput(0))
        stx.verifyRequiredSignatures()
    }

    /** Test generating a transaction to settle an obligation. */
    @Test
    fun `generate settlement transaction`() {
        val cashTx = TransactionBuilder(null).apply {
            Cash().generateIssue(this, 100.DOLLARS `issued by` defaultIssuer, MINI_CORP, DUMMY_NOTARY)
        }.toWireTransaction(miniCorpServices)

        // Generate a transaction issuing the obligation
        val obligationTx = TransactionBuilder(null).apply {
            Obligation<Currency>().generateIssue(this, MINI_CORP, megaCorpDollarSettlement, 100.DOLLARS.quantity,
                    beneficiary = MINI_CORP, notary = DUMMY_NOTARY)
        }.toWireTransaction(miniCorpServices)

        // Now generate a transaction settling the obligation
        val settleTx = TransactionBuilder(DUMMY_NOTARY).apply {
            Obligation<Currency>().generateSettle(this, listOf(obligationTx.outRef(0)), listOf(cashTx.outRef(0)), Cash.Commands.Move(), DUMMY_NOTARY)
        }.toWireTransaction(miniCorpServices)
        assertEquals(2, settleTx.inputs.size)
        assertEquals(1, settleTx.outputs.size)
    }

    @Test
    fun `close-out netting`() {
        // Try netting out two obligations
        mockService.ledger(DUMMY_NOTARY) {
            cashObligationTestRoots(this)
            transaction("Issuance") {
                attachments(Obligation.PROGRAM_ID)
                input("Alice's $1,000,000 obligation to Bob")
                input("Bob's $1,000,000 obligation to Alice")
                // Note we can sign with either key here
                command(ALICE_PUBKEY, Obligation.Commands.Net(NetType.CLOSE_OUT))
                timeWindow(TEST_TX_TIME)
                this.verifies()
            }
            this.verifies()
        }

        // Try netting out two obligations, with the third uninvolved obligation left
        // as-is
        mockService.ledger(DUMMY_NOTARY) {
            cashObligationTestRoots(this)
            transaction("Issuance") {
                attachments(Obligation.PROGRAM_ID)
                input("Alice's $1,000,000 obligation to Bob")
                input("Bob's $1,000,000 obligation to Alice")
                input("MegaCorp's $1,000,000 obligation to Bob")
                output(Obligation.PROGRAM_ID, "change", oneMillionDollars.OBLIGATION between Pair(MEGA_CORP, BOB))
                command(listOf(BOB_PUBKEY, MEGA_CORP_PUBKEY), Obligation.Commands.Net(NetType.CLOSE_OUT))
                timeWindow(TEST_TX_TIME)
                this.verifies()
            }
            this.verifies()
        }

        // Try having outputs mis-match the inputs
        ledgerServices.ledger(DUMMY_NOTARY) {
            cashObligationTestRoots(this)
            transaction("Issuance") {
                attachments(Obligation.PROGRAM_ID)
                input("Alice's $1,000,000 obligation to Bob")
                input("Bob's $1,000,000 obligation to Alice")
                output(Obligation.PROGRAM_ID, "change", oneMillionDollars.splitEvenly(2).first().OBLIGATION between Pair(ALICE, BOB))
                command(BOB_PUBKEY, Obligation.Commands.Net(NetType.CLOSE_OUT))
                timeWindow(TEST_TX_TIME)
                this `fails with` "amounts owed on input and output must match"
            }
        }

        // Have the wrong signature on the transaction
        ledgerServices.ledger(DUMMY_NOTARY) {
            cashObligationTestRoots(this)
            transaction("Issuance") {
                attachments(Obligation.PROGRAM_ID)
                input("Alice's $1,000,000 obligation to Bob")
                input("Bob's $1,000,000 obligation to Alice")
                command(MEGA_CORP_PUBKEY, Obligation.Commands.Net(NetType.CLOSE_OUT))
                timeWindow(TEST_TX_TIME)
                this `fails with` "any involved party has signed"
            }
        }
    }

    @Test
    fun `payment netting`() {
        // Try netting out two obligations
        mockService.ledger(DUMMY_NOTARY) {
            cashObligationTestRoots(this)
            transaction("Issuance") {
                attachments(Obligation.PROGRAM_ID)
                input("Alice's $1,000,000 obligation to Bob")
                input("Bob's $1,000,000 obligation to Alice")
                command(listOf(ALICE_PUBKEY, BOB_PUBKEY), Obligation.Commands.Net(NetType.PAYMENT))
                timeWindow(TEST_TX_TIME)
                this.verifies()
            }
            this.verifies()
        }

        // Try netting out two obligations, but only provide one signature. Unlike close-out netting, we need both
        // signatures for payment netting
        ledgerServices.ledger(DUMMY_NOTARY) {
            cashObligationTestRoots(this)
            transaction("Issuance") {
                attachments(Obligation.PROGRAM_ID)
                input("Alice's $1,000,000 obligation to Bob")
                input("Bob's $1,000,000 obligation to Alice")
                command(BOB_PUBKEY, Obligation.Commands.Net(NetType.PAYMENT))
                timeWindow(TEST_TX_TIME)
                this `fails with` "all involved parties have signed"
            }
        }

        // Multilateral netting, A -> B -> C which can net down to A -> C
        mockService.ledger(DUMMY_NOTARY) {
            cashObligationTestRoots(this)
            transaction("Issuance") {
                attachments(Obligation.PROGRAM_ID)
                input("Bob's $1,000,000 obligation to Alice")
                input("MegaCorp's $1,000,000 obligation to Bob")
                output(Obligation.PROGRAM_ID, "MegaCorp's $1,000,000 obligation to Alice", oneMillionDollars.OBLIGATION between Pair(MEGA_CORP, ALICE))
                command(listOf(ALICE_PUBKEY, BOB_PUBKEY, MEGA_CORP_PUBKEY), Obligation.Commands.Net(NetType.PAYMENT))
                timeWindow(TEST_TX_TIME)
                this.verifies()
            }
            this.verifies()
        }

        // Multilateral netting without the key of the receiving party
        mockService.ledger(DUMMY_NOTARY) {
            cashObligationTestRoots(this)
            transaction("Issuance") {
                attachments(Obligation.PROGRAM_ID)
                input("Bob's $1,000,000 obligation to Alice")
                input("MegaCorp's $1,000,000 obligation to Bob")
                output(Obligation.PROGRAM_ID, "MegaCorp's $1,000,000 obligation to Alice", oneMillionDollars.OBLIGATION between Pair(MEGA_CORP, ALICE))
                command(listOf(ALICE_PUBKEY, BOB_PUBKEY), Obligation.Commands.Net(NetType.PAYMENT))
                timeWindow(TEST_TX_TIME)
                this `fails with` "all involved parties have signed"
            }
        }
    }

    @Test
    fun `cash settlement`() {
        // Try settling an obligation
        ledgerServices.ledger(DUMMY_NOTARY) {
            cashObligationTestRoots(this)
            transaction("Settlement") {
                attachments(Obligation.PROGRAM_ID)
                input("Alice's $1,000,000 obligation to Bob")
                input("Alice's $1,000,000")
                output(Obligation.PROGRAM_ID, "Bob's $1,000,000", 1000000.DOLLARS.CASH issuedBy defaultIssuer ownedBy BOB)
                command(ALICE_PUBKEY, Obligation.Commands.Settle(Amount(oneMillionDollars.quantity, inState.amount.token)))
                command(ALICE_PUBKEY, Cash.Commands.Move(Obligation::class.java))
                attachment(attachment(cashContractBytes.inputStream()))
                this.verifies()
            }
        }

        // Try partial settling of an obligation
        val halfAMillionDollars = 500000.DOLLARS `issued by` defaultIssuer
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction("Settlement") {
                attachments(Obligation.PROGRAM_ID, Cash.PROGRAM_ID)
                input(Obligation.PROGRAM_ID, oneMillionDollars.OBLIGATION between Pair(ALICE, BOB))
                input(Cash.PROGRAM_ID, 500000.DOLLARS.CASH issuedBy defaultIssuer ownedBy ALICE)
                output(Obligation.PROGRAM_ID, "Alice's $500,000 obligation to Bob", halfAMillionDollars.OBLIGATION between Pair(ALICE, BOB))
                output(Obligation.PROGRAM_ID, "Bob's $500,000", 500000.DOLLARS.CASH issuedBy defaultIssuer ownedBy BOB)
                command(ALICE_PUBKEY, Obligation.Commands.Settle(Amount(oneMillionDollars.quantity / 2, inState.amount.token)))
                command(ALICE_PUBKEY, Cash.Commands.Move(Obligation::class.java))
                attachment(attachment(cashContractBytes.inputStream()))
                this.verifies()
            }
        }

        // Make sure we can't settle an obligation that's defaulted
        val defaultedObligation: Obligation.State<Currency> = (oneMillionDollars.OBLIGATION between Pair(ALICE, BOB)).copy(lifecycle = Lifecycle.DEFAULTED)
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction("Settlement") {
                attachments(Obligation.PROGRAM_ID, Cash.PROGRAM_ID)
                input(Obligation.PROGRAM_ID, defaultedObligation) // Alice's defaulted $1,000,000 obligation to Bob
                input(Cash.PROGRAM_ID, 1000000.DOLLARS.CASH issuedBy defaultIssuer ownedBy ALICE)
                output(Obligation.PROGRAM_ID, "Bob's $1,000,000", 1000000.DOLLARS.CASH issuedBy defaultIssuer ownedBy BOB)
                command(ALICE_PUBKEY, Obligation.Commands.Settle(Amount(oneMillionDollars.quantity, inState.amount.token)))
                command(ALICE_PUBKEY, Cash.Commands.Move(Obligation::class.java))
                this `fails with` "all inputs are in the normal state"
            }
        }

        // Make sure settlement amount must match the amount leaving the ledger
        ledgerServices.ledger(DUMMY_NOTARY) {
            cashObligationTestRoots(this)
            transaction("Settlement") {
                attachments(Obligation.PROGRAM_ID)
                input("Alice's $1,000,000 obligation to Bob")
                input("Alice's $1,000,000")
                output(Obligation.PROGRAM_ID, "Bob's $1,000,000", 1000000.DOLLARS.CASH issuedBy defaultIssuer ownedBy BOB)
                command(ALICE_PUBKEY, Obligation.Commands.Settle(Amount(oneMillionDollars.quantity / 2, inState.amount.token)))
                command(ALICE_PUBKEY, Cash.Commands.Move(Obligation::class.java))
                attachment(attachment(cashContractBytes.inputStream()))
                this `fails with` "amount in settle command"
            }
        }
    }

    @Test
    fun `commodity settlement`() {
        val commodityContractBytes = "https://www.big-book-of-banking-law.gov/commodity-claims.html".toByteArray()
        val defaultFcoj = Issued(defaultIssuer, Commodity.getInstance("FCOJ")!!)
        val oneUnitFcoj = Amount(1, defaultFcoj)
        val obligationDef = Obligation.Terms(NonEmptySet.of(commodityContractBytes.sha256() as SecureHash), NonEmptySet.of(defaultFcoj), TEST_TX_TIME)
        val oneUnitFcojObligation = Obligation.State(Obligation.Lifecycle.NORMAL, ALICE,
                obligationDef, oneUnitFcoj.quantity, NULL_PARTY)
        // Try settling a simple commodity obligation
        ledgerServices.ledger(DUMMY_NOTARY) {
            unverifiedTransaction {
                attachments(Obligation.PROGRAM_ID)
                output(Obligation.PROGRAM_ID, "Alice's 1 FCOJ obligation to Bob", oneUnitFcojObligation between Pair(ALICE, BOB))
                output(Obligation.PROGRAM_ID, "Alice's 1 FCOJ", CommodityState(oneUnitFcoj, ALICE))
            }
            transaction("Settlement") {
                attachments(Obligation.PROGRAM_ID)
                input("Alice's 1 FCOJ obligation to Bob")
                input("Alice's 1 FCOJ")
                output(Obligation.PROGRAM_ID, "Bob's 1 FCOJ", CommodityState(oneUnitFcoj, BOB))
                command(ALICE_PUBKEY, Obligation.Commands.Settle(Amount(oneUnitFcoj.quantity, oneUnitFcojObligation.amount.token)))
                command(ALICE_PUBKEY, Obligation.Commands.Move(Obligation::class.java))
                attachment(attachment(commodityContractBytes.inputStream()))
                verifies()
            }
        }
    }

    @Test
    fun `payment default`() {
        // Try defaulting an obligation without a time-window.
        ledgerServices.ledger(DUMMY_NOTARY) {
            cashObligationTestRoots(this)
            transaction("Settlement") {
                attachments(Obligation.PROGRAM_ID)
                input("Alice's $1,000,000 obligation to Bob")
                output(Obligation.PROGRAM_ID, "Alice's defaulted $1,000,000 obligation to Bob", (oneMillionDollars.OBLIGATION between Pair(ALICE, BOB)).copy(lifecycle = Lifecycle.DEFAULTED))
                command(BOB_PUBKEY, Obligation.Commands.SetLifecycle(Lifecycle.DEFAULTED))
                this `fails with` "there is a time-window from the authority"
            }
        }

        // Try defaulting an obligation due in the future
        val pastTestTime = TEST_TX_TIME - 7.days
        val futureTestTime = TEST_TX_TIME + 7.days
        transaction {
            attachments(Obligation.PROGRAM_ID)
            input(Obligation.PROGRAM_ID, oneMillionDollars.OBLIGATION between Pair(ALICE, BOB) at futureTestTime)
            output(Obligation.PROGRAM_ID, "Alice's defaulted $1,000,000 obligation to Bob", (oneMillionDollars.OBLIGATION between Pair(ALICE, BOB) at futureTestTime).copy(lifecycle = Lifecycle.DEFAULTED))
            command(BOB_PUBKEY, Obligation.Commands.SetLifecycle(Lifecycle.DEFAULTED))
            timeWindow(TEST_TX_TIME)
            this `fails with` "the due date has passed"
        }

        // Try defaulting an obligation that is now in the past
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachments(Obligation.PROGRAM_ID)
                input(Obligation.PROGRAM_ID, oneMillionDollars.OBLIGATION between Pair(ALICE, BOB) at pastTestTime)
                output(Obligation.PROGRAM_ID, "Alice's defaulted $1,000,000 obligation to Bob", (oneMillionDollars.OBLIGATION between Pair(ALICE, BOB) at pastTestTime).copy(lifecycle = Lifecycle.DEFAULTED))
                command(BOB_PUBKEY, Obligation.Commands.SetLifecycle(Lifecycle.DEFAULTED))
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
            attachments(Obligation.PROGRAM_ID)
            command(CHARLIE.owningKey, Obligation.Commands.Move())
            tweak {
                input(Obligation.PROGRAM_ID, inState)
                repeat(4) { output(Obligation.PROGRAM_ID, inState.copy(quantity = inState.quantity / 4)) }
                this.verifies()
            }
            // Merging 4 inputs into 2 outputs works.
            tweak {
                repeat(4) { input(Obligation.PROGRAM_ID, inState.copy(quantity = inState.quantity / 4)) }
                output(Obligation.PROGRAM_ID, inState.copy(quantity = inState.quantity / 2))
                output(Obligation.PROGRAM_ID, inState.copy(quantity = inState.quantity / 2))
                this.verifies()
            }
            // Merging 2 inputs into 1 works.
            tweak {
                input(Obligation.PROGRAM_ID, inState.copy(quantity = inState.quantity / 2))
                input(Obligation.PROGRAM_ID, inState.copy(quantity = inState.quantity / 2))
                output(Obligation.PROGRAM_ID, inState)
                this.verifies()
            }
        }
    }

    @Test
    fun zeroSizedValues() {
        transaction {
            attachments(Obligation.PROGRAM_ID)
            command(CHARLIE.owningKey, Obligation.Commands.Move())
            tweak {
                input(Obligation.PROGRAM_ID, inState)
                input(Obligation.PROGRAM_ID, inState.copy(quantity = 0L))
                this `fails with` "zero sized inputs"
            }
            tweak {
                input(Obligation.PROGRAM_ID, inState)
                output(Obligation.PROGRAM_ID, inState)
                output(Obligation.PROGRAM_ID, inState.copy(quantity = 0L))
                this `fails with` "zero sized outputs"
            }
        }
    }

    @Test
    fun trivialMismatches() {
        // Can't change issuer.
        transaction {
            attachments(Obligation.PROGRAM_ID)
            input(Obligation.PROGRAM_ID, inState)
            output(Obligation.PROGRAM_ID, outState `issued by` MINI_CORP)
            command(MINI_CORP_PUBKEY, Obligation.Commands.Move())
            this `fails with` "the amounts balance"
        }
        // Can't mix currencies.
        transaction {
            attachments(Obligation.PROGRAM_ID)
            input(Obligation.PROGRAM_ID, inState)
            output(Obligation.PROGRAM_ID, outState.copy(quantity = 80000, template = megaCorpDollarSettlement))
            output(Obligation.PROGRAM_ID, outState.copy(quantity = 20000, template = megaCorpPoundSettlement))
            command(MINI_CORP_PUBKEY, Obligation.Commands.Move())
            this `fails with` "the amounts balance"
        }
        transaction {
            attachments(Obligation.PROGRAM_ID)
            input(Obligation.PROGRAM_ID, inState)
            input(Obligation.PROGRAM_ID,
                inState.copy(
                        quantity = 15000,
                        template = megaCorpPoundSettlement,
                        beneficiary = AnonymousParty(BOB_PUBKEY)))
            output(Obligation.PROGRAM_ID, outState.copy(quantity = 115000))
            command(MINI_CORP_PUBKEY, Obligation.Commands.Move())
            this `fails with` "the amounts balance"
        }
        // Can't have superfluous input states from different issuers.
        transaction {
            attachments(Obligation.PROGRAM_ID)
            input(Obligation.PROGRAM_ID, inState)
            input(Obligation.PROGRAM_ID, inState `issued by` MINI_CORP)
            output(Obligation.PROGRAM_ID, outState)
            command(CHARLIE.owningKey, Obligation.Commands.Move())
            this `fails with` "the amounts balance"
        }
    }

    @Test
    fun `exit single product obligation`() {
        // Single input/output straightforward case.
        transaction {
            attachments(Obligation.PROGRAM_ID)
            input(Obligation.PROGRAM_ID, inState)
            output(Obligation.PROGRAM_ID, outState.copy(quantity = inState.quantity - 200.DOLLARS.quantity))
            tweak {
                command(CHARLIE.owningKey, Obligation.Commands.Exit(Amount(100.DOLLARS.quantity, inState.amount.token)))
                command(CHARLIE.owningKey, Obligation.Commands.Move())
                this `fails with` "the amounts balance"
            }

            tweak {
                command(CHARLIE.owningKey, Obligation.Commands.Exit(Amount(200.DOLLARS.quantity, inState.amount.token)))
                this `fails with` "required net.corda.finance.contracts.asset.Obligation.Commands.Move command"

                tweak {
                    command(CHARLIE.owningKey, Obligation.Commands.Move())
                    this.verifies()
                }
            }
        }

    }

    @Test
    fun `exit multiple product obligations`() {
        // Multi-product case.
        transaction {
            attachments(Obligation.PROGRAM_ID)
            input(Obligation.PROGRAM_ID, inState.copy(template = inState.template.copy(acceptableIssuedProducts = megaIssuedPounds)))
            input(Obligation.PROGRAM_ID, inState.copy(template = inState.template.copy(acceptableIssuedProducts = megaIssuedDollars)))
            output(Obligation.PROGRAM_ID, inState.copy(template = inState.template.copy(acceptableIssuedProducts = megaIssuedPounds), quantity = inState.quantity - 200.POUNDS.quantity))
            output(Obligation.PROGRAM_ID, inState.copy(template = inState.template.copy(acceptableIssuedProducts = megaIssuedDollars), quantity = inState.quantity - 200.DOLLARS.quantity))
            command(CHARLIE.owningKey, Obligation.Commands.Move())
            this `fails with` "the amounts balance"
            command(CHARLIE.owningKey, Obligation.Commands.Exit(Amount(200.DOLLARS.quantity, inState.amount.token.copy(product = megaCorpDollarSettlement))))
            this `fails with` "the amounts balance"
            command(CHARLIE.owningKey, Obligation.Commands.Exit(Amount(200.POUNDS.quantity, inState.amount.token.copy(product = megaCorpPoundSettlement))))
            this.verifies()
        }
    }

    @Test
    fun multiIssuer() {
        transaction {
            attachments(Obligation.PROGRAM_ID)

            // Gather 2000 dollars from two different issuers.
            input(Obligation.PROGRAM_ID, inState)
            input(Obligation.PROGRAM_ID, inState `issued by` MINI_CORP)
            // Can't merge them together.
            tweak {
                output(Obligation.PROGRAM_ID, inState.copy(beneficiary = AnonymousParty(BOB_PUBKEY), quantity = 200000L))
                command(CHARLIE.owningKey, Obligation.Commands.Move())
                this `fails with` "the amounts balance"
            }
            // Missing MiniCorp deposit
            tweak {
                output(Obligation.PROGRAM_ID, inState.copy(beneficiary = AnonymousParty(BOB_PUBKEY)))
                output(Obligation.PROGRAM_ID, inState.copy(beneficiary = AnonymousParty(BOB_PUBKEY)))
                command(CHARLIE.owningKey, Obligation.Commands.Move())
                this `fails with` "the amounts balance"
            }

            // This works.
            output(Obligation.PROGRAM_ID, inState.copy(beneficiary = AnonymousParty(BOB_PUBKEY)))
            output(Obligation.PROGRAM_ID, inState.copy(beneficiary = AnonymousParty(BOB_PUBKEY)) `issued by` MINI_CORP)
            command(CHARLIE.owningKey, Obligation.Commands.Move())
            this.verifies()
        }
    }

    @Test
    fun multiCurrency() {
        // Check we can do an atomic currency trade tx.
        transaction {
            attachments(Obligation.PROGRAM_ID)
            val pounds = Obligation.State(Lifecycle.NORMAL, MINI_CORP, megaCorpPoundSettlement, 658.POUNDS.quantity, AnonymousParty(BOB_PUBKEY))
            input(Obligation.PROGRAM_ID, inState `owned by` CHARLIE)
            input(Obligation.PROGRAM_ID, pounds)
            output(Obligation.PROGRAM_ID, inState `owned by` AnonymousParty(BOB_PUBKEY))
            output(Obligation.PROGRAM_ID, pounds `owned by` CHARLIE)
            command(listOf(CHARLIE.owningKey, BOB_PUBKEY), Obligation.Commands.Move())
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

    private val cashContractBytes = "https://www.big-book-of-banking-law.gov/cash-claims.html".toByteArray()
    private val Issued<Currency>.OBLIGATION_DEF: Obligation.Terms<Currency>
        get() = Obligation.Terms(NonEmptySet.of(cashContractBytes.sha256() as SecureHash), NonEmptySet.of(this), TEST_TX_TIME)
    private val Amount<Issued<Currency>>.OBLIGATION: Obligation.State<Currency>
        get() = Obligation.State(Obligation.Lifecycle.NORMAL, DUMMY_OBLIGATION_ISSUER, token.OBLIGATION_DEF, quantity, NULL_PARTY)
}

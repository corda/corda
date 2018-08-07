package net.corda.finance.contracts.asset

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.*
import net.corda.finance.utils.sumCash
import net.corda.finance.utils.sumCashBy
import net.corda.finance.utils.sumCashOrNull
import net.corda.finance.utils.sumCashOrZero
import net.corda.node.services.vault.NodeVaultService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.*
import net.corda.testing.dsl.EnforceVerifyOrFail
import net.corda.testing.dsl.TransactionDSL
import net.corda.testing.dsl.TransactionDSLInterpreter
import net.corda.testing.internal.LogHelper
import net.corda.testing.internal.vault.VaultFiller
import net.corda.testing.node.MockServices
import net.corda.testing.node.MockServices.Companion.makeTestDatabaseAndMockServices
import net.corda.testing.node.ledger
import net.corda.testing.node.makeTestIdentityService
import net.corda.testing.node.transaction
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.*
import kotlin.test.*

class CashTests {
    private companion object {
        val alice = TestIdentity(ALICE_NAME, 70)
        val bob = TestIdentity(BOB_NAME, 80)
        val charlie = TestIdentity(CHARLIE_NAME, 90)
        val dummyCashIssuer = TestIdentity(CordaX500Name("Snake Oil Issuer", "London", "GB"), 10)
        val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
        val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    private val defaultIssuer = megaCorp.ref(1)
    private val inState = Cash.State(
            amount = 1000.DOLLARS `issued by` defaultIssuer,
            owner = AnonymousParty(alice.publicKey)
    )
    // Input state held by the issuer
    private val issuerInState = inState.copy(owner = defaultIssuer.party)
    private val outState = issuerInState.copy(owner = AnonymousParty(bob.publicKey))

    private fun Cash.State.editDepositRef(ref: Byte) = copy(
            amount = Amount(amount.quantity, token = amount.token.copy(amount.token.issuer.copy(reference = OpaqueBytes.of(ref))))
    )

    private lateinit var ourServices: MockServices
    private lateinit var miniCorpServices: MockServices
    private lateinit var megaCorpServices: MockServices
    val vault: VaultService get() = miniCorpServices.vaultService
    lateinit var database: CordaPersistence
    private lateinit var vaultStatesUnconsumed: List<StateAndRef<Cash.State>>

    private lateinit var ourIdentity: AbstractParty
    private lateinit var miniCorpAnonymised: AnonymousParty
    private lateinit var cashStates: List<StateAndRef<Cash.State>>

    // TODO: Optimise this so that we don't throw away and rebuild state that can be shared across tests.
    @Before
    fun setUp() {
        val cordapps = listOf("net.corda.finance.contracts.asset", "net.corda.finance.schemas")
        LogHelper.setLevel(NodeVaultService::class)
        megaCorpServices = MockServices(cordapps, megaCorp)
        miniCorpServices = MockServices(cordapps, miniCorp)
        val myself = TestIdentity(CordaX500Name("Me", "London", "GB"))
        val databaseAndServices = makeTestDatabaseAndMockServices(
                cordapps,
                makeTestIdentityService(megaCorp.identity, miniCorp.identity, dummyCashIssuer.identity, dummyNotary.identity, myself.identity),
                myself
        )
        database = databaseAndServices.first
        ourServices = databaseAndServices.second

        // Set up and register identities
        ourIdentity = ourServices.myInfo.singleIdentity()
        miniCorpAnonymised = miniCorpServices.myInfo.singleIdentityAndCert().party.anonymise()

        // Create some cash. Any attempt to spend >$500 will require multiple issuers to be involved.
        database.transaction {
            val vaultFiller = VaultFiller(ourServices, dummyNotary)
            vaultFiller.fillWithSomeTestCash(100.DOLLARS, megaCorpServices, 1, megaCorp.ref(1), ourIdentity)
            vaultFiller.fillWithSomeTestCash(400.DOLLARS, megaCorpServices, 1, megaCorp.ref(1), ourIdentity)
            vaultFiller.fillWithSomeTestCash(80.DOLLARS, miniCorpServices, 1, miniCorp.ref(1), ourIdentity)
            vaultFiller.fillWithSomeTestCash(80.SWISS_FRANCS, miniCorpServices, 1, miniCorp.ref(1), ourIdentity)
        }
        database.transaction {
            vaultStatesUnconsumed = ourServices.vaultService.queryBy<Cash.State>().states
        }
        cashStates = listOf(
            makeCash(100.DOLLARS, megaCorp.party),
            makeCash(400.DOLLARS, megaCorp.party),
            makeCash(80.DOLLARS, miniCorp.party),
            makeCash(80.SWISS_FRANCS, miniCorp.party, 2)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun transaction(script: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail) {
        MockServices(megaCorp).transaction(dummyNotary.party, script)
    }

    @Test
    fun trivial() {
        transaction {
            attachment(Cash.PROGRAM_ID)
            input(Cash.PROGRAM_ID, inState)
            tweak {
                output(Cash.PROGRAM_ID, outState.copy(amount = 2000.DOLLARS `issued by` defaultIssuer))
                command(alice.publicKey, Cash.Commands.Move())
                this `fails with` "the amounts balance"
            }
            tweak {
                output(Cash.PROGRAM_ID, outState)
                command(alice.publicKey, DummyCommandData)
                // Invalid command
                this `fails with` "required net.corda.finance.contracts.asset.Cash.Commands.Move command"
            }
            tweak {
                output(Cash.PROGRAM_ID, outState)
                command(bob.publicKey, Cash.Commands.Move())
                this `fails with` "the owning keys are a subset of the signing keys"
            }
            tweak {
                output(Cash.PROGRAM_ID, outState)
                output(Cash.PROGRAM_ID, outState issuedBy miniCorp.party)
                command(alice.publicKey, Cash.Commands.Move())
                this `fails with` "at least one cash input"
            }
            // Simple reallocation works.
            tweak {
                output(Cash.PROGRAM_ID, outState)
                command(alice.publicKey, Cash.Commands.Move())
                this.verifies()
            }
        }
    }

    @Test
    fun `issue by move`() {
        // Check we can't "move" money into existence.
        transaction {
            attachment(Cash.PROGRAM_ID)
            input(Cash.PROGRAM_ID, DummyState())
            output(Cash.PROGRAM_ID, outState)
            command(miniCorp.publicKey, Cash.Commands.Move())
            this `fails with` "there is at least one cash input for this group"
        }
    }

    @Test
    fun issue() {
        // Check we can issue money only as long as the issuer institution is a command signer, i.e. any recognised
        // institution is allowed to issue as much cash as they want.
        transaction {
            attachment(Cash.PROGRAM_ID)
            output(Cash.PROGRAM_ID, outState)
            command(alice.publicKey, Cash.Commands.Issue())
            this `fails with` "output states are issued by a command signer"
        }
        transaction {
            attachment(Cash.PROGRAM_ID)
            output(Cash.PROGRAM_ID,
                Cash.State(
                        amount = 1000.DOLLARS `issued by` miniCorp.ref(12, 34),
                        owner = AnonymousParty(alice.publicKey)))
            command(miniCorp.publicKey, Cash.Commands.Issue())
            this.verifies()
        }
    }

    @Test
    fun generateIssueRaw() {
        // Test generation works.
        val tx: WireTransaction = TransactionBuilder(notary = null).apply {
            Cash().generateIssue(this, 100.DOLLARS `issued by` miniCorp.ref(12, 34), owner = AnonymousParty(alice.publicKey), notary = dummyNotary.party)
        }.toWireTransactionNew(miniCorpServices)
        assertTrue(tx.inputs.isEmpty())
        val s = tx.outputsOfType<Cash.State>().single()
        assertEquals(100.DOLLARS `issued by` miniCorp.ref(12, 34), s.amount)
        assertEquals(miniCorp.party as AbstractParty, s.amount.token.issuer.party)
        assertEquals(AnonymousParty(alice.publicKey), s.owner)
        assertTrue(tx.commands[0].value is Cash.Commands.Issue)
        assertEquals(miniCorp.publicKey, tx.commands[0].signers[0])
    }

    @Test
    fun generateIssueFromAmount() {
        // Test issuance from an issued amount
        val amount = 100.DOLLARS `issued by` miniCorp.ref(12, 34)
        val tx: WireTransaction = TransactionBuilder(notary = null).apply {
            Cash().generateIssue(this, amount, owner = AnonymousParty(alice.publicKey), notary = dummyNotary.party)
        }.toWireTransactionNew(miniCorpServices)
        assertTrue(tx.inputs.isEmpty())
        assertEquals(tx.outputs[0], tx.outputs[0])
    }

    @Test
    fun `extended issue examples`() {
        // We can consume $1000 in a transaction and output $2000 as long as it's signed by an issuer.
        transaction {
            attachment(Cash.PROGRAM_ID)
            input(Cash.PROGRAM_ID, issuerInState)
            output(Cash.PROGRAM_ID, inState.copy(amount = inState.amount * 2))
            // Move fails: not allowed to summon money.
            tweak {
                command(alice.publicKey, Cash.Commands.Move())
                this `fails with` "the amounts balance"
            }

            // Issue works.
            tweak {
                command(megaCorp.publicKey, Cash.Commands.Issue())
                this.verifies()
            }
        }

        // Can't use an issue command to lower the amount.
        transaction {
            attachment(Cash.PROGRAM_ID)
            input(Cash.PROGRAM_ID, inState)
            output(Cash.PROGRAM_ID, inState.copy(amount = inState.amount.splitEvenly(2).first()))
            command(megaCorp.publicKey, Cash.Commands.Issue())
            this `fails with` "output values sum to more than the inputs"
        }

        // Can't have an issue command that doesn't actually issue money.
        transaction {
            attachment(Cash.PROGRAM_ID)
            input(Cash.PROGRAM_ID, inState)
            output(Cash.PROGRAM_ID, inState)
            command(megaCorp.publicKey, Cash.Commands.Issue())
            this `fails with` "output values sum to more than the inputs"
        }

        // Can't have any other commands if we have an issue command (because the issue command overrules them)
        transaction {
            attachment(Cash.PROGRAM_ID)
            input(Cash.PROGRAM_ID, inState)
            output(Cash.PROGRAM_ID, inState.copy(amount = inState.amount * 2))
            command(megaCorp.publicKey, Cash.Commands.Issue())
            tweak {
                command(megaCorp.publicKey, Cash.Commands.Issue())
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
        // Issue some cash
        var ptx = TransactionBuilder(dummyNotary.party)

        Cash().generateIssue(ptx, 100.DOLLARS `issued by` miniCorp.ref(12, 34), owner = miniCorp.party, notary = dummyNotary.party)
        val tx = miniCorpServices.signInitialTransaction(ptx)

        // Include the previously issued cash in a new issuance command
        ptx = TransactionBuilder(dummyNotary.party)
        ptx.addInputState(tx.tx.outRef<Cash.State>(0))
        Cash().generateIssue(ptx, 100.DOLLARS `issued by` miniCorp.ref(12, 34), owner = miniCorp.party, notary = dummyNotary.party)
    }

    @Test
    fun testMergeSplit() {
        // Splitting value works.
        transaction {
            attachment(Cash.PROGRAM_ID)
            command(alice.publicKey, Cash.Commands.Move())
            tweak {
                input(Cash.PROGRAM_ID, inState)
                val splits4 = inState.amount.splitEvenly(4)
                for (i in 0..3) output(Cash.PROGRAM_ID, inState.copy(amount = splits4[i]))
                this.verifies()
            }
            // Merging 4 inputs into 2 outputs works.
            tweak {
                val splits2 = inState.amount.splitEvenly(2)
                val splits4 = inState.amount.splitEvenly(4)
                for (i in 0..3) input(Cash.PROGRAM_ID, inState.copy(amount = splits4[i]))
                for (i in 0..1) output(Cash.PROGRAM_ID, inState.copy(amount = splits2[i]))
                this.verifies()
            }
            // Merging 2 inputs into 1 works.
            tweak {
                val splits2 = inState.amount.splitEvenly(2)
                for (i in 0..1) input(Cash.PROGRAM_ID, inState.copy(amount = splits2[i]))
                output(Cash.PROGRAM_ID, inState)
                this.verifies()
            }
        }
    }

    @Test
    fun zeroSizedValues() {
        transaction {
            attachment(Cash.PROGRAM_ID)
            input(Cash.PROGRAM_ID, inState)
            input(Cash.PROGRAM_ID, inState.copy(amount = 0.DOLLARS `issued by` defaultIssuer))
            command(alice.publicKey, Cash.Commands.Move())
            this `fails with` "zero sized inputs"
        }
        transaction {
            attachment(Cash.PROGRAM_ID)
            input(Cash.PROGRAM_ID, inState)
            output(Cash.PROGRAM_ID, inState)
            output(Cash.PROGRAM_ID, inState.copy(amount = 0.DOLLARS `issued by` defaultIssuer))
            command(alice.publicKey, Cash.Commands.Move())
            this `fails with` "zero sized outputs"
        }
    }

    @Test
    fun trivialMismatches() {
        // Can't change issuer.
        transaction {
            attachment(Cash.PROGRAM_ID)
            input(Cash.PROGRAM_ID, inState)
            output(Cash.PROGRAM_ID, outState issuedBy miniCorp.party)
            command(alice.publicKey, Cash.Commands.Move())
            this `fails with` "the amounts balance"
        }
        // Can't change deposit reference when splitting.
        transaction {
            attachment(Cash.PROGRAM_ID)
            val splits2 = inState.amount.splitEvenly(2)
            input(Cash.PROGRAM_ID, inState)
            for (i in 0..1) output(Cash.PROGRAM_ID, outState.copy(amount = splits2[i]).editDepositRef(i.toByte()))
            command(alice.publicKey, Cash.Commands.Move())
            this `fails with` "the amounts balance"
        }
        // Can't mix currencies.
        transaction {
            attachment(Cash.PROGRAM_ID)
            input(Cash.PROGRAM_ID, inState)
            output(Cash.PROGRAM_ID, outState.copy(amount = 800.DOLLARS `issued by` defaultIssuer))
            output(Cash.PROGRAM_ID, outState.copy(amount = 200.POUNDS `issued by` defaultIssuer))
            command(alice.publicKey, Cash.Commands.Move())
            this `fails with` "the amounts balance"
        }
        transaction {
            attachment(Cash.PROGRAM_ID)
            input(Cash.PROGRAM_ID, inState)
            input(Cash.PROGRAM_ID,
                inState.copy(
                        amount = 150.POUNDS `issued by` defaultIssuer,
                        owner = AnonymousParty(bob.publicKey)))
            output(Cash.PROGRAM_ID, outState.copy(amount = 1150.DOLLARS `issued by` defaultIssuer))
            command(alice.publicKey, Cash.Commands.Move())
            this `fails with` "the amounts balance"
        }
        // Can't have superfluous input states from different issuers.
        transaction {
            attachment(Cash.PROGRAM_ID)
            input(Cash.PROGRAM_ID, inState)
            input(Cash.PROGRAM_ID, inState issuedBy miniCorp.party)
            output(Cash.PROGRAM_ID, outState)
            command(alice.publicKey, Cash.Commands.Move())
            this `fails with` "the amounts balance"
        }
        // Can't combine two different deposits at the same issuer.
        transaction {
            attachment(Cash.PROGRAM_ID)
            input(Cash.PROGRAM_ID, inState)
            input(Cash.PROGRAM_ID, inState.editDepositRef(3))
            output(Cash.PROGRAM_ID, outState.copy(amount = inState.amount * 2).editDepositRef(3))
            command(alice.publicKey, Cash.Commands.Move())
            this `fails with` "for reference [01]"
        }
    }

    @Test
    fun exitLedger() {
        // Single input/output straightforward case.
        transaction {
            attachment(Cash.PROGRAM_ID)
            input(Cash.PROGRAM_ID, issuerInState)
            output(Cash.PROGRAM_ID, issuerInState.copy(amount = issuerInState.amount - (200.DOLLARS `issued by` defaultIssuer)))
            tweak {
                command(megaCorp.publicKey, Cash.Commands.Exit(100.DOLLARS `issued by` defaultIssuer))
                command(megaCorp.publicKey, Cash.Commands.Move())
                this `fails with` "the amounts balance"
            }

            tweak {
                command(megaCorp.publicKey, Cash.Commands.Exit(200.DOLLARS `issued by` defaultIssuer))
                this `fails with` "required net.corda.finance.contracts.asset.Cash.Commands.Move command"

                tweak {
                    command(megaCorp.publicKey, Cash.Commands.Move())
                    this.verifies()
                }
            }
        }
    }

    @Test
    fun `exit ledger with multiple issuers`() {
        // Multi-issuer case.
        transaction {
            attachment(Cash.PROGRAM_ID)
            input(Cash.PROGRAM_ID, issuerInState)
            input(Cash.PROGRAM_ID, issuerInState.copy(owner = miniCorp.party) issuedBy miniCorp.party)
            output(Cash.PROGRAM_ID, issuerInState.copy(amount = issuerInState.amount - (200.DOLLARS `issued by` defaultIssuer)) issuedBy miniCorp.party)
            output(Cash.PROGRAM_ID, issuerInState.copy(owner = miniCorp.party, amount = issuerInState.amount - (200.DOLLARS `issued by` defaultIssuer)))
            command(listOf(megaCorp.publicKey, miniCorp.publicKey), Cash.Commands.Move())
            this `fails with` "the amounts balance"
            command(megaCorp.publicKey, Cash.Commands.Exit(200.DOLLARS `issued by` defaultIssuer))
            this `fails with` "the amounts balance"
            command(miniCorp.publicKey, Cash.Commands.Exit(200.DOLLARS `issued by` miniCorp.ref(1)))
            this.verifies()
        }
    }

    @Test
    fun `exit cash not held by its issuer`() {
        // Single input/output straightforward case.
        transaction {
            attachment(Cash.PROGRAM_ID)
            input(Cash.PROGRAM_ID, inState)
            output(Cash.PROGRAM_ID, outState.copy(amount = inState.amount - (200.DOLLARS `issued by` defaultIssuer)))
            command(megaCorp.publicKey, Cash.Commands.Exit(200.DOLLARS `issued by` defaultIssuer))
            command(alice.publicKey, Cash.Commands.Move())
            this `fails with` "the amounts balance"
        }
    }

    @Test
    fun multiIssuer() {
        transaction {
            attachment(Cash.PROGRAM_ID)
            // Gather 2000 dollars from two different issuers.
            input(Cash.PROGRAM_ID, inState)
            input(Cash.PROGRAM_ID, inState issuedBy miniCorp.party)
            command(alice.publicKey, Cash.Commands.Move())
            // Can't merge them together.
            tweak {
                output(Cash.PROGRAM_ID, inState.copy(owner = AnonymousParty(bob.publicKey), amount = 2000.DOLLARS `issued by` defaultIssuer))
                this `fails with` "the amounts balance"
            }
            // Missing MiniCorp deposit
            tweak {
                output(Cash.PROGRAM_ID, inState.copy(owner = AnonymousParty(bob.publicKey)))
                output(Cash.PROGRAM_ID, inState.copy(owner = AnonymousParty(bob.publicKey)))
                this `fails with` "the amounts balance"
            }

            // This works.
            output(Cash.PROGRAM_ID, inState.copy(owner = AnonymousParty(bob.publicKey)))
            output(Cash.PROGRAM_ID, inState.copy(owner = AnonymousParty(bob.publicKey)) issuedBy miniCorp.party)
            this.verifies()
        }
    }

    @Test
    fun multiCurrency() {
        // Check we can do an atomic currency trade tx.
        transaction {
            attachment(Cash.PROGRAM_ID)
            val pounds = Cash.State(658.POUNDS `issued by` miniCorp.ref(3, 4, 5), AnonymousParty(bob.publicKey))
            input(Cash.PROGRAM_ID, inState ownedBy AnonymousParty(alice.publicKey))
            input(Cash.PROGRAM_ID, pounds)
            output(Cash.PROGRAM_ID, inState ownedBy AnonymousParty(bob.publicKey))
            output(Cash.PROGRAM_ID, pounds ownedBy AnonymousParty(alice.publicKey))
            command(listOf(alice.publicKey, bob.publicKey), Cash.Commands.Move())
            this.verifies()
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Spend tx generation

    private fun makeCash(amount: Amount<Currency>, issuer: AbstractParty, depositRef: Byte = 1) =
            StateAndRef(
                    TransactionState(Cash.State(amount `issued by` issuer.ref(depositRef), ourIdentity), Cash.PROGRAM_ID, dummyNotary.party),
                    StateRef(SecureHash.randomSHA256(), Random().nextInt(32))
            )

    /**
     * Generate an exit transaction, removing some amount of cash from the ledger.
     */
    private fun makeExit(serviceHub: ServiceHub, amount: Amount<Currency>, issuer: Party, depositRef: Byte = 1): WireTransaction {
        val tx = TransactionBuilder(dummyNotary.party)
        val payChangeTo = serviceHub.keyManagementService.freshKeyAndCert(miniCorp.identity, false).party
        Cash().generateExit(tx, Amount(amount.quantity, Issued(issuer.ref(depositRef), amount.token)), cashStates, payChangeTo)
        return tx.toWireTransactionNew(serviceHub)
    }

    private fun makeSpend(services: ServiceHub, amount: Amount<Currency>, dest: AbstractParty): WireTransaction {
        val ourIdentity = services.myInfo.singleIdentityAndCert()
        val tx = TransactionBuilder(dummyNotary.party)
        database.transaction {
            Cash.generateSpend(services, tx, amount, ourIdentity, dest)
        }
        return tx.toWireTransactionNew(services)
    }

    /**
     * Try exiting an amount which matches a single state.
     */
    @Test
    fun generateSimpleExit() {
        val wtx = makeExit(miniCorpServices, 100.DOLLARS, megaCorp.party, 1)
        assertEquals(cashStates[0].ref, wtx.inputs[0])
        assertEquals(0, wtx.outputs.size)

        val expectedMove = Cash.Commands.Move()
        val expectedExit = Cash.Commands.Exit(Amount(10000, Issued(megaCorp.ref(1), USD)))

        assertEquals(listOf(expectedMove, expectedExit), wtx.commands.map { it.value })
    }

    /**
     * Try exiting an amount smaller than the smallest available input state, and confirm change is generated correctly.
     */
    @Test
    fun generatePartialExit() {
        val wtx = makeExit(miniCorpServices, 50.DOLLARS, megaCorp.party, 1)
        val actualInput = wtx.inputs.single()
        // Filter the available inputs and confirm exactly one has been used
        val expectedInputs = cashStates.filter { it.ref == actualInput }
        assertEquals(1, expectedInputs.size)
        val inputState = expectedInputs.single()
        val actualChange = wtx.outputs.single().data as Cash.State
        val expectedChangeAmount = inputState.state.data.amount.quantity - 50.DOLLARS.quantity
        val expectedChange = cashStates[0].state.data.copy(amount = cashStates[0].state.data.amount.copy(quantity = expectedChangeAmount), owner = actualChange.owner)
        assertEquals(expectedChange, wtx.getOutput(0))
    }

    /**
     * Try exiting a currency we don't have.
     */
    @Test
    fun generateAbsentExit() {
        assertFailsWith<InsufficientBalanceException> { makeExit(miniCorpServices, 100.POUNDS, megaCorp.party, 1) }
    }

    /**
     * Try exiting with a reference mis-match.
     */
    @Test
    fun generateInvalidReferenceExit() {
        assertFailsWith<InsufficientBalanceException> { makeExit(miniCorpServices, 100.POUNDS, megaCorp.party, 2) }
    }

    /**
     * Try exiting an amount greater than the maximum available.
     */
    @Test
    fun generateInsufficientExit() {
        assertFailsWith<InsufficientBalanceException> { makeExit(miniCorpServices, 1000.DOLLARS, megaCorp.party, 1) }
    }

    /**
     * Try exiting for an owner with no states
     */
    @Test
    fun generateOwnerWithNoStatesExit() {
        assertFailsWith<InsufficientBalanceException> { makeExit(miniCorpServices, 100.POUNDS, charlie.party, 1) }
    }

    /**
     * Try exiting when vault is empty
     */
    @Test
    fun generateExitWithEmptyVault() {
        assertFailsWith<IllegalArgumentException> {
            val tx = TransactionBuilder(dummyNotary.party)
            Cash().generateExit(tx, Amount(100, Issued(charlie.ref(1), GBP)), emptyList(), ourIdentity)
        }
    }

    @Test
    fun generateSimpleDirectSpend() {
        val wtx =
                database.transaction {
                    makeSpend(ourServices, 100.DOLLARS, miniCorpAnonymised)
                }
        database.transaction {
            val vaultState = vaultStatesUnconsumed.elementAt(0)
            assertEquals(vaultState.ref, wtx.inputs[0])
            assertEquals(vaultState.state.data.copy(owner = miniCorpAnonymised), wtx.getOutput(0))
            assertEquals(ourIdentity.owningKey, wtx.commands.single { it.value is Cash.Commands.Move }.signers[0])
        }
    }

    @Test
    fun generateSimpleSpendWithParties() {
        database.transaction {
            val tx = TransactionBuilder(dummyNotary.party)
            Cash.generateSpend(ourServices, tx, 80.DOLLARS, ourServices.myInfo.singleIdentityAndCert(), alice.party, setOf(miniCorp.party))

            assertEquals(vaultStatesUnconsumed.elementAt(2).ref, tx.inputStates()[0])
        }
    }

    @Test
    fun generateSimpleSpendWithChange() {
        val wtx =
                database.transaction {
                    makeSpend(ourServices, 10.DOLLARS, miniCorpAnonymised)
                }
        database.transaction {
            val vaultState = vaultStatesUnconsumed.elementAt(0)
            val changeAmount = 90.DOLLARS `issued by` defaultIssuer
            val likelyChangeState = wtx.outputs.map(TransactionState<*>::data).single { state ->
                if (state is Cash.State) {
                    state.amount == changeAmount
                } else {
                    false
                }
            }
            val changeOwner = (likelyChangeState as Cash.State).owner
            assertEquals(1, ourServices.keyManagementService.filterMyKeys(setOf(changeOwner.owningKey)).toList().size)
            assertEquals(vaultState.ref, wtx.inputs[0])
            assertEquals(vaultState.state.data.copy(owner = miniCorpAnonymised, amount = 10.DOLLARS `issued by` defaultIssuer), wtx.outputs[0].data)
            assertEquals(vaultState.state.data.copy(amount = changeAmount, owner = changeOwner), wtx.outputs[1].data)
            assertEquals(ourIdentity.owningKey, wtx.commands.single { it.value is Cash.Commands.Move }.signers[0])
        }
    }

    @Test
    fun generateSpendWithTwoInputs() {
        val wtx =
                database.transaction {
                    makeSpend(ourServices, 500.DOLLARS, miniCorpAnonymised)
                }
        database.transaction {
            val vaultState0 = vaultStatesUnconsumed.elementAt(0)
            val vaultState1 = vaultStatesUnconsumed.elementAt(1)
            assertEquals(vaultState0.ref, wtx.inputs[0])
            assertEquals(vaultState1.ref, wtx.inputs[1])
            assertEquals(vaultState0.state.data.copy(owner = miniCorpAnonymised, amount = 500.DOLLARS `issued by` defaultIssuer), wtx.getOutput(0))
            assertEquals(ourIdentity.owningKey, wtx.commands.single { it.value is Cash.Commands.Move }.signers[0])
        }
    }

    @Test
    fun generateSpendMixedDeposits() {
        val wtx =
                database.transaction {
                    val wtx = makeSpend(ourServices, 580.DOLLARS, miniCorpAnonymised)
                    assertEquals(3, wtx.inputs.size)
                    wtx
                }
        database.transaction {
            val vaultState0: StateAndRef<Cash.State> = vaultStatesUnconsumed.elementAt(0)
            val vaultState1: StateAndRef<Cash.State> = vaultStatesUnconsumed.elementAt(1)
            val vaultState2: StateAndRef<Cash.State> = vaultStatesUnconsumed.elementAt(2)
            assertEquals(vaultState0.ref, wtx.inputs[0])
            assertEquals(vaultState1.ref, wtx.inputs[1])
            assertEquals(vaultState2.ref, wtx.inputs[2])
            assertEquals(vaultState0.state.data.copy(owner = miniCorpAnonymised, amount = 500.DOLLARS `issued by` defaultIssuer), wtx.outputs[1].data)
            assertEquals(vaultState2.state.data.copy(owner = miniCorpAnonymised), wtx.outputs[0].data)
            assertEquals(ourIdentity.owningKey, wtx.commands.single { it.value is Cash.Commands.Move }.signers[0])
        }
    }

    @Test
    fun generateSpendInsufficientBalance() {
        database.transaction {

            val e: InsufficientBalanceException = assertFailsWith("balance") {
                makeSpend(ourServices, 1000.DOLLARS, miniCorpAnonymised)
            }
            assertEquals((1000 - 580).DOLLARS, e.amountMissing)

            assertFailsWith(InsufficientBalanceException::class) {
                makeSpend(ourServices, 81.SWISS_FRANCS, miniCorpAnonymised)
            }
        }
    }

    /**
     * Confirm that aggregation of states is correctly modelled.
     */
    @Test
    fun aggregation() {
        val fiveThousandDollarsFromMega = Cash.State(5000.DOLLARS `issued by` megaCorp.ref(2), megaCorp.party)
        val twoThousandDollarsFromMega = Cash.State(2000.DOLLARS `issued by` megaCorp.ref(2), miniCorp.party)
        val oneThousandDollarsFromMini = Cash.State(1000.DOLLARS `issued by` miniCorp.ref(3), megaCorp.party)

        // Obviously it must be possible to aggregate states with themselves
        assertEquals(fiveThousandDollarsFromMega.amount.token, fiveThousandDollarsFromMega.amount.token)

        // Owner is not considered when calculating whether it is possible to aggregate states
        assertEquals(fiveThousandDollarsFromMega.amount.token, twoThousandDollarsFromMega.amount.token)

        // States cannot be aggregated if the deposit differs
        assertNotEquals(fiveThousandDollarsFromMega.amount.token, oneThousandDollarsFromMini.amount.token)
        assertNotEquals(twoThousandDollarsFromMega.amount.token, oneThousandDollarsFromMini.amount.token)

        // States cannot be aggregated if the currency differs
        assertNotEquals(oneThousandDollarsFromMini.amount.token,
                Cash.State(1000.POUNDS `issued by` miniCorp.ref(3), megaCorp.party).amount.token)

        // States cannot be aggregated if the reference differs
        assertNotEquals(fiveThousandDollarsFromMega.amount.token, (fiveThousandDollarsFromMega withDeposit defaultIssuer).amount.token)
        assertNotEquals((fiveThousandDollarsFromMega withDeposit defaultIssuer).amount.token, fiveThousandDollarsFromMega.amount.token)
    }

    @Test
    fun `summing by owner`() {
        val states = listOf(
                Cash.State(1000.DOLLARS `issued by` defaultIssuer, miniCorp.party),
                Cash.State(2000.DOLLARS `issued by` defaultIssuer, megaCorp.party),
                Cash.State(4000.DOLLARS `issued by` defaultIssuer, megaCorp.party)
        )
        assertEquals(6000.DOLLARS `issued by` defaultIssuer, states.sumCashBy(megaCorp.party))
    }

    @Test(expected = UnsupportedOperationException::class)
    fun `summing by owner throws`() {
        val states = listOf(
                Cash.State(2000.DOLLARS `issued by` defaultIssuer, megaCorp.party),
                Cash.State(4000.DOLLARS `issued by` defaultIssuer, megaCorp.party)
        )
        states.sumCashBy(miniCorp.party)
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
                Cash.State(1000.DOLLARS `issued by` defaultIssuer, megaCorp.party),
                Cash.State(2000.DOLLARS `issued by` defaultIssuer, megaCorp.party),
                Cash.State(4000.DOLLARS `issued by` defaultIssuer, megaCorp.party)
        )
        // Test that summing everything produces the total number of dollars
        val expected = 7000.DOLLARS `issued by` defaultIssuer
        val actual = states.sumCash()
        assertEquals(expected, actual)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `summing multiple currencies`() {
        val states = listOf(
                Cash.State(1000.DOLLARS `issued by` defaultIssuer, megaCorp.party),
                Cash.State(4000.POUNDS `issued by` defaultIssuer, megaCorp.party)
        )
        // Test that summing everything fails because we're mixing units
        states.sumCash()
    }

    // Double spend.
    @Test
    fun chainCashDoubleSpendFailsWith() {
        MockServices(megaCorp).ledger(dummyNotary.party) {
            unverifiedTransaction {
                attachment(Cash.PROGRAM_ID)
                output(Cash.PROGRAM_ID, "MEGA_CORP cash",
                    Cash.State(
                            amount = 1000.DOLLARS `issued by` megaCorp.ref(1, 1),
                            owner = megaCorp.party))
            }

            transaction {
                attachment(Cash.PROGRAM_ID)
                input("MEGA_CORP cash")
                output(Cash.PROGRAM_ID, "MEGA_CORP cash 2", "MEGA_CORP cash".output<Cash.State>().copy(owner = AnonymousParty(alice.publicKey)))
                command(megaCorp.publicKey, Cash.Commands.Move())
                this.verifies()
            }

            tweak {
                transaction {
                    attachment(Cash.PROGRAM_ID)
                    input("MEGA_CORP cash")
                    // We send it to another pubkey so that the transaction is not identical to the previous one
                    output(Cash.PROGRAM_ID, "MEGA_CORP cash 3", "MEGA_CORP cash".output<Cash.State>().copy(owner = alice.party))
                    command(megaCorp.publicKey, Cash.Commands.Move())
                    this.verifies()
                }
                this.fails()
            }

            this.verifies()
        }
    }

    @Test
    fun multiSpend() {
        val tx = TransactionBuilder(dummyNotary.party)
        database.transaction {
            val payments = listOf(
                    PartyAndAmount(miniCorpAnonymised, 400.DOLLARS),
                    PartyAndAmount(charlie.party.anonymise(), 150.DOLLARS)
            )
            Cash.generateSpend(ourServices, tx, payments, ourServices.myInfo.singleIdentityAndCert())
        }
        val wtx = tx.toWireTransactionNew(ourServices)
        fun out(i: Int) = wtx.getOutput(i) as Cash.State
        assertEquals(4, wtx.outputs.size)
        assertEquals(80.DOLLARS, out(0).amount.withoutIssuer())
        assertEquals(320.DOLLARS, out(1).amount.withoutIssuer())
        assertEquals(150.DOLLARS, out(2).amount.withoutIssuer())
        assertEquals(30.DOLLARS, out(3).amount.withoutIssuer())
        assertEquals(miniCorp.party, out(0).amount.token.issuer.party)
        assertEquals(megaCorp.party, out(1).amount.token.issuer.party)
        assertEquals(megaCorp.party, out(2).amount.token.issuer.party)
        assertEquals(megaCorp.party, out(3).amount.token.issuer.party)
    }
}

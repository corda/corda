package net.corda.node.services.vault

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.InsufficientBalanceException
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.concurrent.transpose
import net.corda.core.internal.packageName
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.finance.*
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.getCashBalance
import net.corda.finance.schemas.CashSchemaV1
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.core.*
import net.corda.testing.internal.LogHelper
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.vault.*
import net.corda.testing.node.MockServices
import net.corda.testing.node.MockServices.Companion.makeTestDatabaseAndMockServices
import net.corda.testing.node.makeTestIdentityService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.fail

// TODO: Move this to the cash contract tests once mock services are further split up.

class VaultWithCashTest {
    private companion object {
        val cordappPackages = listOf("net.corda.testing.internal.vault", "net.corda.finance.contracts.asset", CashSchemaV1::class.packageName, "net.corda.core.contracts")
        val BOB = TestIdentity(BOB_NAME, 80).party
        val dummyCashIssuer = TestIdentity(CordaX500Name("Snake Oil Issuer", "London", "GB"), 10)
        val DUMMY_CASH_ISSUER = dummyCashIssuer.ref(1)
        val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
        val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))
        val DUMMY_NOTARY get() = dummyNotary.party
        val MEGA_CORP get() = megaCorp.party
        val MEGA_CORP_IDENTITY get() = megaCorp.identity
        val MEGA_CORP_KEY get() = megaCorp.keyPair
        val MINI_CORP_IDENTITY get() = miniCorp.identity
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)
    private val servicesKey = generateKeyPair()
    lateinit var services: MockServices
    private lateinit var vaultFiller: VaultFiller
    lateinit var issuerServices: MockServices
    val vaultService: VaultService get() = services.vaultService
    lateinit var database: CordaPersistence
    private lateinit var notaryServices: MockServices
    private lateinit var notary: Party

    @Before
    fun setUp() {
        LogHelper.setLevel(VaultWithCashTest::class)
        val databaseAndServices = makeTestDatabaseAndMockServices(
                cordappPackages,
                makeTestIdentityService(MEGA_CORP_IDENTITY, MINI_CORP_IDENTITY, dummyCashIssuer.identity, dummyNotary.identity),
                TestIdentity(MEGA_CORP.name, servicesKey),
                moreKeys = dummyNotary.keyPair)
        database = databaseAndServices.first
        services = databaseAndServices.second
        vaultFiller = VaultFiller(services, dummyNotary)
        issuerServices = MockServices(cordappPackages, dummyCashIssuer, rigorousMock(), MEGA_CORP_KEY)
        notaryServices = MockServices(cordappPackages, dummyNotary, rigorousMock<IdentityService>())
        notary = notaryServices.myInfo.legalIdentitiesAndCerts.single().party
    }

    @After
    fun tearDown() {
        LogHelper.reset(VaultWithCashTest::class)
        database.close()
    }

    @Test
    fun splits() {
        database.transaction {
            // Fix the PRNG so that we get the same splits every time.
            vaultFiller.fillWithSomeTestCash(100.DOLLARS, issuerServices, 3, DUMMY_CASH_ISSUER)
        }
        database.transaction {
            val w = vaultService.queryBy<Cash.State>().states
            assertEquals(3, w.size)

            val state = w[0].state.data
            assertEquals(30.45.DOLLARS `issued by` DUMMY_CASH_ISSUER, state.amount)
            assertEquals(servicesKey.public, state.owner.owningKey)
            assertEquals(34.70.DOLLARS `issued by` DUMMY_CASH_ISSUER, (w[2].state.data).amount)
            assertEquals(34.85.DOLLARS `issued by` DUMMY_CASH_ISSUER, (w[1].state.data).amount)
        }
    }

    @Test
    fun `issue and spend total correctly and irrelevant ignored`() {
        val megaCorpServices = MockServices(cordappPackages, MEGA_CORP.name, rigorousMock(), MEGA_CORP_KEY)
        val freshKey = services.keyManagementService.freshKey()

        val usefulTX =
                database.transaction {
                    // A tx that sends us money.
                    val usefulBuilder = TransactionBuilder(null)
                    Cash().generateIssue(usefulBuilder, 100.DOLLARS `issued by` MEGA_CORP.ref(1), AnonymousParty(freshKey), DUMMY_NOTARY)
                    megaCorpServices.signInitialTransaction(usefulBuilder)
                }
        database.transaction {
            assertEquals(0.DOLLARS, services.getCashBalance(USD))
            services.recordTransactions(usefulTX)
        }
        val spendTX =
                database.transaction {
                    // A tx that spends our money.
                    val spendTXBuilder = TransactionBuilder(DUMMY_NOTARY)
                    Cash.generateSpend(services, spendTXBuilder, 80.DOLLARS, BOB)
                    val spendPTX = services.signInitialTransaction(spendTXBuilder, freshKey)
                    notaryServices.addSignature(spendPTX)
                }
        database.transaction {
            assertEquals(100.DOLLARS, services.getCashBalance(USD))
        }
        database.transaction {
            // A tx that doesn't send us anything.
            val irrelevantBuilder = TransactionBuilder(DUMMY_NOTARY)
            Cash().generateIssue(irrelevantBuilder, 100.DOLLARS `issued by` MEGA_CORP.ref(1), BOB, DUMMY_NOTARY)

            val irrelevantPTX = megaCorpServices.signInitialTransaction(irrelevantBuilder)
            val irrelevantTX = notaryServices.addSignature(irrelevantPTX)

            services.recordTransactions(irrelevantTX)
        }
        database.transaction {
            assertEquals(100.DOLLARS, services.getCashBalance(USD))
        }
        database.transaction {
            services.recordTransactions(spendTX)
        }
        database.transaction {
            assertEquals(20.DOLLARS, services.getCashBalance(USD))
        }
    }

    @Test
    fun `issue and attempt double spend`() {
        val freshKey = services.keyManagementService.freshKey()
        val criteriaLocked = VaultQueryCriteria(softLockingCondition = QueryCriteria.SoftLockingCondition(QueryCriteria.SoftLockingType.LOCKED_ONLY))

        database.transaction {
            // A tx that sends us money.
            vaultFiller.fillWithSomeTestCash(100.DOLLARS, issuerServices, 10, MEGA_CORP.ref(1), AnonymousParty(freshKey))
            println("Cash balance: ${services.getCashBalance(USD)}")
        }
        database.transaction {
            assertThat(vaultService.queryBy<Cash.State>().states).hasSize(10)
            assertThat(vaultService.queryBy<Cash.State>(criteriaLocked).states).hasSize(0)
        }

        val backgroundExecutor = Executors.newFixedThreadPool(2)
        // 1st tx that spends our money.
        val first = backgroundExecutor.fork {
            database.transaction {
                val txn1Builder = TransactionBuilder(DUMMY_NOTARY)
                Cash.generateSpend(services, txn1Builder, 60.DOLLARS, BOB)
                val ptxn1 = notaryServices.signInitialTransaction(txn1Builder)
                val txn1 = services.addSignature(ptxn1, freshKey)
                println("txn1: ${txn1.id} spent ${((txn1.tx.outputs[0].data) as Cash.State).amount}")
                val unconsumedStates1 = vaultService.queryBy<Cash.State>()
                val consumedStates1 = vaultService.queryBy<Cash.State>(VaultQueryCriteria(status = Vault.StateStatus.CONSUMED))
                val lockedStates1 = vaultService.queryBy<Cash.State>(criteriaLocked).states
                println("""txn1 states:
                                UNCONSUMED: ${unconsumedStates1.totalStatesAvailable} : $unconsumedStates1,
                                CONSUMED: ${consumedStates1.totalStatesAvailable} : $consumedStates1,
                                LOCKED: ${lockedStates1.count()} : $lockedStates1
                    """)
                services.recordTransactions(txn1)
                println("txn1: Cash balance: ${services.getCashBalance(USD)}")
                val unconsumedStates2 = vaultService.queryBy<Cash.State>()
                val consumedStates2 = vaultService.queryBy<Cash.State>(VaultQueryCriteria(status = Vault.StateStatus.CONSUMED))
                val lockedStates2 = vaultService.queryBy<Cash.State>(criteriaLocked).states
                println("""txn1 states:
                                UNCONSUMED: ${unconsumedStates2.totalStatesAvailable} : $unconsumedStates2,
                                CONSUMED: ${consumedStates2.totalStatesAvailable} : $consumedStates2,
                                LOCKED: ${lockedStates2.count()} : $lockedStates2
                    """)
                txn1
            }
            println("txn1 COMMITTED!")
        }

        // 2nd tx that attempts to spend same money
        val second = backgroundExecutor.fork {
            database.transaction {
                val txn2Builder = TransactionBuilder(DUMMY_NOTARY)
                Cash.generateSpend(services, txn2Builder, 80.DOLLARS, BOB)
                val ptxn2 = notaryServices.signInitialTransaction(txn2Builder)
                val txn2 = services.addSignature(ptxn2, freshKey)
                println("txn2: ${txn2.id} spent ${((txn2.tx.outputs[0].data) as Cash.State).amount}")
                val unconsumedStates1 = vaultService.queryBy<Cash.State>()
                val consumedStates1 = vaultService.queryBy<Cash.State>(VaultQueryCriteria(status = Vault.StateStatus.CONSUMED))
                val lockedStates1 = vaultService.queryBy<Cash.State>(criteriaLocked).states
                println("""txn2 states:
                                UNCONSUMED: ${unconsumedStates1.totalStatesAvailable} : $unconsumedStates1,
                                CONSUMED: ${consumedStates1.totalStatesAvailable} : $consumedStates1,
                                LOCKED: ${lockedStates1.count()} : $lockedStates1
                    """)
                services.recordTransactions(txn2)
                println("txn2: Cash balance: ${services.getCashBalance(USD)}")
                val unconsumedStates2 = vaultService.queryBy<Cash.State>()
                val consumedStates2 = vaultService.queryBy<Cash.State>()
                val lockedStates2 = vaultService.queryBy<Cash.State>(criteriaLocked).states
                println("""txn2 states:
                                UNCONSUMED: ${unconsumedStates2.totalStatesAvailable} : $unconsumedStates2,
                                CONSUMED: ${consumedStates2.totalStatesAvailable} : $consumedStates2,
                                LOCKED: ${lockedStates2.count()} : $lockedStates2
                    """)
                txn2
            }
            println("txn2 COMMITTED!")
        }
        val both = listOf(first, second).transpose()
        try {
            both.getOrThrow()
            fail("Expected insufficient balance.")
        } catch (e: InsufficientBalanceException) {
            assertEquals(0, e.suppressed.size) // One should succeed.
        }
        database.transaction {
            println("Cash balance: ${services.getCashBalance(USD)}")
            assertThat(services.getCashBalance(USD)).isIn(DOLLARS(20), DOLLARS(40))
        }
    }

    @Test
    fun `branching LinearStates fails to verify`() {
        database.transaction {
            val freshKey = services.keyManagementService.freshKey()
            val freshIdentity = AnonymousParty(freshKey)
            val linearId = UniqueIdentifier()

            // Issue a linear state
            val dummyIssueBuilder = TransactionBuilder(notary = notary).apply {
                addOutputState(DummyLinearContract.State(linearId = linearId, participants = listOf(freshIdentity)), DUMMY_LINEAR_CONTRACT_PROGRAM_ID)
                addOutputState(DummyLinearContract.State(linearId = linearId, participants = listOf(freshIdentity)), DUMMY_LINEAR_CONTRACT_PROGRAM_ID)
                addCommand(dummyCommand(notary!!.owningKey))
            }
            val dummyIssue = notaryServices.signInitialTransaction(dummyIssueBuilder)

            assertThatThrownBy {
                dummyIssue.toLedgerTransaction(services).verify()
            }
        }
    }

    @Test
    fun `sequencing LinearStates works`() {
        val freshKey = services.keyManagementService.freshKey()
        val freshIdentity = AnonymousParty(freshKey)
        val linearId = UniqueIdentifier()

        val dummyIssue =
                database.transaction {
                    // Issue a linear state
                    val dummyIssueBuilder = TransactionBuilder(notary = DUMMY_NOTARY)
                            .addOutputState(DummyLinearContract.State(linearId = linearId, participants = listOf(freshIdentity)), DUMMY_LINEAR_CONTRACT_PROGRAM_ID)
                            .addCommand(dummyCommand(notary.owningKey))
                    val dummyIssuePtx = notaryServices.signInitialTransaction(dummyIssueBuilder)
                    val dummyIssue = services.addSignature(dummyIssuePtx)

                    dummyIssue.toLedgerTransaction(services).verify()

                    services.recordTransactions(dummyIssue)
                    dummyIssue
                }
        database.transaction {
            assertThat(vaultService.queryBy<DummyLinearContract.State>().states).hasSize(1)

            // Move the same state
            val dummyMoveBuilder = TransactionBuilder(notary = DUMMY_NOTARY)
                    .addOutputState(DummyLinearContract.State(linearId = linearId, participants = listOf(freshIdentity)), DUMMY_LINEAR_CONTRACT_PROGRAM_ID)
                    .addInputState(dummyIssue.tx.outRef<LinearState>(0))
                    .addCommand(dummyCommand(notary.owningKey))

            val dummyMove = notaryServices.signInitialTransaction(dummyMoveBuilder)

            dummyIssue.toLedgerTransaction(services).verify()

            services.recordTransactions(dummyMove)
        }
        database.transaction {
            assertThat(vaultService.queryBy<DummyLinearContract.State>().states).hasSize(1)
        }
    }

    @Test
    fun `spending cash in vault of mixed state types works`() {

        val freshKey = services.keyManagementService.freshKey()
        database.transaction {
            vaultFiller.fillWithSomeTestCash(100.DOLLARS, issuerServices, 3, DUMMY_CASH_ISSUER, AnonymousParty(freshKey))
            vaultFiller.fillWithSomeTestCash(100.SWISS_FRANCS, issuerServices, 2, DUMMY_CASH_ISSUER)
            vaultFiller.fillWithSomeTestCash(100.POUNDS, issuerServices, 1, DUMMY_CASH_ISSUER)
        }
        database.transaction {
            val cash = vaultService.queryBy<Cash.State>().states
            cash.forEach { println(it.state.data.amount) }
        }
        database.transaction {
            vaultFiller.fillWithSomeTestDeals(listOf("123", "456", "789"), issuerServices)
        }
        database.transaction {
            val deals = vaultService.queryBy<DummyDealContract.State>().states
            deals.forEach { println(it.state.data.linearId.externalId!!) }
        }

        database.transaction {
            // A tx that spends our money.
            val spendTXBuilder = TransactionBuilder(DUMMY_NOTARY)
            Cash.generateSpend(services, spendTXBuilder, 80.DOLLARS, BOB)
            val spendPTX = notaryServices.signInitialTransaction(spendTXBuilder)
            val spendTX = services.addSignature(spendPTX, freshKey)
            services.recordTransactions(spendTX)
        }
        database.transaction {
            val consumedStates = vaultService.queryBy<ContractState>(VaultQueryCriteria(status = Vault.StateStatus.CONSUMED)).states
            assertEquals(3, consumedStates.count())

            val unconsumedStates = vaultService.queryBy<ContractState>().states
            assertEquals(7, unconsumedStates.count())
        }
    }

    @Test
    fun `consuming multiple contract state types`() {

        val freshKey = services.keyManagementService.freshKey()
        val freshIdentity = AnonymousParty(freshKey)
        database.transaction {
            vaultFiller.fillWithSomeTestDeals(listOf("123", "456", "789"), issuerServices)
        }
        val deals =
                database.transaction {
                    vaultService.queryBy<DummyDealContract.State>().states
                }
        database.transaction {
            vaultFiller.fillWithSomeTestLinearStates(3)
        }
        database.transaction {
            val linearStates = vaultService.queryBy<DummyLinearContract.State>().states
            linearStates.forEach { println(it.state.data.linearId) }

            // Create a txn consuming different contract types
            val dummyMoveBuilder = TransactionBuilder(notary = notary).apply {
                addOutputState(DummyLinearContract.State(participants = listOf(freshIdentity)), DUMMY_LINEAR_CONTRACT_PROGRAM_ID)
                addOutputState(DummyDealContract.State(ref = "999", participants = listOf(freshIdentity)), DUMMY_DEAL_PROGRAM_ID)
                addInputState(linearStates.first())
                addInputState(deals.first())
                addCommand(dummyCommand(notary!!.owningKey))
            }

            val dummyMove = notaryServices.signInitialTransaction(dummyMoveBuilder)
            dummyMove.toLedgerTransaction(services).verify()
            services.recordTransactions(dummyMove)
        }
        database.transaction {
            val consumedStates = vaultService.queryBy<ContractState>(VaultQueryCriteria(status = Vault.StateStatus.CONSUMED)).states
            assertEquals(2, consumedStates.count())

            val unconsumedStates = vaultService.queryBy<ContractState>().states
            assertEquals(6, unconsumedStates.count())
        }
    }
}

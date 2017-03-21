package net.corda.node.services

import io.requery.query.Operator
import net.corda.contracts.asset.Cash
import net.corda.contracts.testing.fillWithSomeTestCash
import net.corda.contracts.testing.fillWithSomeTestDeals
import net.corda.contracts.testing.fillWithSomeTestLinearStates
import net.corda.core.contracts.*
import net.corda.core.crypto.Party
import net.corda.core.crypto.composite
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.node.services.TxWritableStorageService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.unconsumedStates
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.LogHelper
import net.corda.core.utilities.TEST_TX_TIME
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.MEGA_CORP
import net.corda.testing.MEGA_CORP_KEY
import net.corda.testing.MINI_CORP
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Database
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import java.math.BigInteger
import java.security.KeyPair
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertEquals

class NodeVaultServiceTest {
    lateinit var services: MockServices
    val vaultSvc: VaultService get() = services.vaultService
    lateinit var dataSource: Closeable
    lateinit var database: Database

    @Before
    fun setUp() {
        LogHelper.setLevel(NodeVaultService::class)
        val dataSourceProps = makeTestDataSourceProperties()
        val dataSourceAndDatabase = configureDatabase(dataSourceProps)
        dataSource = dataSourceAndDatabase.first
        database = dataSourceAndDatabase.second
        databaseTransaction(database) {
            services = object : MockServices() {
                override val vaultService: VaultService = NodeVaultService(this, dataSourceProps)

                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        storageService.validatedTransactions.addTransaction(stx)
                    }
                    // Refactored to use notifyAll() as we have no other unit test for that method with multiple transactions.
                    vaultSvc.notifyAll(txs.map { it.tx })
                }
            }
        }
    }

    @After
    fun tearDown() {
        dataSource.close()
        LogHelper.reset(NodeVaultService::class)
    }

    @Test
    fun `states not local to instance`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))

            val w1 = vaultSvc.unconsumedStates<Cash.State>()
            assertThat(w1).hasSize(3)

            val originalStorage = services.storageService
            val originalVault = vaultSvc
            val services2 = object : MockServices() {
                override val vaultService: VaultService get() = originalVault

                // We need to be able to find the same transactions as before, too.
                override val storageService: TxWritableStorageService get() = originalStorage

                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        storageService.validatedTransactions.addTransaction(stx)
                        vaultSvc.notify(stx.tx)
                    }
                }
            }

            val w2 = services2.vaultService.unconsumedStates<Cash.State>()
            assertThat(w2).hasSize(3)
        }
    }

    @Test
    fun `states for refs`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))

            val w1 = vaultSvc.unconsumedStates<Cash.State>().toList()
            assertThat(w1).hasSize(3)

            val stateRefs = listOf(w1[1].ref, w1[2].ref)
            val states = vaultSvc.statesForRefs(stateRefs)
            assertThat(states).hasSize(2)
        }
    }

    @Test
    fun addNoteToTransaction() {
        databaseTransaction(database) {

            val freshKey = services.legalIdentityKey

            // Issue a txn to Send us some Money
            val usefulTX = TransactionType.General.Builder(null).apply {
                Cash().generateIssue(this, 100.DOLLARS `issued by` MEGA_CORP.ref(1), freshKey.public.composite, DUMMY_NOTARY)
                signWith(MEGA_CORP_KEY)
            }.toSignedTransaction()

            services.recordTransactions(listOf(usefulTX))

            vaultSvc.addNoteToTransaction(usefulTX.id, "USD Sample Note 1")
            vaultSvc.addNoteToTransaction(usefulTX.id, "USD Sample Note 2")
            vaultSvc.addNoteToTransaction(usefulTX.id, "USD Sample Note 3")
            assertEquals(3, vaultSvc.getTransactionNotes(usefulTX.id).count())

            // Issue more Money (GBP)
            val anotherTX = TransactionType.General.Builder(null).apply {
                Cash().generateIssue(this, 200.POUNDS `issued by` MEGA_CORP.ref(1), freshKey.public.composite, DUMMY_NOTARY)
                signWith(MEGA_CORP_KEY)
            }.toSignedTransaction()

            services.recordTransactions(listOf(anotherTX))

            vaultSvc.addNoteToTransaction(anotherTX.id, "GPB Sample Note 1")
            assertEquals(1, vaultSvc.getTransactionNotes(anotherTX.id).count())
        }
    }

    /**
     * Query API tests
     */
    @Test
    fun `unconsumed states`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123","456","789"))

            val criteria = QueryCriteria() // default is UNCONSUMED
            val states = vaultSvc.queryBy<ContractState>(criteria)
            assertThat(states).hasSize(16)
        }
    }

    val CASH_NOTARY_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(20)) }
    val CASH_NOTARY: Party get() = Party("Notary Service", CASH_NOTARY_KEY.public)

    @Test
    fun `unconsumed states by notary`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, CASH_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123","456","789"))

            val criteria = QueryCriteria(notary = CASH_NOTARY)
            val states = vaultSvc.queryBy<ContractState>(criteria)
            assertThat(states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed states excluding soft locks`() {
        databaseTransaction(database) {

            val issuedStates = services.fillWithSomeTestCash(100.DOLLARS, CASH_NOTARY, 3, 3, Random(0L))
            // NOTE: awaiting merge of Soft Locking PR
//            vaultSvc.softLockReserve(setOf<StateRef>(issuedStates.first(), issuedStates.last()))

            val criteria = QueryCriteria(includeSoftlocks = false)
            val states = vaultSvc.queryBy<ContractState>(criteria)
            assertThat(states).hasSize(1)
        }
    }

    @Test
    fun `unconsumed states recorded between two time intervals`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, CASH_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123","456","789"))

            val start = TEST_TX_TIME
            val end = TEST_TX_TIME.plus(30, ChronoUnit.DAYS)
            val recordedBetweenExpression = QueryCriteria.LogicalExpression(
                    QueryCriteria.TimeInstantType.RECORDED, Operator.BETWEEN, arrayOf(start, end))
            val criteria = QueryCriteria(timeCondition = recordedBetweenExpression)
            val states = vaultSvc.queryBy<ContractState>(criteria)
            assertThat(states).hasSize(3)
        }
    }

    @Test
    fun `states consumed after time`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, CASH_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123","456","789"))

            val asOfDateTime = TEST_TX_TIME
            val consumedAfterExpression = QueryCriteria.LogicalExpression(
                    QueryCriteria.TimeInstantType.CONSUMED, Operator.GREATER_THAN, arrayOf(asOfDateTime))
            val criteria = QueryCriteria(status = Vault.StateStatus.CONSUMED,
                                         timeCondition = consumedAfterExpression)
            val states = vaultSvc.queryBy<ContractState>(criteria)
            assertThat(states).hasSize(3)
        }
    }

    @Test
    fun `consumed states`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(2, UniqueIdentifier("TEST")) // create 2 states with same UID
            services.fillWithSomeTestLinearStates(8)
            services.fillWithSomeTestDeals(listOf("123","456","789"))

//            services.consumeLinearStates(UniqueIdentifier("TEST"))
//            services.consumeDeals("456")
//            services.consumeCash(80.DOLLARS)

            val criteria = QueryCriteria(status = Vault.StateStatus.CONSUMED)
            val states = vaultSvc.queryBy<ContractState>(criteria)
            assertThat(states).hasSize(12)
        }
    }

    @Test
    fun `unconsumed linear heads`() {
        databaseTransaction(database) {

            services.fillWithSomeTestLinearStates(10)

            val criteria = QueryCriteria()
            val states = vaultSvc.queryBy<LinearState>(criteria)
            assertThat(states).hasSize(10)
        }
    }

    @Test
    fun `consumed linear heads`() {
        databaseTransaction(database) {

            services.fillWithSomeTestLinearStates(10)
//            services.consumeLinearStates(8)

            val criteria = QueryCriteria(status = Vault.StateStatus.CONSUMED)
            val states = vaultSvc.queryBy<LinearState>(criteria)
            assertThat(states).hasSize(2)
        }
    }

    @Test
    fun `unconsumed linear heads for linearId`() {
        databaseTransaction(database) {

            val issuedStates = services.fillWithSomeTestLinearStates(10)
            val linearIds = issuedStates.states.map { it.state.data.linearId }.toList()
            val criteria = QueryCriteria(linearId = listOf(linearIds.first(), linearIds.last()) )
            val states = vaultSvc.queryBy<LinearState>(criteria)
            assertThat(states).hasSize(2)
        }
    }

    @Test
    fun `latest unconsumed linear heads for linearId`() {
        databaseTransaction(database) {

            val issuedStates = services.fillWithSomeTestLinearStates(2, UniqueIdentifier("TEST")) // create 2 states with same UID
            services.fillWithSomeTestLinearStates(8)

            val linearIds = issuedStates.states.map { it.state.data.linearId }.toList()
            val criteria = QueryCriteria(linearId = listOf(linearIds.first()),
                                         latestOnly = true)
            val states = vaultSvc.queryBy<LinearState>(criteria)
            assertThat(states).hasSize(1)
        }
    }

    @Test
    fun `latest unconsumed linear heads for state refs`() {
        databaseTransaction(database) {

            val issuedStates = services.fillWithSomeTestLinearStates(2, UniqueIdentifier("TEST")) // create 2 states with same UID
            services.fillWithSomeTestLinearStates(8)
            val stateRefs = issuedStates.states.map { it.ref }.toList()

            val criteria = QueryCriteria(stateRefs = listOf(stateRefs.first(), stateRefs.last()),
                                         latestOnly = true)
            val states = vaultSvc.queryBy<LinearState>(criteria)
            assertThat(states).hasSize(2)
        }
    }

    @Test
    fun `unconsumed deals`() {
        databaseTransaction(database) {

            services.fillWithSomeTestDeals(listOf("123","456","789"))

            val criteria = QueryCriteria()
            val states = vaultSvc.queryBy<DealState>(criteria)
            assertThat(states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed deals for ref`() {
        databaseTransaction(database) {

            services.fillWithSomeTestDeals(listOf("123","456","789"))

            val criteria = QueryCriteria(dealRef = listOf("456"))
            val states = vaultSvc.queryBy<DealState>(criteria)
            assertThat(states).hasSize(1)
        }
    }

    @Test
    fun `latest unconsumed deals for ref`() {
        databaseTransaction(database) {

            services.fillWithSomeTestLinearStates(2, UniqueIdentifier("TEST"))
            services.fillWithSomeTestDeals(listOf("456"), 3)        // create 3 revisions with same ID
            services.fillWithSomeTestDeals(listOf("123", "789"))

            val criteria = QueryCriteria(dealRef = listOf("456"), latestOnly = true)
            val states = vaultSvc.queryBy<DealState>(criteria)
            assertThat(states).hasSize(1)
        }
    }

    @Test
    fun `latest unconsumed deals with party`() {
        databaseTransaction(database) {

            services.fillWithSomeTestLinearStates(2, UniqueIdentifier("TEST"))
            services.fillWithSomeTestDeals(listOf("456"), 3)        // specify party
            services.fillWithSomeTestDeals(listOf("123", "789"))

            val criteria = QueryCriteria(dealParties = listOf(MEGA_CORP, MINI_CORP))
            val states = vaultSvc.queryBy<DealState>(criteria)
            assertThat(states).hasSize(1)
        }
    }
}

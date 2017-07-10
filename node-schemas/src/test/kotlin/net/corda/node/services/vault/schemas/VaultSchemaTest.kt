package net.corda.node.services.vault.schemas

import io.requery.Persistable
import io.requery.TransactionIsolation
import io.requery.kotlin.*
import io.requery.query.RowExpression
import io.requery.rx.KotlinRxEntityStore
import io.requery.sql.*
import io.requery.sql.platform.Generic
import net.corda.core.contracts.*
import net.corda.testing.contracts.DummyContract
import net.corda.core.crypto.composite.CompositeKey
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.generateKeyPair
import net.corda.core.crypto.toBase58String
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.schemas.requery.converters.InstantConverter
import net.corda.core.schemas.requery.converters.VaultStateStatusConverter
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.LedgerTransaction
import net.corda.node.services.vault.schemas.requery.*
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.DUMMY_NOTARY_KEY
import org.h2.jdbcx.JdbcDataSource
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import rx.Observable
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultSchemaTest {

    var instance: KotlinEntityDataStore<Persistable>? = null
    val data: KotlinEntityDataStore<Persistable> get() = instance!!

    var oinstance: KotlinRxEntityStore<Persistable>? = null
    val odata: KotlinRxEntityStore<Persistable> get() = oinstance!!

    var transaction: LedgerTransaction? = null

    @Before
    fun setup() {
        val dataSource = JdbcDataSource()
        dataSource.setURL("jdbc:h2:mem:vault_persistence;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1")
        val configuration = KotlinConfiguration(dataSource = dataSource, model = Models.VAULT, mapping = setupCustomMapping(), useDefaultLogging = true)
        instance = KotlinEntityDataStore<Persistable>(configuration)
        oinstance = KotlinRxEntityStore(KotlinEntityDataStore<Persistable>(configuration))
        val tables = SchemaModifier(configuration)
        val mode = TableCreationMode.DROP_CREATE
        tables.createTables(mode)

        // create dummy test data
        setupDummyData()
    }

    private fun setupCustomMapping(): Mapping? {
        val mapping = GenericMapping(Generic())
        val instantConverter = InstantConverter()
        mapping.addConverter(instantConverter, instantConverter.mappedType)
        val vaultStateStatusConverter = VaultStateStatusConverter()
        mapping.addConverter(vaultStateStatusConverter, vaultStateStatusConverter.mappedType)
        return mapping
    }

    @After
    fun tearDown() {
        data.close()
    }

    private class VaultNoopContract : Contract {
        override val legalContractReference = SecureHash.sha256("")

        data class VaultNoopState(override val owner: AbstractParty) : OwnableState {
            override val contract = VaultNoopContract()
            override val participants: List<AbstractParty>
                get() = listOf(owner)

            override fun withNewOwner(newOwner: AbstractParty) = Pair(Commands.Create(), copy(owner = newOwner))
        }

        interface Commands : CommandData {
            class Create : TypeOnlyCommandData(), Commands
        }

        override fun verify(tx: TransactionForContract) {
            // Always accepts.
        }
    }

    private fun setupDummyData() {
        // dummy Transaction comprised of 3 different Contract State types
        // 1. SingleOwnerState
        // 2. MultiOwnerState
        // 3. VaultNoopState
        val notary: Party = DUMMY_NOTARY
        val inState1 = TransactionState(DummyContract.SingleOwnerState(0, ALICE), notary)
        val inState2 = TransactionState(DummyContract.MultiOwnerState(0,
                listOf(ALICE, BOB)), notary)
        val inState3 = TransactionState(VaultNoopContract.VaultNoopState(ALICE), notary)
        val outState1 = inState1.copy()
        val outState2 = inState2.copy()
        val outState3 = inState3.copy()
        val inputs = listOf(StateAndRef(inState1, StateRef(SecureHash.randomSHA256(), 0)),
                StateAndRef(inState2, StateRef(SecureHash.randomSHA256(), 0)),
                StateAndRef(inState3, StateRef(SecureHash.randomSHA256(), 0)))
        val outputs = listOf(outState1, outState2, outState3)
        val commands = emptyList<AuthenticatedObject<CommandData>>()
        val attachments = emptyList<Attachment>()
        val id = SecureHash.randomSHA256()
        val signers = listOf(DUMMY_NOTARY_KEY.public)
        val timeWindow: TimeWindow? = null
        transaction = LedgerTransaction(
                inputs,
                outputs,
                commands,
                attachments,
                id,
                notary,
                signers,
                timeWindow,
                TransactionType.General
        )
    }

    private fun createTxnWithTwoStateTypes(): LedgerTransaction {
        val notary: Party = DUMMY_NOTARY
        val inState1 = TransactionState(DummyContract.SingleOwnerState(0, ALICE), notary)
        val inState2 = TransactionState(DummyContract.MultiOwnerState(0,
                listOf(ALICE, BOB)), notary)
        val outState1 = inState1.copy()
        val outState2 = inState2.copy()
        val state1TxHash = SecureHash.randomSHA256()
        val state2TxHash = SecureHash.randomSHA256()
        val inputs = listOf(StateAndRef(inState1, StateRef(state1TxHash, 0)),
                StateAndRef(inState1, StateRef(state1TxHash, 1)),
                StateAndRef(inState2, StateRef(state2TxHash, 0)),
                StateAndRef(inState1, StateRef(state1TxHash, 2)))  // bogus state not in db
        val outputs = listOf(outState1, outState2)
        val commands = emptyList<AuthenticatedObject<CommandData>>()
        val attachments = emptyList<Attachment>()
        val id = SecureHash.randomSHA256()
        val signers = listOf(DUMMY_NOTARY_KEY.public)
        val timeWindow: TimeWindow? = null
        return LedgerTransaction(
                inputs,
                outputs,
                commands,
                attachments,
                id,
                notary,
                signers,
                timeWindow,
                TransactionType.General
        )
    }

    private fun dummyStatesInsert(txn: LedgerTransaction) {
        data.invoke {
            // skip inserting the last txn state (to mimic spend attempt of non existent unconsumed state)
            txn.inputs.subList(0, txn.inputs.lastIndex).forEach {
                insert(createStateEntity(it))
                // create additional state entities with idx >0
                for (i in 3..4) {
                    try {
                        createStateEntity(it, idx = i).apply {
                            insert(this)
                        }
                    } catch(e: Exception) {
                    }
                }
                // create additional state entities with different txn id
                for (i in 1..3) {
                    createStateEntity(it, txHash = SecureHash.randomSHA256().toString()).apply {
                        insert(this)
                    }
                }
            }
            // insert an additional MultiOwnerState with idx 1
            insert(createStateEntity(txn.inputs[2], idx = 1))

            // insert entities with other state type
            for (i in 1..5) {
                VaultStatesEntity().apply {
                    txId = SecureHash.randomSHA256().toString()
                    index = 0
                    contractStateClassName = VaultNoopContract.VaultNoopState::class.java.name
                    stateStatus = Vault.StateStatus.UNCONSUMED
                    insert(this)
                }
            }
        }

        // check total numner of inserted states
        assertEquals(3 + 4 + 9 + 1 + 5, data.select(VaultSchema.VaultStates::class).get().count())
    }

    /**
     *  Vault Schema: VaultStates
     */
    @Test
    fun testInsertState() {
        val state = VaultStatesEntity()
        state.txId = "12345"
        state.index = 0
        data.invoke {
            insert(state)
            val result = select(VaultSchema.VaultStates::class) where (VaultSchema.VaultStates::txId eq state.txId)
            Assert.assertSame(state, result().first())
        }
    }

    @Test
    fun testUpsertUnconsumedState() {
        val stateEntity = createStateEntity(transaction!!.inputs[0])
        data.invoke {
            upsert(stateEntity)
            val result = select(VaultSchema.VaultStates::class) where (VaultSchema.VaultStates::txId eq stateEntity.txId)
            Assert.assertSame(stateEntity, result().first())
        }
    }

    @Test
    fun testUpsertConsumedState() {
        val stateEntity = createStateEntity(transaction!!.inputs[0])
        data.invoke {
            upsert(stateEntity)
        }
        val keys = mapOf(VaultStatesEntity.TX_ID to stateEntity.txId,
                VaultStatesEntity.INDEX to stateEntity.index)
        val key = io.requery.proxy.CompositeKey(keys)
        data.invoke {
            val state = findByKey(VaultStatesEntity::class, key)
            state?.run {
                stateStatus = Vault.StateStatus.CONSUMED
                consumedTime = Instant.now()
                update(state)
                val result = select(VaultSchema.VaultStates::class) where (VaultSchema.VaultStates::txId eq state.txId)
                assertEquals(Vault.StateStatus.CONSUMED, result().first().stateStatus)
            }
        }
    }

    @Test
    fun testCashBalanceUpdate() {
        val cashBalanceEntity = VaultCashBalancesEntity()
        cashBalanceEntity.currency = "USD"
        cashBalanceEntity.amount = 100
        data.invoke {
            val state = findByKey(VaultCashBalancesEntity::class, cashBalanceEntity.currency)
            assertNull(state)
            upsert(cashBalanceEntity)
        }
        data.invoke {
            val state = findByKey(VaultCashBalancesEntity::class, cashBalanceEntity.currency)
            state?.let {
                state.amount -= 80
                upsert(state)
            }
            assertEquals(20, state!!.amount)
        }
    }

    @Test
    fun testTransactionalUpsertState() {
        data.withTransaction(TransactionIsolation.REPEATABLE_READ) {
            transaction!!.inputs.forEach {
                val stateEntity = createStateEntity(it)
                insert(stateEntity)
            }
            val result = select(VaultSchema.VaultStates::class)
            Assert.assertSame(3, result().toList().size)
        }
        data.invoke {
            val result = select(VaultSchema.VaultStates::class)
            Assert.assertSame(3, result().toList().size)
        }
    }

    @Test
    fun testDistinctContractStateTypes() {
        val txn = createTxnWithTwoStateTypes()
        dummyStatesInsert(txn)

        data.invoke {
            transaction!!.inputs.forEach {
                val stateEntity = createStateEntity(it)
                insert(stateEntity)
            }

            val query = select(VaultSchema.VaultStates::contractStateClassName).distinct()
            val results = query.get()

            Assert.assertSame(3, results.count())
        }
    }

    private fun createStateEntity(stateAndRef: StateAndRef<*>, idx: Int? = null, txHash: String? = null): VaultStatesEntity {
        val stateRef = stateAndRef.ref
        val state = stateAndRef.state
        return VaultStatesEntity().apply {
            txId = txHash ?: stateRef.txhash.toString()
            index = idx ?: stateRef.index
            stateStatus = Vault.StateStatus.UNCONSUMED
            contractStateClassName = state.data.javaClass.name
            contractState = state.serialize().bytes
            notaryName = state.notary.name.toString()
            notaryKey = state.notary.owningKey.toBase58String()
            recordedTime = Instant.now()
        }
    }

    /**
     *  Vault Schema: Transaction Notes
     */
    @Test
    fun testInsertTxnNote() {
        val txnNoteEntity = VaultTxnNoteEntity()
        txnNoteEntity.txId = "12345"
        txnNoteEntity.note = "Sample transaction note"
        data.invoke {
            insert(txnNoteEntity)
            val result = select(VaultSchema.VaultTxnNote::class)
            Assert.assertSame(txnNoteEntity, result().first())
        }
    }

    @Test
    fun testFindTxnNote() {
        val txnNoteEntity = VaultTxnNoteEntity()
        txnNoteEntity.txId = "12345"
        txnNoteEntity.note = "Sample transaction note #1"
        val txnNoteEntity2 = VaultTxnNoteEntity()
        txnNoteEntity2.txId = "23456"
        txnNoteEntity2.note = "Sample transaction note #2"
        data.invoke {
            insert(txnNoteEntity)
            insert(txnNoteEntity2)
        }
        data.invoke {
            val result = select(VaultSchema.VaultTxnNote::class) where (VaultSchema.VaultTxnNote::txId eq txnNoteEntity2.txId)
            assertEquals(result().count(), 1)
            Assert.assertSame(txnNoteEntity2, result().first())
        }
    }

    /**
     *  Vault Schema: Cash Balances
     */
    @Test
    fun testInsertCashBalance() {
        val cashBalanceEntity = VaultCashBalancesEntity()
        cashBalanceEntity.currency = "GPB"
        cashBalanceEntity.amount = 12345
        data.invoke {
            insert(cashBalanceEntity)
            val result = select(VaultSchema.VaultCashBalances::class)
            Assert.assertSame(cashBalanceEntity, result().first())
        }
    }

    @Test
    fun testUpdateCashBalance() {
        val cashBalanceEntity = VaultCashBalancesEntity()
        cashBalanceEntity.currency = "GPB"
        cashBalanceEntity.amount = 12345
        data.invoke {
            insert(cashBalanceEntity)
        }
        data.invoke {
            val state = findByKey(VaultCashBalancesEntity::class, cashBalanceEntity.currency)
            assertNotNull(state)
            state?.let {
                state.amount += 10000
                update(state)
                val result = select(VaultCashBalancesEntity::class)
                assertEquals(22345, result().first().amount)
            }
        }
    }

    @Test
    fun testUpsertCashBalance() {
        val cashBalanceEntity = VaultCashBalancesEntity()
        cashBalanceEntity.currency = "GPB"
        cashBalanceEntity.amount = 12345
        data.invoke {
            val state = findByKey(VaultCashBalancesEntity::class, cashBalanceEntity.currency)
            state?.let {
                state.amount += 10000
            }
            val result = upsert(state ?: cashBalanceEntity)
            assertEquals(12345, result.amount)
        }
    }

    @Test
    fun testAllUnconsumedStates() {
        data.invoke {
            transaction!!.inputs.forEach {
                insert(createStateEntity(it))
            }
        }
        val stateAndRefs = unconsumedStates<ContractState>()
        assertNotNull(stateAndRefs)
        assertTrue { stateAndRefs.size == 3 }
    }

    @Test
    fun tesUnconsumedDummyStates() {
        data.invoke {
            transaction!!.inputs.forEach {
                insert(createStateEntity(it))
            }
        }
        val stateAndRefs = unconsumedStates<DummyContract.State>()
        assertNotNull(stateAndRefs)
        assertTrue { stateAndRefs.size == 2 }
    }

    @Test
    fun tesUnconsumedDummySingleOwnerStates() {
        data.invoke {
            transaction!!.inputs.forEach {
                insert(createStateEntity(it))
            }
        }
        val stateAndRefs = unconsumedStates<DummyContract.SingleOwnerState>()
        assertNotNull(stateAndRefs)
        assertTrue { stateAndRefs.size == 1 }
    }

    inline fun <reified T : ContractState> unconsumedStates(): List<StateAndRef<T>> {
        val stateAndRefs =
                data.invoke {
                    val result = select(VaultSchema.VaultStates::class)
                            .where(VaultSchema.VaultStates::stateStatus eq Vault.StateStatus.UNCONSUMED)
                    result.get()
                            .map { it ->
                                val stateRef = StateRef(SecureHash.parse(it.txId), it.index)
                                val state = it.contractState.deserialize<TransactionState<T>>()
                                StateAndRef(state, stateRef)
                            }.filter {
                        T::class.java.isAssignableFrom(it.state.data.javaClass)
                    }.toList()
                }
        return stateAndRefs
    }

    /**
     * Observables testing
     */
    @Test
    @Throws(Exception::class)
    fun testInsert() {
        val stateEntity = createStateEntity(transaction!!.inputs[0])
        val latch = CountDownLatch(1)
        odata.insert(stateEntity).subscribe { stateEntity ->
            Assert.assertNotNull(stateEntity.txId)
            Assert.assertTrue(stateEntity.txId.isNotEmpty())
            val cached = data.select(VaultSchema.VaultStates::class)
                    .where(VaultSchema.VaultStates::txId.eq(stateEntity.txId)).get().first()
            Assert.assertSame(cached, stateEntity)
            latch.countDown()
        }
        latch.await()
    }

    @Test
    @Throws(Exception::class)
    fun testInsertCount() {
        val stateEntity = createStateEntity(transaction!!.inputs[0])
        Observable.just(stateEntity)
                .concatMap { person -> odata.insert(person).toObservable() }
        odata.insert(stateEntity).toBlocking().value()
        Assert.assertNotNull(stateEntity.txId)
        Assert.assertTrue(stateEntity.txId.isNotEmpty())
        val count = data.count(VaultSchema.VaultStates::class).get().value()
        Assert.assertEquals(1, count.toLong())
    }

    @Test
    @Throws(Exception::class)
    fun testQueryEmpty() {
        val latch = CountDownLatch(1)
        odata.select(VaultSchema.VaultStates::class).get().toObservable()
                .subscribe({ Assert.fail() }, { Assert.fail() }) { latch.countDown() }
        if (!latch.await(1, TimeUnit.SECONDS)) {
            Assert.fail()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testQueryObservable() {
        transaction!!.inputs.forEach {
            val stateEntity = createStateEntity(it)
            odata.insert(stateEntity).toBlocking().value()
        }
        val states = ArrayList<VaultStatesEntity>()
        odata.select(VaultSchema.VaultStates::class).get()
                .toObservable()
                .subscribe { it -> states.add(it as VaultStatesEntity) }
        Assert.assertEquals(3, states.size)
    }

    /**
     *  Requery composite key tests (using RowExpression introduced in 1.2.1)
     */
    @Test
    fun testQueryWithCompositeKey() {
        // txn entity with 4 input states (SingleOwnerState x 3, MultiOwnerState x 1)
        val txn = createTxnWithTwoStateTypes()
        dummyStatesInsert(txn)

        data.invoke {
            val primaryCompositeKey = listOf(VaultStatesEntity.TX_ID, VaultStatesEntity.INDEX)
            val expression = RowExpression.of(primaryCompositeKey)
            val stateRefs = txn.inputs.map { listOf("'${it.ref.txhash}'", it.ref.index) }

            val result = select(VaultStatesEntity::class) where (expression.`in`(stateRefs))
            assertEquals(3, result.get().count())
        }
    }

    @Test
    fun testUpdateWithCompositeKey() {
        // txn entity with 4 input states (SingleOwnerState x 3, MultiOwnerState x 1)
        val txn = createTxnWithTwoStateTypes()
        dummyStatesInsert(txn)

        data.invoke {
            val primaryCompositeKey = listOf(VaultStatesEntity.TX_ID, VaultStatesEntity.INDEX)
            val expression = RowExpression.of(primaryCompositeKey)
            val stateRefs = txn.inputs.map { listOf("'${it.ref.txhash}'", it.ref.index) }

            val update = update(VaultStatesEntity::class)
                    .set(VaultStatesEntity.LOCK_ID, "")
                    .set(VaultStatesEntity.LOCK_UPDATE_TIME, Instant.now())
                    .where(VaultStatesEntity.STATE_STATUS eq Vault.StateStatus.UNCONSUMED)
                    .and(expression.`in`(stateRefs)).get()
            assertEquals(3, update.value())
        }
    }

    /**
     *  Soft locking tests
     */
    @Test
    fun testSingleSoftLockUpdate() {

        // insert unconsumed state
        val stateEntity = createStateEntity(transaction!!.inputs[0])
        data.invoke {
            upsert(stateEntity)
        }

        // reserve soft lock on state
        stateEntity.apply {
            this.lockId = "LOCK#1"
            this.lockUpdateTime = Instant.now()
            data.invoke {
                upsert(stateEntity)
            }
        }

        // select unlocked states
        data.invoke {
            val result = select(VaultSchema.VaultStates::class) where (VaultSchema.VaultStates::txId eq stateEntity.txId)
                             .and(VaultSchema.VaultStates::lockId.isNull())
            assertEquals(0, result.get().count())
        }

        // release soft lock on state
        data.invoke {
            val update = update(VaultStatesEntity::class)
                    .set(VaultStatesEntity.LOCK_ID, null)
                    .set(VaultStatesEntity.LOCK_UPDATE_TIME, Instant.now())
                    .where(VaultStatesEntity.STATE_STATUS eq Vault.StateStatus.UNCONSUMED)
                    .and(VaultStatesEntity.LOCK_ID eq "LOCK#1").get()
            assertEquals(1, update.value())
        }

        // select unlocked states
        data.invoke {
            val result = select(VaultSchema.VaultStates::class) where (VaultSchema.VaultStates::txId eq stateEntity.txId)
                    .and(VaultSchema.VaultStates::lockId.isNull())
            assertEquals(1, result.get().count())
        }
    }

    @Test
    fun testMultipleSoftLocksUpdate() {

        // insert unconsumed state
        data.withTransaction(TransactionIsolation.REPEATABLE_READ) {
            transaction!!.inputs.forEach {
                val stateEntity = createStateEntity(it)
                insert(stateEntity)
            }
            val result = select(VaultSchema.VaultStates::class)
            Assert.assertSame(3, result().toList().size)
        }

        // reserve soft locks on states
        transaction!!.inputs.forEach {
            val stateEntity = createStateEntity(it)
            stateEntity.apply {
                this.lockId = "LOCK#1"
                this.lockUpdateTime = Instant.now()
                data.invoke {
                    upsert(stateEntity)
                }
            }
        }

        // select unlocked states
        val txnIds = transaction!!.inputs.map { it.ref.txhash.toString() }.toSet()
        data.invoke {
            val result = select(VaultSchema.VaultStates::class) where (VaultSchema.VaultStates::txId `in` txnIds)
                    .and(VaultSchema.VaultStates::lockId eq "")
            assertEquals(0, result.get().count())
        }

        // release soft lock on states
        data.invoke {
            val primaryCompositeKey = listOf(VaultStatesEntity.TX_ID, VaultStatesEntity.INDEX)
            val expression = RowExpression.of(primaryCompositeKey)
            val stateRefs = transaction!!.inputs.map { listOf("'${it.ref.txhash}'", it.ref.index) }

            val update = update(VaultStatesEntity::class)
                    .set(VaultStatesEntity.LOCK_ID, "")
                    .set(VaultStatesEntity.LOCK_UPDATE_TIME, Instant.now())
                    .where(VaultStatesEntity.STATE_STATUS eq Vault.StateStatus.UNCONSUMED)
                    .and(expression.`in`(stateRefs)).get()
            assertEquals(3, update.value())
        }

        // select unlocked states
        data.invoke {
            val result = select(VaultSchema.VaultStates::class) where (VaultSchema.VaultStates::txId `in` txnIds)
                    .and(VaultSchema.VaultStates::lockId eq "")
            assertEquals(3, result.get().count())
        }
    }

    @Test
    fun insertWithBigCompositeKey() {
        val keys = (1..314).map { generateKeyPair().public }
        val bigNotaryKey = CompositeKey.Builder().addKeys(keys).build()
        val vaultStEntity = VaultStatesEntity().apply {
            txId = SecureHash.randomSHA256().toString()
            index = 314
            stateStatus = Vault.StateStatus.UNCONSUMED
            contractStateClassName = VaultNoopContract.VaultNoopState::class.java.name
            notaryName = "Huge distributed notary"
            notaryKey = bigNotaryKey.toBase58String()
            recordedTime = Instant.now()
        }
        data.insert(vaultStEntity)
        assertEquals(1, data.select(VaultSchema.VaultStates::class).get().count())
    }
}
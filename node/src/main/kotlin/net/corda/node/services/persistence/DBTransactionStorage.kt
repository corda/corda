package net.corda.node.services.persistence

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.ComponentGroupEnum
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.internal.ThreadBox
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.messaging.DataFeed
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.*
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.core.toFuture
import net.corda.core.transactions.CoreTransaction
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.node.services.api.WritableTransactionStorage
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.utilities.AppendOnlyPersistentMapBase
import net.corda.node.utilities.WeightBasedAppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.bufferUntilDatabaseCommit
import net.corda.nodeapi.internal.persistence.wrapWithDatabaseTransaction
import org.apache.commons.lang3.ArrayUtils.EMPTY_BYTE_ARRAY
import rx.Observable
import rx.subjects.PublishSubject
import javax.persistence.*

// cache value type to just store the immutable bits of a signed transaction plus conversion helpers
typealias TxCacheValue = Pair<SerializedBytes<CoreTransaction>, List<TransactionSignature>>
typealias SerializedTxState = SerializedBytes<TransactionState<ContractState>>

fun TxCacheValue.toSignedTx() = SignedTransaction(this.first, this.second)
fun SignedTransaction.toTxCacheValue() = TxCacheValue(this.txBits, this.sigs)

class DBTransactionStorage(private val database: CordaPersistence, cacheFactory: NamedCacheFactory) : WritableTransactionStorage, SingletonSerializeAsToken() {

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}transactions")
    class DBTransaction(
            @Id
            @Column(name = "tx_id", length = 64, nullable = false)
            var txId: String = "",

            @Column(name = "state_machine_run_id", length = 36, nullable = true)
            var stateMachineRunId: String? = "",

            @Lob
            @Column(name = "transaction_value", nullable = false)
            var transaction: ByteArray = EMPTY_BYTE_ARRAY
    )

    /**
     * SGX: Store some [StateRef] to [StateAndRef] mapping plus associated Merkle Tree certificate from transaction that
     * have been filtered
     */
    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}stateref")
    class DBStateRef(
            @EmbeddedId
            var stateRef: PersistentStateRef? = null,

            @Lob
            @Column(name = "serialized_transaction_state", nullable = false)
            var txState: ByteArray = EMPTY_BYTE_ARRAY
    )

    private companion object {
        private fun contextToUse(): SerializationContext {
            return if (effectiveSerializationEnv.serializationFactory.currentContext?.useCase == SerializationContext.UseCase.Storage) {
                effectiveSerializationEnv.serializationFactory.currentContext!!
            } else {
                SerializationDefaults.STORAGE_CONTEXT
            }
        }

        fun createTransactionsMap(cacheFactory: NamedCacheFactory)
                : AppendOnlyPersistentMapBase<SecureHash, TxCacheValue, DBTransaction, String> {
            return WeightBasedAppendOnlyPersistentMap<SecureHash, TxCacheValue, DBTransaction, String>(
                    cacheFactory = cacheFactory,
                    name = "DBTransactionStorage_transactions",
                    toPersistentEntityKey = { it.toString() },
                    fromPersistentEntity = {
                        Pair(SecureHash.parse(it.txId),
                                it.transaction.deserialize<SignedTransaction>(context = contextToUse())
                                        .toTxCacheValue())
                    },
                    toPersistentEntity = { key: SecureHash, value: TxCacheValue ->
                        DBTransaction().apply {
                            txId = key.toString()
                            stateMachineRunId = FlowStateMachineImpl.currentStateMachine()?.id?.uuid?.toString()
                            transaction = value.toSignedTx().serialize(context = contextToUse()).bytes
                        }
                    },
                    persistentEntityClass = DBTransaction::class.java,
                    weighingFunc = { hash, tx -> hash.size + weighTx(tx) }
            )
        }

        fun buildStateRefMap(cacheFactory: NamedCacheFactory)
            : AppendOnlyPersistentMapBase<StateRef, SerializedTxState, DBStateRef, PersistentStateRef>  {
            return WeightBasedAppendOnlyPersistentMap<StateRef, SerializedTxState, DBStateRef, PersistentStateRef>(
                    cacheFactory = cacheFactory,
                    name = "DBTransactionStorage_transactions",
                    toPersistentEntityKey = { PersistentStateRef(it) },
                    fromPersistentEntity = {
                        val stateRef: StateRef = it.stateRef?.let { it -> StateRef(SecureHash.parse(it.txId), it.index) }!!
                        val serializedTxState = SerializedTxState(it.txState)
                        Pair(stateRef, serializedTxState)
                    },
                    toPersistentEntity = { key: StateRef, value: SerializedTxState ->
                        DBStateRef().apply {
                            stateRef = PersistentStateRef(key)
                            txState = value.bytes
                        }
                    },
                    persistentEntityClass = DBStateRef::class.java,
                    weighingFunc = { stateRef, bytes -> stateRef.txhash.size + 4 + weighTxState(bytes) }
            )
        }


        // Rough estimate for the average of a public key and the transaction metadata - hard to get exact figures here,
        // as public keys can vary in size a lot, and if someone else is holding a reference to the key, it won't add
        // to the memory pressure at all here.
        private const val transactionSignatureOverheadEstimate = 1024

        private fun weighTx(tx: AppendOnlyPersistentMapBase.Transactional<TxCacheValue>): Int {
            val actTx = tx.peekableValue
            if (actTx == null) {
                return 0
            }
            return actTx.second.sumBy { it.size + transactionSignatureOverheadEstimate } + actTx.first.size
        }

        private fun weighTxState(s: AppendOnlyPersistentMapBase.Transactional<SerializedTxState>): Int {
            return s.peekableValue?.let { it.bytes.size } ?: 0
        }
    }

    private val txStorage = ThreadBox(Pair(createTransactionsMap(cacheFactory), buildStateRefMap(cacheFactory)))

    override fun addTransaction(transaction: SignedTransaction): Boolean = database.transaction {
        txStorage.locked {
            if (transaction.coreTransaction is FilteredTransaction) {
                addFilteredTransactionOutputStates(transaction.coreTransaction as FilteredTransaction)
            }
            first.addWithDuplicatesAllowed(transaction.id, transaction.toTxCacheValue()).apply {
                updatesPublisher.bufferUntilDatabaseCommit().onNext(transaction)
            }
        }
    }

    /**
     * SGX: Add explicit [StateRef] resolution mapping for partially torn-off transactions
     */
    fun addFilteredTransactionOutputStates(tx: FilteredTransaction) {
        val outputStatesById = tx.filteredComponentGroups.first { it.groupIndex == ComponentGroupEnum.OUTPUTS_GROUP.ordinal }.indexed()
        for ((data, id) in outputStatesById) {
            txStorage.content.second.addWithDuplicatesAllowed(StateRef(tx.id, id), SerializedTxState(data.bytes))
        }
    }

    override fun getTransaction(id: SecureHash): SignedTransaction? = database.transaction { txStorage.content.first[id]?.toSignedTx() }

    private val updatesPublisher = PublishSubject.create<SignedTransaction>().toSerialized()
    override val updates: Observable<SignedTransaction> = updatesPublisher.wrapWithDatabaseTransaction()

    override fun track(): DataFeed<List<SignedTransaction>, SignedTransaction> {
        return database.transaction {
            txStorage.locked {
                DataFeed(first.allPersisted().map { it.second.toSignedTx() }.toList(), updates.bufferUntilSubscribed())
            }
        }
    }

    override fun trackTransaction(id: SecureHash): CordaFuture<SignedTransaction> {
        return database.transaction {
            txStorage.locked {
                val existingTransaction = first.get(id)
                if (existingTransaction == null) {
                    updates.filter { it.id == id }.toFuture()
                } else {
                    doneFuture(existingTransaction.toSignedTx())
                }
            }
        }
    }

    override fun resolveState(id: StateRef): SerializedBytes<TransactionState<ContractState>>? {
        return database.transaction {
            txStorage.content.second[id]
        }
    }

    @VisibleForTesting
    val transactions: Iterable<SignedTransaction>
        get() = database.transaction { txStorage.content.first.allPersisted().map { it.second.toSignedTx() }.toList() }
}

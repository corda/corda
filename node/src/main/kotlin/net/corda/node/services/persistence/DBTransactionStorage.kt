package net.corda.node.services.persistence

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Command
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ComponentGroupEnum
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.PrivacySalt
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.PartialMerkleTree
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.identity.Party
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.internal.ThreadBox
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.internal.createComponentGroups
import net.corda.core.internal.deserialiseCommands
import net.corda.core.internal.deserialiseComponentGroup
import net.corda.core.messaging.DataFeed
import net.corda.core.serialization.*
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.core.toFuture
import net.corda.core.transactions.ComponentGroup
import net.corda.core.transactions.CoreTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.CordaClock
import net.corda.node.services.api.WritableTransactionStorage
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.utilities.AppendOnlyPersistentMapBase
import net.corda.node.utilities.WeightBasedAppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.*
import net.corda.serialization.internal.CordaSerializationEncoding.SNAPPY
import org.hibernate.Session
import org.hibernate.annotations.Type
import rx.Observable
import rx.subjects.PublishSubject
import java.io.Serializable
import java.security.PublicKey
import java.time.Instant
import java.util.*
import javax.persistence.*
import kotlin.reflect.KClass
import kotlin.streams.toList

class DBTransactionStorage(private val database: CordaPersistence, cacheFactory: NamedCacheFactory,
                           private val clock: CordaClock) : WritableTransactionStorage, SingletonSerializeAsToken() {

    @Suppress("MagicNumber") // database column width
    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}transactions")
    class DBTransaction(
            @Id
            @Column(name = "tx_id", length = 64, nullable = false)
            val txId: String,

            @Column(name = "state_machine_run_id", length = 36, nullable = true)
            val stateMachineRunId: String?,

            @Lob
            @Column(name = "transaction_value", nullable = false)
            val transaction: ByteArray,

            @Column(name = "status", nullable = false, length = 1)
            @Convert(converter = TransactionStatusConverter::class)
            val status: TransactionStatus,

            @Column(name = "timestamp", nullable = false)
            val timestamp: Instant
            )

    enum class TransactionStatus {
        UNVERIFIED,
        VERIFIED;

        fun toDatabaseValue(): String {
            return when (this) {
                UNVERIFIED -> "U"
                VERIFIED -> "V"
            }
        }

        fun isVerified(): Boolean {
            return this == VERIFIED
        }

        companion object {
            fun fromDatabaseValue(databaseValue: String): TransactionStatus {
                return when (databaseValue) {
                    "V" -> VERIFIED
                    "U" -> UNVERIFIED
                    else -> throw UnexpectedStatusValueException(databaseValue)
                }
            }
        }

        private class UnexpectedStatusValueException(status: String) : Exception("Found unexpected status value $status in transaction store")
    }

    /**
     * Holds the data of a Component inside a ComponentGroup, identified by the txId, componentGroupIndex and componentIndex
     */
    @Suppress("MagicNumber") // database column width
    @CordaSerializable
    @Entity
    @Table(name = "transaction_component")
    class DBTransactionComponent(
            @EmbeddedId
            val transactionComponentId: DBTransactionComponentId,

            /** The serialized data of this component */
            @Type(type = "corda-blob")
            @Column(name = "data", nullable = false)
            val data: ByteArray,

            @Type(type = "corda-blob")
            @Column(name = "privacy_salt", nullable = false, length = 32)
            val privacySalt: ByteArray,

            //todo conal - check with Matthew if this will always be 1-1
            @OneToOne(cascade = [CascadeType.ALL])
            @JoinTable(name = "transaction_referenced_by_stateref",
                    joinColumns = [
                        JoinColumn(name = "owning_tx_id", referencedColumnName = "tx_id"),
                        JoinColumn(name = "owning_component_group_index", referencedColumnName = "component_group_index"),
                        JoinColumn(name = "owning_component_group_leaf_index", referencedColumnName = "component_group_leaf_index")
                    ])
            var referenced: DBTransactionComponent? = null,

            @ManyToMany(cascade = [CascadeType.ALL])
            @JoinTable(name = "transaction_component_signature_assoc",
                    joinColumns = [
                        JoinColumn(name = "tx_id", referencedColumnName = "tx_id"),
                        JoinColumn(name = "component_group_index", referencedColumnName = "component_group_index"),
                        JoinColumn(name = "component_group_leaf_index", referencedColumnName = "component_group_leaf_index")],
                    inverseJoinColumns = [JoinColumn(name = "signature_id", referencedColumnName = "id")])
            val transactionSignature: List<DBTransactionSignature>
    )

    @Embeddable
    @CordaSerializable
    class DBTransactionComponentId(
            /** The transaction id, root of the Transaction Merkle tree containing the component */
            @Column(name = "tx_id", length = 64, nullable = false)
            val txId: String,

            /** Ordinal of ComponentGroupEnum, used as a componentGroup index in the Transaction Merkle tree e.g. input=0/output=1/attachments=3 */
            @Column(name = "component_group_index", nullable = false)
            @Enumerated(value = EnumType.STRING)
            val componentGroupIndex: ComponentGroupEnum,

            /** Component index within ComponentGroup's Merkle tree */
            @Column(name = "component_group_leaf_index", nullable = false)
            val componentGroupLeafIndex: Int
    ): Serializable {
        override fun equals(other: Any?): Boolean {
            if(this === other) return true
            if(other !is DBTransactionComponentId) return false
            return (other.txId == this.txId
                    && other.componentGroupIndex == this.componentGroupIndex
                    && other.componentGroupLeafIndex == this.componentGroupLeafIndex)
        }

        override fun hashCode(): Int {
            return super.hashCode()
        }
    }

    @CordaSerializable
    @Entity
    @Table(name = "transaction_signature", indexes = [Index(name = "tx_id_idx", columnList = "tx_id", unique = false)])
    class DBTransactionSignature(
            @Id
            @Column(name = "id", nullable = false)
            val id: String,

            @Column(name = "tx_id", length = 64, nullable = false)
            val txId: String,

            @Column(name = "by", length = 1024, nullable = false)
            val by: PublicKey,

            @Type(type = "corda-blob")
            @Column(name = "signature", nullable = false)
            val signature: ByteArray,

            @Column(name = "platform_version", nullable = false)
            val platformVersion: Int,

            @Column(name = "scheme_number_id", nullable = false)
            val schemeNumberId: Int,

            @Type(type = "corda-blob")
            @Column(name = "partial_merkle_tree", nullable = true)
            val partialMerkleTree: ByteArray?
    )

    @CordaSerializable
    @Entity
    @Table(name = "transaction_command")
    class DBTransactionCommand(
            @Id
            @Column(name = "id", nullable = false)
            val id: String,

            @Column(name = "tx_id", length = 64, nullable = false, unique = false)
            val txId: String,

            @Type(type = "corda-blob")
            @Column(name = "command_data", nullable = false)
            val commandData: ByteArray,

            @ElementCollection(targetClass = PublicKey::class, fetch = FetchType.EAGER)
            @Column(name = "signer", nullable = false)
            @CollectionTable(name = "transaction_command_signer", joinColumns = [(JoinColumn(name = "command_id", referencedColumnName = "id"))])
            var signers: List<PublicKey>? = null
    ): Serializable

    @Converter
    class TransactionStatusConverter : AttributeConverter<TransactionStatus, String> {
        override fun convertToDatabaseColumn(attribute: TransactionStatus): String {
            return attribute.toDatabaseValue()
        }

        override fun convertToEntityAttribute(dbData: String): TransactionStatus {
            return TransactionStatus.fromDatabaseValue(dbData)
        }
    }

    internal companion object {
        const val TRANSACTION_ALREADY_IN_PROGRESS_WARNING = "trackTransaction is called with an already existing, open DB transaction. As a result, there might be transactions missing from the returned data feed, because of race conditions."

        // Rough estimate for the average of a public key and the transaction metadata - hard to get exact figures here,
        // as public keys can vary in size a lot, and if someone else is holding a reference to the key, it won't add
        // to the memory pressure at all here.
        private const val transactionSignatureOverheadEstimate = 1024

        private val logger = contextLogger()

        private fun contextToUse(): SerializationContext {
            return if (effectiveSerializationEnv.serializationFactory.currentContext?.useCase == SerializationContext.UseCase.Storage) {
                effectiveSerializationEnv.serializationFactory.currentContext!!
            } else {
                SerializationDefaults.STORAGE_CONTEXT
            }
        }

        private fun createTransactionsMap(cacheFactory: NamedCacheFactory, clock: CordaClock)
                : AppendOnlyPersistentMapBase<SecureHash, TxCacheValue, DBTransaction, String> {
            return WeightBasedAppendOnlyPersistentMap<SecureHash, TxCacheValue, DBTransaction, String>(
                    cacheFactory = cacheFactory,
                    name = "DBTransactionStorage_transactions",
                    toPersistentEntityKey = SecureHash::toString,
                    fromPersistentEntity = {
                        SecureHash.parse(it.txId) to TxCacheValue(
                                it.transaction.deserialize(context = contextToUse()),
                                it.status)
                    },
                    toPersistentEntity = { key: SecureHash, value: TxCacheValue ->
                        DBTransaction(
                                txId = key.toString(),
                                stateMachineRunId = FlowStateMachineImpl.currentStateMachine()?.id?.uuid?.toString(),
                                transaction = value.toSignedTx().serialize(context = contextToUse().withEncoding(SNAPPY)).bytes,
                                status = value.status,
                                timestamp = clock.instant()
                        )
                    },
                    persistentEntityClass = DBTransaction::class.java,
                    weighingFunc = { hash, tx -> hash.size + weighTx(tx) }
            )
        }

        private fun weighTx(tx: AppendOnlyPersistentMapBase.Transactional<TxCacheValue>): Int {
            val actTx = tx.peekableValue ?: return 0
            return actTx.sigs.sumBy { it.size + transactionSignatureOverheadEstimate } + actTx.txBits.size
        }

        private val log = contextLogger()
    }

    private val txStorage = ThreadBox(createTransactionsMap(cacheFactory, clock))

    private fun updateTransaction(txId: SecureHash): Boolean {
        val session = currentDBSession()
        val criteriaBuilder = session.criteriaBuilder
        val criteriaUpdate = criteriaBuilder.createCriteriaUpdate(DBTransaction::class.java)
        val updateRoot = criteriaUpdate.from(DBTransaction::class.java)
        criteriaUpdate.set(updateRoot.get<TransactionStatus>(DBTransaction::status.name), TransactionStatus.VERIFIED)
        criteriaUpdate.where(criteriaBuilder.and(
                criteriaBuilder.equal(updateRoot.get<String>(DBTransaction::txId.name), txId.toString()),
                criteriaBuilder.equal(updateRoot.get<TransactionStatus>(DBTransaction::status.name), TransactionStatus.UNVERIFIED)
        ))
        criteriaUpdate.set(updateRoot.get<Instant>(DBTransaction::timestamp.name), clock.instant())
        val update = session.createQuery(criteriaUpdate)
        val rowsUpdated = update.executeUpdate()
        return rowsUpdated != 0
    }

    override fun addTransaction(transaction: SignedTransaction): Boolean {
        return database.transaction {
            txStorage.locked {
                val cachedValue = TxCacheValue(transaction, TransactionStatus.VERIFIED)
                val addedOrUpdated = addOrUpdate(transaction.id, cachedValue) { k, _ -> updateTransaction(k) }
                if (addedOrUpdated) {
                    logger.debug { "Transaction ${transaction.id} has been recorded as verified" }
                    onNewTx(transaction)
                    storeTransactionComponents(transaction)
                } else {
                    logger.debug { "Transaction ${transaction.id} is already recorded as verified, so no need to re-record" }
                    false
                }
            }
        }
    }

    private fun storeTransactionComponents(transaction: SignedTransaction): Boolean {
        database.transaction {
            txStorage.locked {
                val session = currentDBSession()
                transaction.tx.componentGroups.let {
                    deserialiseInputGroup(it, session, transaction)
                    deserialiseComponentGroupAndStoreComponents(it, session, transaction, TransactionState::class, ComponentGroupEnum.OUTPUTS_GROUP)
                    deserialiseComponentGroupAndStoreCommands(it, session, transaction) // storing list of Command instead of individual CommandData and Signers
                    deserialiseComponentGroupAndStoreComponents(it, session, transaction, SecureHash::class, ComponentGroupEnum.ATTACHMENTS_GROUP)
                    deserialiseComponentGroupAndStoreComponents(it, session, transaction, Party::class, ComponentGroupEnum.NOTARY_GROUP)
                    deserialiseComponentGroupAndStoreComponents(it, session, transaction, TimeWindow::class, ComponentGroupEnum.TIMEWINDOW_GROUP)
                    deserialiseComponentGroupAndStoreComponents(it, session, transaction, StateRef::class, ComponentGroupEnum.REFERENCES_GROUP)
                    deserialiseComponentGroupAndStoreComponents(it, session, transaction, SecureHash::class, ComponentGroupEnum.PARAMETERS_GROUP)
                }
            }
        }
        return true
    }

    private fun deserialiseInputGroup(componentGroups: List<ComponentGroup>, session: Session, transaction: SignedTransaction) {
        val listOfStateRefs = deserialiseComponentGroup(componentGroups, StateRef::class, ComponentGroupEnum.INPUTS_GROUP, forceDeserialize = true)

        val listOfSignatures = transaction.sigs.map {
            DBTransactionSignature(UUID.randomUUID().toString(), transaction.id.toString(), it.by, it.bytes,
                    it.signatureMetadata.platformVersion, it.signatureMetadata.schemeNumberID,
                    // todo conal - partialMerkleTree
                    /*if(it.partialMerkleTree != null){ it.partialMerkleTree!.serialize().bytes} else {null}*/null) }
                .toList()

        listOfStateRefs.forEachIndexed { index, stateRef ->
            val referencedDbTransactionComponent =
                    session.load(
                            DBTransactionComponent::class.java,
                            DBTransactionComponentId(stateRef.txhash.toString(), ComponentGroupEnum.OUTPUTS_GROUP, stateRef.index))

            session.save(
                    DBTransactionComponent(
                            DBTransactionComponentId(transaction.id.toString(), ComponentGroupEnum.INPUTS_GROUP, index),
                            stateRef.serialize().bytes,
                            transaction.tx.privacySalt.bytes,
                            referencedDbTransactionComponent,
                            listOfSignatures))
        }
    }

    private fun <T : Any> deserialiseComponentGroupAndStoreComponents(componentGroups: List<ComponentGroup>, session: Session,
                                                                    transaction: SignedTransaction, kClass: KClass<T>,
                                                                    componentGroupEnum: ComponentGroupEnum) {
        val listOfDeserialisedComponents = deserialiseComponentGroup(componentGroups, kClass, componentGroupEnum, forceDeserialize = true)

        val listOfSignatures = transaction.sigs.map {
            DBTransactionSignature(UUID.randomUUID().toString(), transaction.id.toString(), it.by, it.bytes,
                    it.signatureMetadata.platformVersion, it.signatureMetadata.schemeNumberID,
                    // todo conal - partialMerkleTree
                    /*if(it.partialMerkleTree != null){ it.partialMerkleTree!.serialize().bytes} else {null}*/null) }.toList()

        listOfDeserialisedComponents.forEachIndexed { index, component ->
            session.save(DBTransactionComponent(DBTransactionComponentId(transaction.id.toString(), componentGroupEnum, index),
                    component.serialize().bytes, transaction.tx.privacySalt.bytes, null, listOfSignatures))
        }
    }

    private fun deserialiseComponentGroupAndStoreCommands(it: List<ComponentGroup>, session: Session, transaction: SignedTransaction) {
        val listOfCommands = deserialiseCommands(it, forceDeserialize = true)
        listOfCommands.forEach {
            val dbTransactionCommand = DBTransactionCommand(id = UUID.randomUUID().toString(), txId = transaction.id.toString(),
                    commandData = it.value.serialize().bytes, signers = it.signers)
            session.save(dbTransactionCommand)
        }
    }

    fun convertTransactionComponentsToWireTransaction(transactionComponents: List<DBTransactionComponent>): WireTransaction {
        return WireTransaction(createComponentGroups(
                inputs = transactionComponents
                        .filter { it.transactionComponentId.componentGroupIndex == ComponentGroupEnum.INPUTS_GROUP }
                        .map { deserialiseComponent(it) as StateRef }
                        .toList(),
                outputs = transactionComponents
                        .filter { it.transactionComponentId.componentGroupIndex == ComponentGroupEnum.OUTPUTS_GROUP }
                        .map { deserialiseComponent(it) as TransactionState<ContractState> }
                        .toList(),
                commands = transactionComponents
                        .filter { it.transactionComponentId.componentGroupIndex == ComponentGroupEnum.COMMANDS_GROUP }
                        .map { deserialiseComponent(it) as Command<*> }
                        .toList(),
                attachments = transactionComponents
                        .filter { it.transactionComponentId.componentGroupIndex == ComponentGroupEnum.ATTACHMENTS_GROUP }
                        .map { deserialiseComponent(it) as SecureHash }
                        .toList(),
                notary = transactionComponents
                        .filter { it.transactionComponentId.componentGroupIndex == ComponentGroupEnum.NOTARY_GROUP }
                        .map { deserialiseComponent(it) as Party }.getOrNull(0),
                timeWindow = transactionComponents
                        .filter { it.transactionComponentId.componentGroupIndex == ComponentGroupEnum.TIMEWINDOW_GROUP }
                        .map { deserialiseComponent(it) as TimeWindow }.getOrNull(0),
                references = transactionComponents
                        .filter { it.transactionComponentId.componentGroupIndex == ComponentGroupEnum.REFERENCES_GROUP }
                        .map { deserialiseComponent(it) as StateRef }
                        .toList(),
                networkParametersHash = null //todo conal
        ))
    }

    fun deserialiseComponent(component: DBTransactionComponent): Any {
        return component.data.let {
            when (component.transactionComponentId.componentGroupIndex) {
                ComponentGroupEnum.INPUTS_GROUP -> it.deserialize<StateRef>()
                ComponentGroupEnum.OUTPUTS_GROUP -> it.deserialize<TransactionState<ContractState>>()
                ComponentGroupEnum.COMMANDS_GROUP -> it.deserialize<CommandData>()
                ComponentGroupEnum.ATTACHMENTS_GROUP -> it.deserialize<SecureHash>()
                ComponentGroupEnum.NOTARY_GROUP -> it.deserialize<Party>()
                ComponentGroupEnum.TIMEWINDOW_GROUP -> it.deserialize<TimeWindow>()
                ComponentGroupEnum.SIGNERS_GROUP -> it.deserialize<List<PublicKey>>()
                ComponentGroupEnum.REFERENCES_GROUP -> it.deserialize<StateRef>()
                ComponentGroupEnum.PARAMETERS_GROUP -> it.deserialize<SecureHash>()
            }
        }
    }

    fun fetchTransactionComponents(txId: SecureHash): List<DBTransactionComponent> {
        return database.transaction {
            //todo conal - convert to use jpa criteria?
            session.createQuery(
                    "from ${DBTransactionComponent::class.java.name} where tx_id = :txId", DBTransactionComponent::class.java)
                    .setParameter("txId", txId.toString())
                    .resultList
        }
    }

    fun fetchSingleTransactionComponent(txId: SecureHash, componentGroup: ComponentGroupEnum, componentIndex: Int): DBTransactionComponent {
        return database.transaction {
            //todo conal - convert to use jpa criteria?
            var singleResult: DBTransactionComponent = session.createQuery(
                    "from ${DBTransactionComponent::class.java.name} " +
                            "where tx_id = :txId and component_group_index = :componentGroupIndex and component_group_leaf_index = :componentIndex",
                    DBTransactionComponent::class.java)
                    .setParameter("txId", txId.toString())
                    .setParameter("componentGroupIndex", componentGroup.name)
                    .setParameter("componentIndex", componentIndex)
                    .singleResult
            singleResult
        }
    }

    private fun onNewTx(transaction: SignedTransaction): Boolean {
        updatesPublisher.bufferUntilDatabaseCommit().onNext(transaction)
        return true
    }

    override fun getTransaction(id: SecureHash): SignedTransaction? {
        return database.transaction {
            txStorage.content[id]?.let { if (it.status.isVerified()) it.toSignedTx() else null }
        }
    }

    override fun addUnverifiedTransaction(transaction: SignedTransaction) {
        database.transaction {
            txStorage.locked {
                val cacheValue = TxCacheValue(transaction, status = TransactionStatus.UNVERIFIED)
                val added = addWithDuplicatesAllowed(transaction.id, cacheValue)
                if (added) {
                    logger.debug { "Transaction ${transaction.id} recorded as unverified." }
                } else {
                    logger.info("Transaction ${transaction.id} already exists so no need to record.")
                }
            }
        }
    }

    override fun getTransactionInternal(id: SecureHash): Pair<SignedTransaction, Boolean>? {
        return database.transaction {
            txStorage.content[id]?.let { it.toSignedTx() to it.status.isVerified() }
        }
    }

    private val updatesPublisher = PublishSubject.create<SignedTransaction>().toSerialized()
    override val updates: Observable<SignedTransaction> = updatesPublisher.wrapWithDatabaseTransaction()

    override fun track(): DataFeed<List<SignedTransaction>, SignedTransaction> {
        return database.transaction {
            txStorage.locked {
                DataFeed(snapshot(), updates.bufferUntilSubscribed())
            }
        }
    }

    override fun trackTransaction(id: SecureHash): CordaFuture<SignedTransaction> {
        val (transaction, warning) = trackTransactionInternal(id)
        warning?.also { log.warn(it) }
        return transaction
    }

    /**
     * @return a pair of the signed transaction, and a string containing any warning.
     */
    internal fun trackTransactionInternal(id: SecureHash): Pair<CordaFuture<SignedTransaction>, String?> {
        val warning: String? = if (contextTransactionOrNull != null) {
            TRANSACTION_ALREADY_IN_PROGRESS_WARNING
        } else {
            null
        }

        return Pair(trackTransactionWithNoWarning(id), warning)
    }

    override fun trackTransactionWithNoWarning(id: SecureHash): CordaFuture<SignedTransaction> {
        val updateFuture = updates.filter { it.id == id }.toFuture()
        return database.transaction {
            txStorage.locked {
                val existingTransaction = getTransaction(id)
                if (existingTransaction == null) {
                    updateFuture
                } else {
                    updateFuture.cancel(false)
                    doneFuture(existingTransaction)
                }
            }
        }
    }

    @VisibleForTesting
    val transactions: List<SignedTransaction>
        get() = database.transaction { snapshot() }

    private fun snapshot(): List<SignedTransaction> {
        return txStorage.content.allPersisted.use {
            it.filter { it.second.status.isVerified() }.map { it.second.toSignedTx() }.toList()
        }
    }

    // Cache value type to just store the immutable bits of a signed transaction plus conversion helpers
    private data class TxCacheValue(
            val txBits: SerializedBytes<CoreTransaction>,
            val sigs: List<net.corda.core.crypto.TransactionSignature>,
            val status: TransactionStatus
    ) {
        constructor(stx: SignedTransaction, status: TransactionStatus) : this(
                stx.txBits,
                Collections.unmodifiableList(stx.sigs),
                status)

        fun toSignedTx() = SignedTransaction(txBits, sigs)
    }
}

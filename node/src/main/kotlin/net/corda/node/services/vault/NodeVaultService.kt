package net.corda.node.services.vault

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import com.google.common.annotations.VisibleForTesting
import io.requery.PersistenceException
import io.requery.kotlin.eq
import io.requery.kotlin.notNull
import io.requery.query.RowExpression
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.containsAny
import net.corda.core.crypto.toBase58String
import net.corda.core.internal.ThreadBox
import net.corda.core.internal.tee
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.StatesNotAvailableException
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.vault.IQueryCriteriaParser
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.node.services.vault.SortAttribute
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.SerializationDefaults.STORAGE_CONTEXT
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.CoreTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.toNonEmptySet
import net.corda.core.utilities.trace
import net.corda.node.services.database.RequeryConfiguration
import net.corda.node.services.database.parserTransactionIsolationLevel
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.services.vault.schemas.requery.Models
import net.corda.node.services.vault.schemas.requery.VaultSchema
import net.corda.node.services.vault.schemas.requery.VaultStatesEntity
import net.corda.node.services.vault.schemas.requery.VaultTxnNoteEntity
import net.corda.node.utilities.bufferUntilDatabaseCommit
import net.corda.node.utilities.wrapWithDatabaseTransaction
import rx.Observable
import rx.subjects.PublishSubject
import java.security.PublicKey
import java.util.*
import javax.persistence.criteria.Predicate

/**
 * Currently, the node vault service is a very simple RDBMS backed implementation.  It will change significantly when
 * we add further functionality as the design for the vault and vault service matures.
 *
 * This class needs database transactions to be in-flight during method calls and init, and will throw exceptions if
 * this is not the case.
 *
 * TODO: move query / filter criteria into the database query.
 * TODO: keep an audit trail with time stamps of previously unconsumed states "as of" a particular point in time.
 * TODO: have transaction storage do some caching.
 */
class NodeVaultService(private val services: ServiceHub, dataSourceProperties: Properties, databaseProperties: Properties?) : SingletonSerializeAsToken(), VaultService {

    private companion object {
        val log = loggerFor<NodeVaultService>()

        // Define composite primary key used in Requery Expression
        val stateRefCompositeColumn: RowExpression = RowExpression.of(listOf(VaultStatesEntity.TX_ID, VaultStatesEntity.INDEX))
    }

    val configuration = RequeryConfiguration(dataSourceProperties, databaseProperties = databaseProperties ?: Properties())
    val session = configuration.sessionForModel(Models.VAULT)
    private val transactionIsolationLevel = parserTransactionIsolationLevel(databaseProperties?.getProperty("transactionIsolationLevel") ?:"")

    private class InnerState {
        val _updatesPublisher = PublishSubject.create<Vault.Update<ContractState>>()!!
        val _rawUpdatesPublisher = PublishSubject.create<Vault.Update<ContractState>>()!!
        val _updatesInDbTx = _updatesPublisher.wrapWithDatabaseTransaction().asObservable()!!

        // For use during publishing only.
        val updatesPublisher: rx.Observer<Vault.Update<ContractState>> get() = _updatesPublisher.bufferUntilDatabaseCommit().tee(_rawUpdatesPublisher)
    }

    private val mutex = ThreadBox(InnerState())

    private fun recordUpdate(update: Vault.Update<ContractState>): Vault.Update<ContractState> {
        if (!update.isEmpty()) {
            val producedStateRefs = update.produced.map { it.ref }
            val producedStateRefsMap = update.produced.associateBy { it.ref }
            val consumedStateRefs = update.consumed.map { it.ref }
            log.trace { "Removing $consumedStateRefs consumed contract states and adding $producedStateRefs produced contract states to the database." }

            session.withTransaction(transactionIsolationLevel) {
                producedStateRefsMap.forEach { it ->
                    val state = VaultStatesEntity().apply {
                        txId = it.key.txhash.toString()
                        index = it.key.index
                        stateStatus = Vault.StateStatus.UNCONSUMED
                        contractStateClassName = it.value.state.data.javaClass.name
                        contractState = it.value.state.serialize(context = STORAGE_CONTEXT).bytes
                        notaryName = it.value.state.notary.name.toString()
                        notaryKey = it.value.state.notary.owningKey.toBase58String()
                        recordedTime = services.clock.instant()
                    }
                    insert(state)
                }
                // TODO: awaiting support of UPDATE WHERE <Composite key> IN in Requery DSL
                consumedStateRefs.forEach { stateRef ->
                    val queryKey = io.requery.proxy.CompositeKey(mapOf(VaultStatesEntity.TX_ID to stateRef.txhash.toString(),
                            VaultStatesEntity.INDEX to stateRef.index))
                    val state = findByKey(VaultStatesEntity::class, queryKey)
                    state?.run {
                        stateStatus = Vault.StateStatus.CONSUMED
                        consumedTime = services.clock.instant()
                        // remove lock (if held)
                        if (lockId != null) {
                            lockId = null
                            lockUpdateTime = services.clock.instant()
                            log.trace("Releasing soft lock on consumed state: $stateRef")
                        }
                        update(state)
                    }
                }
            }
        }
        return update
    }

    override val rawUpdates: Observable<Vault.Update<ContractState>>
        get() = mutex.locked { _rawUpdatesPublisher }

    override val updates: Observable<Vault.Update<ContractState>>
        get() = mutex.locked { _updatesInDbTx }

    override val updatesPublisher: PublishSubject<Vault.Update<ContractState>>
        get() = mutex.locked { _updatesPublisher }

    /**
     * Splits the provided [txns] into batches of [WireTransaction] and [NotaryChangeWireTransaction].
     * This is required because the batches get aggregated into single updates, and we want to be able to
     * indicate whether an update consists entirely of regular or notary change transactions, which may require
     * different processing logic.
     */
    override fun notifyAll(txns: Iterable<CoreTransaction>) {
        // It'd be easier to just group by type, but then we'd lose ordering.
        val regularTxns = mutableListOf<WireTransaction>()
        val notaryChangeTxns = mutableListOf<NotaryChangeWireTransaction>()

        for (tx in txns) {
            when (tx) {
                is WireTransaction -> {
                    regularTxns.add(tx)
                    if (notaryChangeTxns.isNotEmpty()) {
                        notifyNotaryChange(notaryChangeTxns.toList())
                        notaryChangeTxns.clear()
                    }
                }
                is NotaryChangeWireTransaction -> {
                    notaryChangeTxns.add(tx)
                    if (regularTxns.isNotEmpty()) {
                        notifyRegular(regularTxns.toList())
                        regularTxns.clear()
                    }
                }
            }
        }

        if (regularTxns.isNotEmpty()) notifyRegular(regularTxns.toList())
        if (notaryChangeTxns.isNotEmpty()) notifyNotaryChange(notaryChangeTxns.toList())
    }

    private fun notifyRegular(txns: Iterable<WireTransaction>) {
        val ourKeys = services.keyManagementService.keys
        fun makeUpdate(tx: WireTransaction): Vault.Update<ContractState> {
            val ourNewStates = tx.outputs.
                    filter { isRelevant(it.data, ourKeys) }.
                    map { tx.outRef<ContractState>(it.data) }

            // Retrieve all unconsumed states for this transaction's inputs
            val consumedStates = loadStates(tx.inputs)

            // Is transaction irrelevant?
            if (consumedStates.isEmpty() && ourNewStates.isEmpty()) {
                log.trace { "tx ${tx.id} was irrelevant to this vault, ignoring" }
                return Vault.NoUpdate
            }

            return Vault.Update(consumedStates, ourNewStates.toHashSet())
        }

        val netDelta = txns.fold(Vault.NoUpdate) { netDelta, txn -> netDelta + makeUpdate(txn) }
        processAndNotify(netDelta)
    }

    private fun notifyNotaryChange(txns: Iterable<NotaryChangeWireTransaction>) {
        val ourKeys = services.keyManagementService.keys
        fun makeUpdate(tx: NotaryChangeWireTransaction): Vault.Update<ContractState> {
            // We need to resolve the full transaction here because outputs are calculated from inputs
            // We also can't do filtering beforehand, since output encumbrance pointers get recalculated based on
            // input positions
            val ltx = tx.resolve(services, emptyList())

            val (consumedStateAndRefs, producedStates) = ltx.inputs.
                    zip(ltx.outputs).
                    filter {
                        (_, output) ->
                        isRelevant(output.data, ourKeys)
                    }.
                    unzip()

            val producedStateAndRefs = producedStates.map { ltx.outRef<ContractState>(it.data) }

            if (consumedStateAndRefs.isEmpty() && producedStateAndRefs.isEmpty()) {
                log.trace { "tx ${tx.id} was irrelevant to this vault, ignoring" }
                return Vault.NoNotaryUpdate
            }

            return Vault.Update(consumedStateAndRefs.toHashSet(), producedStateAndRefs.toHashSet(), null, Vault.UpdateType.NOTARY_CHANGE)
        }

        val netDelta = txns.fold(Vault.NoNotaryUpdate) { netDelta, txn -> netDelta + makeUpdate(txn) }
        processAndNotify(netDelta)
    }

    private fun loadStates(refs: Collection<StateRef>): HashSet<StateAndRef<ContractState>> {
        val states = HashSet<StateAndRef<ContractState>>()
        if (refs.isNotEmpty()) {
            session.withTransaction(transactionIsolationLevel) {
                val result = select(VaultStatesEntity::class).
                        where(stateRefCompositeColumn.`in`(stateRefArgs(refs))).
                        and(VaultSchema.VaultStates::stateStatus eq Vault.StateStatus.UNCONSUMED)
                result.get().forEach {
                    val txHash = SecureHash.parse(it.txId)
                    val index = it.index
                    val state = it.contractState.deserialize<TransactionState<ContractState>>(context = STORAGE_CONTEXT)
                    states.add(StateAndRef(state, StateRef(txHash, index)))
                }
            }
        }
        return states
    }

    private fun processAndNotify(update: Vault.Update<ContractState>) {
        if (!update.isEmpty()) {
            recordUpdate(update)
            mutex.locked {
                // flowId required by SoftLockManager to perform auto-registration of soft locks for new states
                val uuid = (Strand.currentStrand() as? FlowStateMachineImpl<*>)?.id?.uuid
                val vaultUpdate = if (uuid != null) update.copy(flowId = uuid) else update
                updatesPublisher.onNext(vaultUpdate)
            }
        }
    }

    override fun addNoteToTransaction(txnId: SecureHash, noteText: String) {
        session.withTransaction(transactionIsolationLevel) {
            val txnNoteEntity = VaultTxnNoteEntity()
            txnNoteEntity.txId = txnId.toString()
            txnNoteEntity.note = noteText
            insert(txnNoteEntity)
        }
    }

    override fun getTransactionNotes(txnId: SecureHash): Iterable<String> {
        return session.withTransaction(transactionIsolationLevel) {
            (select(VaultSchema.VaultTxnNote::class) where (VaultSchema.VaultTxnNote::txId eq txnId.toString())).get().asIterable().map { it.note }
        }
    }

    @Throws(StatesNotAvailableException::class)
    override fun softLockReserve(lockId: UUID, stateRefs: NonEmptySet<StateRef>) {
        val softLockTimestamp = services.clock.instant()
        val stateRefArgs = stateRefArgs(stateRefs)
        try {
            session.withTransaction(transactionIsolationLevel) {
                val updatedRows = update(VaultStatesEntity::class)
                        .set(VaultStatesEntity.LOCK_ID, lockId.toString())
                        .set(VaultStatesEntity.LOCK_UPDATE_TIME, softLockTimestamp)
                        .where(VaultStatesEntity.STATE_STATUS eq Vault.StateStatus.UNCONSUMED)
                        .and((VaultStatesEntity.LOCK_ID eq lockId.toString()) or (VaultStatesEntity.LOCK_ID.isNull()))
                        .and(stateRefCompositeColumn.`in`(stateRefArgs)).get().value()
                if (updatedRows > 0 && updatedRows == stateRefs.size) {
                    log.trace("Reserving soft lock states for $lockId: $stateRefs")
                    FlowStateMachineImpl.currentStateMachine()?.hasSoftLockedStates = true
                } else {
                    // revert partial soft locks
                    val revertUpdatedRows = update(VaultStatesEntity::class)
                            .set(VaultStatesEntity.LOCK_ID, null)
                            .where(VaultStatesEntity.LOCK_UPDATE_TIME eq softLockTimestamp)
                            .and(VaultStatesEntity.LOCK_ID eq lockId.toString())
                            .and(stateRefCompositeColumn.`in`(stateRefArgs)).get().value()
                    if (revertUpdatedRows > 0) {
                        log.trace("Reverting $revertUpdatedRows partially soft locked states for $lockId")
                    }
                    throw StatesNotAvailableException("Attempted to reserve $stateRefs for $lockId but only $updatedRows rows available")
                }
            }
        } catch (e: PersistenceException) {
            log.error("""soft lock update error attempting to reserve states for $lockId and $stateRefs")
                    $e.
                """)
            if (e.cause is StatesNotAvailableException) throw (e.cause as StatesNotAvailableException)
        }
    }

    override fun softLockRelease(lockId: UUID, stateRefs: NonEmptySet<StateRef>?) {
        if (stateRefs == null) {
            session.withTransaction(transactionIsolationLevel) {
                val update = update(VaultStatesEntity::class)
                        .set(VaultStatesEntity.LOCK_ID, null)
                        .set(VaultStatesEntity.LOCK_UPDATE_TIME, services.clock.instant())
                        .where(VaultStatesEntity.STATE_STATUS eq Vault.StateStatus.UNCONSUMED)
                        .and(VaultStatesEntity.LOCK_ID eq lockId.toString()).get()
                if (update.value() > 0) {
                    log.trace("Releasing ${update.value()} soft locked states for $lockId")
                }
            }
        } else {
            try {
                session.withTransaction(transactionIsolationLevel) {
                    val updatedRows = update(VaultStatesEntity::class)
                            .set(VaultStatesEntity.LOCK_ID, null)
                            .set(VaultStatesEntity.LOCK_UPDATE_TIME, services.clock.instant())
                            .where(VaultStatesEntity.STATE_STATUS eq Vault.StateStatus.UNCONSUMED)
                            .and(VaultStatesEntity.LOCK_ID eq lockId.toString())
                            .and(stateRefCompositeColumn.`in`(stateRefArgs(stateRefs))).get().value()
                    if (updatedRows > 0) {
                        log.trace("Releasing $updatedRows soft locked states for $lockId and stateRefs $stateRefs")
                    }
                }
            } catch (e: PersistenceException) {
                log.error("""soft lock update error attempting to release states for $lockId and $stateRefs")
                    $e.
                """)
            }
        }
    }

    // TODO We shouldn't need to rewrite the query if we could modify the defaults.
    private class QueryEditor<out T : ContractState>(val services: ServiceHub,
                                                     val lockId: UUID,
                                                     val contractType: Class<out T>) : IQueryCriteriaParser {
        var alreadyHasVaultQuery: Boolean = false
        var modifiedCriteria: QueryCriteria = QueryCriteria.VaultQueryCriteria(contractStateTypes = setOf(contractType),
                softLockingCondition = QueryCriteria.SoftLockingCondition(QueryCriteria.SoftLockingType.UNLOCKED_AND_SPECIFIED, listOf(lockId)),
                status = Vault.StateStatus.UNCONSUMED)

        override fun parseCriteria(criteria: QueryCriteria.CommonQueryCriteria): Collection<Predicate> {
            modifiedCriteria = criteria
            return emptyList()
        }

        override fun parseCriteria(criteria: QueryCriteria.FungibleAssetQueryCriteria): Collection<Predicate> {
            modifiedCriteria = criteria
            return emptyList()
        }

        override fun parseCriteria(criteria: QueryCriteria.LinearStateQueryCriteria): Collection<Predicate> {
            modifiedCriteria = criteria
            return emptyList()
        }

        override fun <L : PersistentState> parseCriteria(criteria: QueryCriteria.VaultCustomQueryCriteria<L>): Collection<Predicate> {
            modifiedCriteria = criteria
            return emptyList()
        }

        override fun parseCriteria(criteria: QueryCriteria.VaultQueryCriteria): Collection<Predicate> {
            modifiedCriteria = criteria.copy(contractStateTypes = setOf(contractType),
                    softLockingCondition = QueryCriteria.SoftLockingCondition(QueryCriteria.SoftLockingType.UNLOCKED_AND_SPECIFIED, listOf(lockId)),
                    status = Vault.StateStatus.UNCONSUMED)
            alreadyHasVaultQuery = true
            return emptyList()
        }

        override fun parseOr(left: QueryCriteria, right: QueryCriteria): Collection<Predicate> {
            parse(left)
            val modifiedLeft = modifiedCriteria
            parse(right)
            val modifiedRight = modifiedCriteria
            modifiedCriteria = modifiedLeft.or(modifiedRight)
            return emptyList()
        }

        override fun parseAnd(left: QueryCriteria, right: QueryCriteria): Collection<Predicate> {
            parse(left)
            val modifiedLeft = modifiedCriteria
            parse(right)
            val modifiedRight = modifiedCriteria
            modifiedCriteria = modifiedLeft.and(modifiedRight)
            return emptyList()
        }

        override fun parse(criteria: QueryCriteria, sorting: Sort?): Collection<Predicate> {
            val basicQuery = modifiedCriteria
            criteria.visit(this)
            modifiedCriteria = if (alreadyHasVaultQuery) modifiedCriteria else criteria.and(basicQuery)
            return emptyList()
        }

        fun queryForEligibleStates(criteria: QueryCriteria): Vault.Page<T> {
            val sortAttribute = SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF)
            val sorter = Sort(setOf(Sort.SortColumn(sortAttribute, Sort.Direction.ASC)))
            parse(criteria, sorter)

            return services.vaultQueryService.queryBy(contractType, modifiedCriteria, sorter)
        }
    }


    @Suspendable
    @Throws(StatesNotAvailableException::class)
    override fun <T : FungibleAsset<U>, U : Any> tryLockFungibleStatesForSpending(lockId: UUID,
                                                                                  eligibleStatesQuery: QueryCriteria,
                                                                                  amount: Amount<U>,
                                                                                  contractType: Class<out T>): List<StateAndRef<T>> {
        if (amount.quantity == 0L) {
            return emptyList()
        }

        // TODO This helper code re-writes the query to alter the defaults on things such as soft locks
        // and then runs the query. Ideally we would not need to do this.
        val results = QueryEditor(services, lockId, contractType).queryForEligibleStates(eligibleStatesQuery)

        var claimedAmount = 0L
        val claimedStates = mutableListOf<StateAndRef<T>>()
        for (state in results.states) {
            val issuedAssetToken = state.state.data.amount.token
            if (issuedAssetToken.product == amount.token) {
                claimedStates += state
                claimedAmount += state.state.data.amount.quantity
                if (claimedAmount > amount.quantity) {
                    break
                }
            }
        }
        if (claimedStates.isEmpty() || claimedAmount < amount.quantity) {
            return emptyList()
        }
        softLockReserve(lockId, claimedStates.map { it.ref }.toNonEmptySet())
        return claimedStates
    }

    // TODO : Persists this in DB.
    private val authorisedUpgrade = mutableMapOf<StateRef, Class<out UpgradedContract<*, *>>>()

    override fun getAuthorisedContractUpgrade(ref: StateRef) = authorisedUpgrade[ref]

    override fun authoriseContractUpgrade(stateAndRef: StateAndRef<*>, upgradedContractClass: Class<out UpgradedContract<*, *>>) {
        val upgrade = upgradedContractClass.newInstance()
        if (upgrade.legacyContract != stateAndRef.state.data.contract.javaClass) {
            throw IllegalArgumentException("The contract state cannot be upgraded using provided UpgradedContract.")
        }
        authorisedUpgrade.put(stateAndRef.ref, upgradedContractClass)
    }

    override fun deauthoriseContractUpgrade(stateAndRef: StateAndRef<*>) {
        authorisedUpgrade.remove(stateAndRef.ref)
    }

    @VisibleForTesting
    internal fun isRelevant(state: ContractState, ourKeys: Set<PublicKey>) = when (state) {
        is OwnableState -> state.owner.owningKey.containsAny(ourKeys)
        is LinearState -> state.isRelevant(ourKeys)
        else -> ourKeys.intersect(state.participants.map { it.owningKey }).isNotEmpty()
    }

    /**
     * Helper method to generate a string formatted list of Composite Keys for Requery Expression clause
     */
    private fun stateRefArgs(stateRefs: Iterable<StateRef>): List<List<Any>> {
        return stateRefs.map { listOf("'${it.txhash}'", it.index) }
    }
}

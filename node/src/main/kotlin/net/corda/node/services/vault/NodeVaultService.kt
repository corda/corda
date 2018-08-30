/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.vault

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.*
import net.corda.core.messaging.DataFeed
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.KeyManagementService
import net.corda.core.node.services.StatesNotAvailableException
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultQueryException
import net.corda.core.node.services.vault.*
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.*
import net.corda.core.utilities.*
import net.corda.node.services.api.SchemaService
import net.corda.node.services.api.VaultServiceInternal
import net.corda.node.services.config.KB
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.schema.PersistentStateService
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.utilities.profiling.CacheTracing.Companion.wrap
import net.corda.node.utilities.profiling.CacheTracingConfig
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.bufferUntilDatabaseCommit
import net.corda.nodeapi.internal.persistence.currentDBSession
import net.corda.nodeapi.internal.persistence.wrapWithDatabaseTransaction
import org.hibernate.Session
import rx.Observable
import rx.subjects.PublishSubject
import java.security.PublicKey
import java.time.Clock
import java.time.Instant
import java.util.*
import javax.persistence.Tuple
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaUpdate
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root

private fun CriteriaBuilder.executeUpdate(session: Session, configure: Root<*>.(CriteriaUpdate<*>) -> Any?) = createCriteriaUpdate(VaultSchemaV1.VaultStates::class.java).let { update ->
    update.from(VaultSchemaV1.VaultStates::class.java).run { configure(update) }
    session.createQuery(update).executeUpdate()
}

/**
 * The vault service handles storage, retrieval and querying of states.
 *
 * This class needs database transactions to be in-flight during method calls and init, and will throw exceptions if
 * this is not the case.
 *
 * TODO: keep an audit trail with time stamps of previously unconsumed states "as of" a particular point in time.
 */
class NodeVaultService(
        private val clock: Clock,
        private val keyManagementService: KeyManagementService,
        private val servicesForResolution: ServicesForResolution,
        private val database: CordaPersistence,
        private val schemaService: SchemaService,
        transactionCacheSizeBytes: Long = NodeConfiguration.defaultTransactionCacheSize,
        cacheTraceConfig: CacheTracingConfig? = null
) : SingletonSerializeAsToken(), VaultServiceInternal {
    private companion object {
        private val log = contextLogger()
    }

    private class InnerState {
        val _updatesPublisher = PublishSubject.create<Vault.Update<ContractState>>()!!
        val _rawUpdatesPublisher = PublishSubject.create<Vault.Update<ContractState>>()!!
        val _updatesInDbTx = _updatesPublisher.wrapWithDatabaseTransaction().asObservable()!!

        // For use during publishing only.
        val updatesPublisher: rx.Observer<Vault.Update<ContractState>> get() = _updatesPublisher.bufferUntilDatabaseCommit().tee(_rawUpdatesPublisher)
    }

    private val concurrentBox = ConcurrentBox(InnerState())
    private lateinit var criteriaBuilder: CriteriaBuilder
    private val persistentStateService = PersistentStateService(schemaService)

    /**
     * Maintain a list of contract state interfaces to concrete types stored in the vault
     * for usage in generic queries of type queryBy<LinearState> or queryBy<FungibleState<*>>
     */
    private val contractStateTypeMappings = mutableMapOf<String, MutableSet<String>>()

    /**
     * This caches what states are in the vault for a particular transaction. Size the cache based on one entry per 8KB of transaction cache.
     * This size results in minimum of 1024.
     */
    private val producedStatesMapping = wrap(
            Caffeine.newBuilder().maximumSize(transactionCacheSizeBytes / 8.KB).build<SecureHash, BitSet>(),
            converter = { key: SecureHash -> longHash.hashBytes(key.bytes).asLong() },
            config = cacheTraceConfig,
            traceName = "vaulteservice"
    )

    private val longHash = com.google.common.hash.Hashing.sipHash24()

    override fun start() {
        criteriaBuilder = database.hibernateConfig.sessionFactoryForRegisteredSchemas.criteriaBuilder
        bootstrapContractStateTypes()
        rawUpdates.subscribe { update ->
            update.produced.forEach {
                val concreteType = it.state.data.javaClass
                log.trace { "State update of type: $concreteType" }
                val seen = contractStateTypeMappings.any { it.value.contains(concreteType.name) }
                if (!seen) {
                    val contractTypes = deriveContractTypes(concreteType)
                    contractTypes.map {
                        val contractStateType = contractStateTypeMappings.getOrPut(it.name) { mutableSetOf() }
                        contractStateType.add(concreteType.name)
                    }
                }
            }
        }
    }

    private fun recordUpdate(update: Vault.Update<ContractState>): Vault.Update<ContractState> {
        if (!update.isEmpty()) {
            val producedStateRefs = update.produced.map { it.ref }
            val producedStateRefsMap = update.produced.associateBy { it.ref }
            val consumedStateRefs = update.consumed.map { it.ref }
            log.trace { "Removing $consumedStateRefs consumed contract states and adding $producedStateRefs produced contract states to the database." }

            val session = currentDBSession()
            val now = clock.instant()
            producedStateRefsMap.forEach { stateAndRef ->
                val uuid = if (stateAndRef.value.state.data is FungibleAsset<*>) {
                    FlowStateMachineImpl.currentStateMachine()?.id?.uuid?.toString()
                } else null
                if (uuid != null) {
                    FlowStateMachineImpl.currentStateMachine()?.hasSoftLockedStates = true
                    log.trace { "Reserving soft lock for flow id $uuid and state ${stateAndRef.key}" }
                }
                val stateOnly = stateAndRef.value.state.data
                // TODO: Optimise this.
                //
                // For EVERY state to be committed to the vault, this checks whether it is spendable by the recording
                // node. The behaviour is as follows:
                //
                // 1) All vault updates marked as RELEVANT will, of, course all have isRelevant = true.
                // 2) For ALL_VISIBLE updates, those which are not relevant according to the relevancy rules will have isRelevant = false.
                //
                // This is useful when it comes to querying for fungible states, when we do not want non-relevant states
                // included in the result.
                //
                // The same functionality could be obtained by passing in a list of participants to the vault query,
                // however this:
                //
                // * requires a join on the participants table which results in slow queries
                // * states may flip from being non-relevant to relevant
                // * it's more complicated for CorDapp developers
                //
                // Adding a new column in the "VaultStates" table was considered the best approach.
                val keys = stateOnly.participants.map { it.owningKey }
                val isRelevant = isRelevant(stateOnly, keyManagementService.filterMyKeys(keys).toSet())
                val stateToAdd = VaultSchemaV1.VaultStates(
                        notary = stateAndRef.value.state.notary,
                        contractStateClassName = stateAndRef.value.state.data.javaClass.name,
                        stateStatus = Vault.StateStatus.UNCONSUMED,
                        recordedTime = clock.instant(),
                        isRelevant = if (isRelevant) Vault.RelevancyStatus.RELEVANT else Vault.RelevancyStatus.NOT_RELEVANT
                )
                stateToAdd.stateRef = PersistentStateRef(stateAndRef.key)
                session.save(stateToAdd)
            }
            if (consumedStateRefs.isNotEmpty()) {
                // We have to do this so that the session does not hold onto the prior version of the states status.  i.e.
                // it is not aware of this query.
                session.flush()
                session.clear()
                val criteriaBuilder = session.criteriaBuilder
                val updateQuery = criteriaBuilder.createCriteriaUpdate(VaultSchemaV1.VaultStates::class.java)
                val root = updateQuery.from(VaultSchemaV1.VaultStates::class.java)
                updateQuery.set(root.get<Vault.StateStatus>(VaultSchemaV1.VaultStates::stateStatus.name), Vault.StateStatus.CONSUMED)
                updateQuery.set(root.get<Instant>(VaultSchemaV1.VaultStates::consumedTime.name), now)
                updateQuery.set(root.get<String>(VaultSchemaV1.VaultStates::lockId.name), criteriaBuilder.nullLiteral(String::class.java))
                updateQuery.where(root.get<PersistentStateRef>(VaultSchemaV1.VaultStates::stateRef.name).`in`(consumedStateRefs.map { PersistentStateRef(it) }))
                session.createQuery(updateQuery).executeUpdate()
            }
        }
        return update
    }

    override val rawUpdates: Observable<Vault.Update<ContractState>>
        get() = concurrentBox.content._rawUpdatesPublisher

    override val updates: Observable<Vault.Update<ContractState>>
        get() = concurrentBox.content._updatesInDbTx

    /** Groups adjacent transactions into batches to generate separate net updates per transaction type. */
    override fun notifyAll(statesToRecord: StatesToRecord, txns: Iterable<CoreTransaction>) {
        if (statesToRecord == StatesToRecord.NONE || !txns.any()) {
            txns.forEach { producedStatesMapping.put(it.id, BitSet(0)) }
            return
        }
        val batch = mutableListOf<CoreTransaction>()

        fun flushBatch() {
            val updates = makeUpdates(batch, statesToRecord)
            processAndNotify(updates)
            batch.clear()
        }

        for (tx in txns) {
            if (batch.isNotEmpty() && tx.javaClass != batch.last().javaClass) {
                flushBatch()
            }
            batch.add(tx)
        }
        flushBatch()
    }

    private fun makeUpdates(batch: Iterable<CoreTransaction>, statesToRecord: StatesToRecord): List<Vault.Update<ContractState>> {
        fun makeUpdate(tx: WireTransaction): Vault.Update<ContractState>? {
            val outputsBitSet = BitSet(tx.outputs.size)
            val ourNewStates = when (statesToRecord) {
                StatesToRecord.NONE -> throw AssertionError("Should not reach here")
                StatesToRecord.ONLY_RELEVANT -> tx.outputs.withIndex().filter {
                    isRelevant(it.value.data, keyManagementService.filterMyKeys(tx.outputs.flatMap { it.data.participants.map { it.owningKey } }).toSet())
                }
                StatesToRecord.ALL_VISIBLE -> tx.outputs.withIndex()
            }.map {
                outputsBitSet[it.index] = true
                tx.outRef<ContractState>(it.index)
            }
            producedStatesMapping.put(tx.id, outputsBitSet)

            // Retrieve all unconsumed states for this transaction's inputs
            val consumedStates = loadStatesWithVaultFilter(tx.inputs)

            // Is transaction irrelevant?
            if (consumedStates.isEmpty() && ourNewStates.isEmpty()) {
                log.trace { "tx ${tx.id} was irrelevant to this vault, ignoring" }
                return null
            }

            return Vault.Update(consumedStates.toSet(), ourNewStates.toSet())
        }

        fun resolveAndMakeUpdate(tx: CoreTransaction): Vault.Update<ContractState>? {
            // We need to resolve the full transaction here because outputs are calculated from inputs
            // We also can't do filtering beforehand, since for notary change transactions output encumbrance pointers
            // get recalculated based on input positions.
            val ltx: FullTransaction = when (tx) {
                is NotaryChangeWireTransaction -> tx.resolve(servicesForResolution, emptyList())
                is ContractUpgradeWireTransaction -> tx.resolve(servicesForResolution, emptyList())
                else -> throw IllegalArgumentException("Unsupported transaction type: ${tx.javaClass.name}")
            }
            val myKeys by lazy { keyManagementService.filterMyKeys(ltx.outputs.flatMap { it.data.participants.map { it.owningKey } }) }
            val (consumedStateAndRefs, producedStates) = ltx.inputs.zip(ltx.outputs).filter { (_, output) ->
                if (statesToRecord == StatesToRecord.ONLY_RELEVANT) {
                    isRelevant(output.data, myKeys.toSet())
                } else {
                    true
                }
            }.unzip()

            val producedStateAndRefs = producedStates.map { ltx.outRef<ContractState>(it.data) }
            if (consumedStateAndRefs.isEmpty() && producedStateAndRefs.isEmpty()) {
                log.trace { "tx ${tx.id} was irrelevant to this vault, ignoring" }
                return null
            }

            val updateType = if (tx is ContractUpgradeWireTransaction) {
                Vault.UpdateType.CONTRACT_UPGRADE
            } else {
                Vault.UpdateType.NOTARY_CHANGE
            }
            return Vault.Update(consumedStateAndRefs.toSet(), producedStateAndRefs.toSet(), null, updateType)
        }


        return batch.mapNotNull {
            if (it is WireTransaction) makeUpdate(it) else resolveAndMakeUpdate(it)
        }
    }

    private fun loadStatesWithVaultFilter(refs: Collection<StateRef>): Collection<StateAndRef<ContractState>> {
        val states = mutableSetOf<StateRef>()
        if (refs.isNotEmpty()) {
            // Eliminate any that we have cached.
            val uncachedTx = refs.groupBy { it.txhash }.filter {
                val cachedProduced = producedStatesMapping.getIfPresent(it.key)
                if (cachedProduced == null) {
                    true
                } else {
                    // Add to results.
                    states.addAll(it.value.filter { cachedProduced[it.index] })
                    false
                }
            }
            // If we have uncached, go and get those in a single query
            if (uncachedTx.isNotEmpty()) {
                // Select all rows with matching stateRefs
                val criteriaQuery = criteriaBuilder.createTupleQuery()
                val root = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
                criteriaQuery.multiselect(root.get<PersistentStateRef>(VaultSchemaV1.VaultStates::stateRef.name))
                val txIds = uncachedTx.keys.map { it.toString() }
                val compositeKey = root.get<PersistentStateRef>(VaultSchemaV1.VaultStates::stateRef.name).get<String>(PersistentStateRef::txId.name)
                criteriaQuery.where(compositeKey.`in`(txIds))

                // prepare query for execution
                val session = currentDBSession()
                val query = session.createQuery(criteriaQuery)

                // execution.  For each transaction:
                query.resultList.map { it[0] as PersistentStateRef }.groupBy { it.txId }.forEach {
                    // Record what states were found, in the cache and the results.
                    val secureHash = SecureHash.parse(it.key)
                    val outputsBitSet = BitSet(0) // This is auto-expanded when setting higher bits.
                    states.addAll(it.value.map {
                        outputsBitSet[it.index] = true
                        StateRef(secureHash, it.index)
                    })
                    // Cache the result for future lookups.
                    producedStatesMapping.put(secureHash, outputsBitSet)
                }
            }
        }
        return servicesForResolution.loadStates(states)
    }

    private fun processAndNotify(updates: List<Vault.Update<ContractState>>) {
        if (updates.isEmpty()) return
        val netUpdate = updates.reduce { update1, update2 -> update1 + update2 }
        if (!netUpdate.isEmpty()) {
            recordUpdate(netUpdate)
            concurrentBox.concurrent {
                // flowId was required by SoftLockManager to perform auto-registration of soft locks for new states
                val uuid = (Strand.currentStrand() as? FlowStateMachineImpl<*>)?.id?.uuid
                val vaultUpdate = if (uuid != null) netUpdate.copy(flowId = uuid) else netUpdate
                persistentStateService.persist(vaultUpdate.produced)
                updatesPublisher.onNext(vaultUpdate)
            }
        }
    }

    override fun addNoteToTransaction(txnId: SecureHash, noteText: String) {
        database.transaction {
            val txnNoteEntity = VaultSchemaV1.VaultTxnNote(txnId.toString(), noteText)
            currentDBSession().save(txnNoteEntity)
        }
    }

    override fun getTransactionNotes(txnId: SecureHash): Iterable<String> {
        return database.transaction {
            val session = currentDBSession()
            val criteriaBuilder = session.criteriaBuilder
            val criteriaQuery = criteriaBuilder.createQuery(VaultSchemaV1.VaultTxnNote::class.java)
            val vaultStates = criteriaQuery.from(VaultSchemaV1.VaultTxnNote::class.java)
            val txIdPredicate = criteriaBuilder.equal(vaultStates.get<Vault.StateStatus>(VaultSchemaV1.VaultTxnNote::txId.name), txnId.toString())
            criteriaQuery.where(txIdPredicate)
            val results = session.createQuery(criteriaQuery).resultList
            results.asIterable().map { it.note ?: "" }
        }
    }

    @Throws(StatesNotAvailableException::class)
    override fun softLockReserve(lockId: UUID, stateRefs: NonEmptySet<StateRef>) {
        val softLockTimestamp = clock.instant()
        try {
            val session = currentDBSession()
            val criteriaBuilder = session.criteriaBuilder
            fun execute(configure: Root<*>.(CriteriaUpdate<*>, Array<Predicate>) -> Any?) = criteriaBuilder.executeUpdate(session) { update ->
                val persistentStateRefs = stateRefs.map { PersistentStateRef(it.txhash.bytes.toHexString(), it.index) }
                val compositeKey = get<PersistentStateRef>(VaultSchemaV1.VaultStates::stateRef.name)
                val stateRefsPredicate = criteriaBuilder.and(compositeKey.`in`(persistentStateRefs))
                configure(update, arrayOf(stateRefsPredicate))
            }

            val updatedRows = execute { update, commonPredicates ->
                val stateStatusPredication = criteriaBuilder.equal(get<Vault.StateStatus>(VaultSchemaV1.VaultStates::stateStatus.name), Vault.StateStatus.UNCONSUMED)
                val lockIdPredicate = criteriaBuilder.or(get<String>(VaultSchemaV1.VaultStates::lockId.name).isNull,
                        criteriaBuilder.equal(get<String>(VaultSchemaV1.VaultStates::lockId.name), lockId.toString()))
                update.set(get<String>(VaultSchemaV1.VaultStates::lockId.name), lockId.toString())
                update.set(get<Instant>(VaultSchemaV1.VaultStates::lockUpdateTime.name), softLockTimestamp)
                update.where(stateStatusPredication, lockIdPredicate, *commonPredicates)
            }
            if (updatedRows > 0 && updatedRows == stateRefs.size) {
                log.trace { "Reserving soft lock states for $lockId: $stateRefs" }
                FlowStateMachineImpl.currentStateMachine()?.hasSoftLockedStates = true
            } else {
                // revert partial soft locks
                val revertUpdatedRows = execute { update, commonPredicates ->
                    val lockIdPredicate = criteriaBuilder.equal(get<String>(VaultSchemaV1.VaultStates::lockId.name), lockId.toString())
                    val lockUpdateTime = criteriaBuilder.equal(get<Instant>(VaultSchemaV1.VaultStates::lockUpdateTime.name), softLockTimestamp)
                    update.set(get<String>(VaultSchemaV1.VaultStates::lockId.name), criteriaBuilder.nullLiteral(String::class.java))
                    update.where(lockUpdateTime, lockIdPredicate, *commonPredicates)
                }
                if (revertUpdatedRows > 0) {
                    log.trace { "Reverting $revertUpdatedRows partially soft locked states for $lockId" }
                }
                throw StatesNotAvailableException("Attempted to reserve $stateRefs for $lockId but only $updatedRows rows available")
            }
        } catch (e: Exception) {
            log.error("""soft lock update error attempting to reserve states for $lockId and $stateRefs")
                    $e.
                """)
            if (e.cause is StatesNotAvailableException) throw (e.cause as StatesNotAvailableException)
            throw e
        }
    }

    override fun softLockRelease(lockId: UUID, stateRefs: NonEmptySet<StateRef>?) {
        val softLockTimestamp = clock.instant()
        val session = currentDBSession()
        val criteriaBuilder = session.criteriaBuilder
        fun execute(configure: Root<*>.(CriteriaUpdate<*>, Array<Predicate>) -> Any?) = criteriaBuilder.executeUpdate(session) { update ->
            val stateStatusPredication = criteriaBuilder.equal(get<Vault.StateStatus>(VaultSchemaV1.VaultStates::stateStatus.name), Vault.StateStatus.UNCONSUMED)
            val lockIdPredicate = criteriaBuilder.equal(get<String>(VaultSchemaV1.VaultStates::lockId.name), lockId.toString())
            update.set<String>(get<String>(VaultSchemaV1.VaultStates::lockId.name), criteriaBuilder.nullLiteral(String::class.java))
            update.set(get<Instant>(VaultSchemaV1.VaultStates::lockUpdateTime.name), softLockTimestamp)
            configure(update, arrayOf(stateStatusPredication, lockIdPredicate))
        }
        if (stateRefs == null) {
            val update = execute { update, commonPredicates ->
                update.where(*commonPredicates)
            }
            if (update > 0) {
                log.trace { "Releasing $update soft locked states for $lockId" }
            }
        } else {
            try {
                val updatedRows = execute { update, commonPredicates ->
                    val persistentStateRefs = stateRefs.map { PersistentStateRef(it.txhash.bytes.toHexString(), it.index) }
                    val compositeKey = get<PersistentStateRef>(VaultSchemaV1.VaultStates::stateRef.name)
                    val stateRefsPredicate = criteriaBuilder.and(compositeKey.`in`(persistentStateRefs))
                    update.where(*commonPredicates, stateRefsPredicate)
                }
                if (updatedRows > 0) {
                    log.trace { "Releasing $updatedRows soft locked states for $lockId and stateRefs $stateRefs" }
                }
            } catch (e: Exception) {
                log.error("""soft lock update error attempting to release states for $lockId and $stateRefs")
                    $e.
                """)
                throw e
            }
        }
    }

    @Suspendable
    @Throws(StatesNotAvailableException::class)
    override fun <T : FungibleAsset<U>, U : Any> tryLockFungibleStatesForSpending(lockId: UUID,
                                                                                  eligibleStatesQuery: QueryCriteria,
                                                                                  amount: Amount<U>,
                                                                                  contractStateType: Class<out T>): List<StateAndRef<T>> {
        if (amount.quantity == 0L) {
            return emptyList()
        }

        // Enrich QueryCriteria with additional default attributes (such as soft locks).
        // We only want to return RELEVANT states here.
        val sortAttribute = SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF)
        val sorter = Sort(setOf(Sort.SortColumn(sortAttribute, Sort.Direction.ASC)))
        val enrichedCriteria = QueryCriteria.VaultQueryCriteria(
                contractStateTypes = setOf(contractStateType),
                softLockingCondition = QueryCriteria.SoftLockingCondition(QueryCriteria.SoftLockingType.UNLOCKED_AND_SPECIFIED, listOf(lockId)),
                isRelevant = Vault.RelevancyStatus.RELEVANT
        )
        val results = queryBy(contractStateType, enrichedCriteria.and(eligibleStatesQuery), sorter)

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

    @VisibleForTesting
    internal fun isRelevant(state: ContractState, myKeys: Set<PublicKey>): Boolean {
        val keysToCheck = when (state) {
        // Sometimes developers forget to add the owning key to participants for OwnableStates.
        // TODO: This logic should probably be moved to OwnableState so we can just do a simple intersection here.
            is OwnableState -> (state.participants.map { it.owningKey } + state.owner.owningKey).toSet()
            else -> state.participants.map { it.owningKey }
        }
        return keysToCheck.any { it in myKeys }
    }

    @Throws(VaultQueryException::class)
    override fun <T : ContractState> _queryBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractStateType: Class<out T>): Vault.Page<T> {
        return _queryBy(criteria, paging, sorting, contractStateType, false)
    }

    @Throws(VaultQueryException::class)
    private fun <T : ContractState> _queryBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractStateType: Class<out T>, skipPagingChecks: Boolean): Vault.Page<T> {
        log.debug { "Vault Query for contract type: $contractStateType, criteria: $criteria, pagination: $paging, sorting: $sorting" }
        return database.transaction {
            // calculate total results where a page specification has been defined
            var totalStates = -1L
            if (!skipPagingChecks && !paging.isDefault) {
                val count = builder { VaultSchemaV1.VaultStates::recordedTime.count() }
                val countCriteria = QueryCriteria.VaultCustomQueryCriteria(count, Vault.StateStatus.ALL)
                val results = _queryBy(criteria.and(countCriteria), PageSpecification(), Sort(emptyList()), contractStateType, true)  // only skip pagination checks for total results count query
                totalStates = results.otherResults.last() as Long
            }

            val session = getSession()

            val criteriaQuery = criteriaBuilder.createQuery(Tuple::class.java)
            val queryRootVaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
            // TODO: revisit (use single instance of parser for all queries)
            val criteriaParser = HibernateQueryCriteriaParser(contractStateType, contractStateTypeMappings, criteriaBuilder, criteriaQuery, queryRootVaultStates)

            // parse criteria and build where predicates
            criteriaParser.parse(criteria, sorting)

            // prepare query for execution
            val query = session.createQuery(criteriaQuery)

            // pagination checks
            if (!skipPagingChecks && !paging.isDefault) {
                // pagination
                if (paging.pageNumber < DEFAULT_PAGE_NUM) throw VaultQueryException("Page specification: invalid page number ${paging.pageNumber} [page numbers start from $DEFAULT_PAGE_NUM]")
                if (paging.pageSize < 1) throw VaultQueryException("Page specification: invalid page size ${paging.pageSize} [must be a value between 1 and $MAX_PAGE_SIZE]")
            }

            // For both SQLServer and PostgresSQL, firstResult must be >= 0. So we set a floor at 0.
            // TODO: This is a catch-all solution. But why is the default pageNumber set to be -1 in the first place?
            // Even if we set the default pageNumber to be 1 instead, that may not cover the non-default cases.
            // So the floor may be necessary anyway.
            query.firstResult = maxOf(0, (paging.pageNumber - 1) * paging.pageSize)
            query.maxResults = paging.pageSize + 1  // detection too many results

            // execution
            val results = query.resultList

            // final pagination check (fail-fast on too many results when no pagination specified)
            if (!skipPagingChecks && paging.isDefault && results.size > DEFAULT_PAGE_SIZE) {
                throw VaultQueryException("There are ${results.size} results, which exceeds the limit of $DEFAULT_PAGE_SIZE for queries that do not specify paging. In order to retrieve these results, provide a `PageSpecification(pageNumber, pageSize)` to the method invoked.")
            }
            val statesAndRefs: MutableList<StateAndRef<T>> = mutableListOf()
            val statesMeta: MutableList<Vault.StateMetadata> = mutableListOf()
            val otherResults: MutableList<Any> = mutableListOf()
            val stateRefs = mutableSetOf<StateRef>()

            results.asSequence()
                    .forEachIndexed { index, result ->
                        if (result[0] is VaultSchemaV1.VaultStates) {
                            if (!paging.isDefault && index == paging.pageSize) // skip last result if paged
                                return@forEachIndexed
                            val vaultState = result[0] as VaultSchemaV1.VaultStates
                            val stateRef = StateRef(SecureHash.parse(vaultState.stateRef!!.txId), vaultState.stateRef!!.index)
                            stateRefs.add(stateRef)
                            statesMeta.add(Vault.StateMetadata(stateRef,
                                    vaultState.contractStateClassName,
                                    vaultState.recordedTime,
                                    vaultState.consumedTime,
                                    vaultState.stateStatus,
                                    vaultState.notary,
                                    vaultState.lockId,
                                    vaultState.lockUpdateTime,
                                    vaultState.isRelevant))
                        } else {
                            // TODO: improve typing of returned other results
                            log.debug { "OtherResults: ${Arrays.toString(result.toArray())}" }
                            otherResults.addAll(result.toArray().asList())
                        }
                    }
            if (stateRefs.isNotEmpty())
                statesAndRefs.addAll(uncheckedCast(servicesForResolution.loadStates(stateRefs)))

            Vault.Page(states = statesAndRefs, statesMetadata = statesMeta, stateTypes = criteriaParser.stateTypes, totalStatesAvailable = totalStates, otherResults = otherResults)
        }
    }
    @Throws(VaultQueryException::class)
    override fun <T : ContractState> _trackBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractStateType: Class<out T>): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        return concurrentBox.exclusive {
            val snapshotResults = _queryBy(criteria, paging, sorting, contractStateType)
            val updates: Observable<Vault.Update<T>> = uncheckedCast(_updatesPublisher.bufferUntilSubscribed()
                    .filter { it.containsType(contractStateType, snapshotResults.stateTypes) }
                    .map { filterContractStates(it, contractStateType) })
            DataFeed(snapshotResults, updates)
        }
    }

    private fun <T : ContractState> filterContractStates(update: Vault.Update<T>, contractStateType: Class<out T>) =
            update.copy(consumed = filterByContractState(contractStateType, update.consumed),
                    produced = filterByContractState(contractStateType, update.produced))

    private fun <T : ContractState> filterByContractState(contractStateType: Class<out T>, stateAndRefs: Set<StateAndRef<T>>) =
            stateAndRefs.filter { contractStateType.isAssignableFrom(it.state.data.javaClass) }.toSet()

    private fun getSession() = database.currentOrNew().session
    /**
     * Derive list from existing vault states and then incrementally update using vault observables
     */
    fun bootstrapContractStateTypes() {
        val criteria = criteriaBuilder.createQuery(String::class.java)
        val vaultStates = criteria.from(VaultSchemaV1.VaultStates::class.java)
        criteria.select(vaultStates.get("contractStateClassName")).distinct(true)
        val session = getSession()

        val query = session.createQuery(criteria)
        val results = query.resultList
        val distinctTypes = results.map { it }

        val unknownTypes = mutableSetOf<String>()
        distinctTypes.forEach { type ->
            val concreteType: Class<ContractState>? = try {
                uncheckedCast(Class.forName(type))
            } catch (e: ClassNotFoundException) {
                unknownTypes += type
                null
            }
            concreteType?.let {
                val contractTypes = deriveContractTypes(it)
                contractTypes.map {
                    val contractStateType = contractStateTypeMappings.getOrPut(it.name) { mutableSetOf() }
                    contractStateType.add(concreteType.name)
                }
            }
        }
        if (unknownTypes.isNotEmpty()) {
            log.warn("There are unknown contract state types in the vault, which will prevent these states from being used. The relevant CorDapps must be loaded for these states to be used. The types not on the classpath are ${unknownTypes.joinToString(", ", "[", "]")}.")
        }
    }

    private fun <T : ContractState> deriveContractTypes(clazz: Class<T>): Set<Class<T>> {
        val myTypes : MutableSet<Class<T>> = mutableSetOf()
        clazz.superclass?.let {
            if (!it.isInstance(Any::class)) {
                myTypes.add(uncheckedCast(it))
                myTypes.addAll(deriveContractTypes(uncheckedCast(it)))
            }
        }
        clazz.interfaces.forEach {
            if (it != ContractState::class.java) {
                myTypes.add(uncheckedCast(it))
                myTypes.addAll(deriveContractTypes(uncheckedCast(it)))
            }
        }
        return myTypes
    }
}

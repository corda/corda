package net.corda.node.services.vault

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.*
import net.corda.core.messaging.DataFeed
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.*
import net.corda.core.node.services.vault.*
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.*
import net.corda.core.utilities.*
import net.corda.node.services.api.VaultServiceInternal
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.nodeapi.internal.persistence.*
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
 * TODO: have transaction storage do some caching.
 */
class NodeVaultService(
        private val clock: Clock,
        private val keyManagementService: KeyManagementService,
        private val servicesForResolution: ServicesForResolution,
        hibernateConfig: HibernateConfiguration
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

    private val mutex = ThreadBox(InnerState())

    private fun recordUpdate(update: Vault.Update<ContractState>): Vault.Update<ContractState> {
        if (!update.isEmpty()) {
            val producedStateRefs = update.produced.map { it.ref }
            val producedStateRefsMap = update.produced.associateBy { it.ref }
            val consumedStateRefs = update.consumed.map { it.ref }
            log.trace { "Removing $consumedStateRefs consumed contract states and adding $producedStateRefs produced contract states to the database." }

            val session = currentDBSession()
            producedStateRefsMap.forEach { stateAndRef ->
                val state = VaultSchemaV1.VaultStates(
                        notary = stateAndRef.value.state.notary,
                        contractStateClassName = stateAndRef.value.state.data.javaClass.name,
                        stateStatus = Vault.StateStatus.UNCONSUMED,
                        recordedTime = clock.instant())
                state.stateRef = PersistentStateRef(stateAndRef.key)
                session.save(state)
            }
            consumedStateRefs.forEach { stateRef ->
                val state = session.get<VaultSchemaV1.VaultStates>(VaultSchemaV1.VaultStates::class.java, PersistentStateRef(stateRef))
                state?.run {
                    stateStatus = Vault.StateStatus.CONSUMED
                    consumedTime = clock.instant()
                    // remove lock (if held)
                    if (lockId != null) {
                        lockId = null
                        lockUpdateTime = clock.instant()
                        log.trace("Releasing soft lock on consumed state: $stateRef")
                    }
                    session.save(state)
                }
            }
        }
        return update
    }

    override val rawUpdates: Observable<Vault.Update<ContractState>>
        get() = mutex.locked { _rawUpdatesPublisher }

    override val updates: Observable<Vault.Update<ContractState>>
        get() = mutex.locked { _updatesInDbTx }

    /** Groups adjacent transactions into batches to generate separate net updates per transaction type. */
    override fun notifyAll(statesToRecord: StatesToRecord, txns: Iterable<CoreTransaction>) {
        if (statesToRecord == StatesToRecord.NONE || !txns.any()) return
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
            val ourNewStates = when (statesToRecord) {
                StatesToRecord.NONE -> throw AssertionError("Should not reach here")
                StatesToRecord.ONLY_RELEVANT -> tx.outputs.filter { isRelevant(it.data, keyManagementService.filterMyKeys(tx.outputs.flatMap { it.data.participants.map { it.owningKey } }).toSet()) }
                StatesToRecord.ALL_VISIBLE -> tx.outputs
            }.map { tx.outRef<ContractState>(it.data) }

            // Retrieve all unconsumed states for this transaction's inputs
            val consumedStates = loadStates(tx.inputs)

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
            val (consumedStateAndRefs, producedStates) = ltx.inputs.
                    zip(ltx.outputs).
                    filter { (_, output) ->
                        if (statesToRecord == StatesToRecord.ONLY_RELEVANT) {
                            isRelevant(output.data, myKeys.toSet())
                        } else {
                            true
                        }
                    }.
                    unzip()

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

    private fun loadStates(refs: Collection<StateRef>): Collection<StateAndRef<ContractState>> {
        val states = mutableListOf<StateAndRef<ContractState>>()
        if (refs.isNotEmpty()) {
            val refsList = refs.toList()
            val pageSize = PageSpecification().pageSize
            (0..(refsList.size - 1) / pageSize).forEach {
                val offset = it * pageSize
                val limit = minOf(offset + pageSize, refsList.size)
                val page = queryBy<ContractState>(QueryCriteria.VaultQueryCriteria(stateRefs = refsList.subList(offset, limit))).states
                states.addAll(page)
            }
        }
        return states
    }

    private fun processAndNotify(updates: List<Vault.Update<ContractState>>) {
        if (updates.isEmpty()) return
        val netUpdate = updates.reduce { update1, update2 -> update1 + update2 }
        if (!netUpdate.isEmpty()) {
            recordUpdate(netUpdate)
            mutex.locked {
                // flowId was required by SoftLockManager to perform auto-registration of soft locks for new states
                val uuid = (Strand.currentStrand() as? FlowStateMachineImpl<*>)?.id?.uuid
                val vaultUpdate = if (uuid != null) netUpdate.copy(flowId = uuid) else netUpdate
                if (uuid != null) {
                    val fungible = netUpdate.produced.filter { it.state.data is FungibleAsset<*> }
                    if (fungible.isNotEmpty()) {
                        val stateRefs = fungible.map { it.ref }.toNonEmptySet()
                        log.trace { "Reserving soft locks for flow id $uuid and states $stateRefs" }
                        softLockReserve(uuid, stateRefs)
                    }
                }
                updatesPublisher.onNext(vaultUpdate)
            }
        }
    }

    override fun addNoteToTransaction(txnId: SecureHash, noteText: String) {
        val txnNoteEntity = VaultSchemaV1.VaultTxnNote(txnId.toString(), noteText)
        currentDBSession().save(txnNoteEntity)
    }

    override fun getTransactionNotes(txnId: SecureHash): Iterable<String> {
        val session = currentDBSession()
        val criteriaBuilder = session.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(VaultSchemaV1.VaultTxnNote::class.java)
        val vaultStates = criteriaQuery.from(VaultSchemaV1.VaultTxnNote::class.java)
        val txIdPredicate = criteriaBuilder.equal(vaultStates.get<Vault.StateStatus>(VaultSchemaV1.VaultTxnNote::txId.name), txnId.toString())
        criteriaQuery.where(txIdPredicate)
        val results = session.createQuery(criteriaQuery).resultList
        return results.asIterable().map { it.note }
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

        // Enrich QueryCriteria with additional default attributes (such as soft locks)
        val sortAttribute = SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF)
        val sorter = Sort(setOf(Sort.SortColumn(sortAttribute, Sort.Direction.ASC)))
        val enrichedCriteria = QueryCriteria.VaultQueryCriteria(
                contractStateTypes = setOf(contractStateType),
                softLockingCondition = QueryCriteria.SoftLockingCondition(QueryCriteria.SoftLockingType.UNLOCKED_AND_SPECIFIED, listOf(lockId)))
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
            is OwnableState -> listOf(state.owner.owningKey)
            else -> state.participants.map { it.owningKey }
        }
        return keysToCheck.any { it in myKeys }
    }

    private val sessionFactory = hibernateConfig.sessionFactoryForRegisteredSchemas
    private val criteriaBuilder = sessionFactory.criteriaBuilder
    /**
     * Maintain a list of contract state interfaces to concrete types stored in the vault
     * for usage in generic queries of type queryBy<LinearState> or queryBy<FungibleState<*>>
     */
    private val contractStateTypeMappings = bootstrapContractStateTypes()

    init {
        rawUpdates.subscribe { update ->
            update.produced.forEach {
                val concreteType = it.state.data.javaClass
                log.trace { "State update of type: $concreteType" }
                val seen = contractStateTypeMappings.any { it.value.contains(concreteType.name) }
                if (!seen) {
                    val contractInterfaces = deriveContractInterfaces(concreteType)
                    contractInterfaces.map {
                        val contractInterface = contractStateTypeMappings.getOrPut(it.name, { mutableSetOf() })
                        contractInterface.add(concreteType.name)
                    }
                }
            }
        }
    }

    @Throws(VaultQueryException::class)
    override fun <T : ContractState> _queryBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractStateType: Class<out T>): Vault.Page<T> {
        return _queryBy(criteria, paging, sorting, contractStateType, false)
    }

    @Throws(VaultQueryException::class)
    private fun <T : ContractState> _queryBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractStateType: Class<out T>, skipPagingChecks: Boolean): Vault.Page<T> {
        log.info("Vault Query for contract type: $contractStateType, criteria: $criteria, pagination: $paging, sorting: $sorting")
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

        query.firstResult = (paging.pageNumber - 1) * paging.pageSize
        query.maxResults = paging.pageSize + 1  // detection too many results

        // execution
        val results = query.resultList

        // final pagination check (fail-fast on too many results when no pagination specified)
        if (!skipPagingChecks && paging.isDefault && results.size > DEFAULT_PAGE_SIZE)
            throw VaultQueryException("Please specify a `PageSpecification` as there are more results [${results.size}] than the default page size [$DEFAULT_PAGE_SIZE]")

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
                        val stateRef = StateRef(SecureHash.parse(vaultState.stateRef!!.txId!!), vaultState.stateRef!!.index!!)
                        stateRefs.add(stateRef)
                        statesMeta.add(Vault.StateMetadata(stateRef,
                                vaultState.contractStateClassName,
                                vaultState.recordedTime,
                                vaultState.consumedTime,
                                vaultState.stateStatus,
                                vaultState.notary,
                                vaultState.lockId,
                                vaultState.lockUpdateTime))
                    } else {
                        // TODO: improve typing of returned other results
                        log.debug { "OtherResults: ${Arrays.toString(result.toArray())}" }
                        otherResults.addAll(result.toArray().asList())
                    }
                }
        if (stateRefs.isNotEmpty())
            statesAndRefs.addAll(servicesForResolution.loadStates(stateRefs) as Collection<StateAndRef<T>>)

        return Vault.Page(states = statesAndRefs, statesMetadata = statesMeta, stateTypes = criteriaParser.stateTypes, totalStatesAvailable = totalStates, otherResults = otherResults)
    }

    @Throws(VaultQueryException::class)
    override fun <T : ContractState> _trackBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractStateType: Class<out T>): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        return mutex.locked {
            val snapshotResults = _queryBy(criteria, paging, sorting, contractStateType)
            val updates: Observable<Vault.Update<T>> = uncheckedCast(_updatesPublisher.bufferUntilSubscribed().filter { it.containsType(contractStateType, snapshotResults.stateTypes) })
            DataFeed(snapshotResults, updates)
        }
    }

    private fun getSession() = contextDatabase.currentOrNew().session
    /**
     * Derive list from existing vault states and then incrementally update using vault observables
     */
    private fun bootstrapContractStateTypes(): MutableMap<String, MutableSet<String>> {
        val criteria = criteriaBuilder.createQuery(String::class.java)
        val vaultStates = criteria.from(VaultSchemaV1.VaultStates::class.java)
        criteria.select(vaultStates.get("contractStateClassName")).distinct(true)
        val session = getSession()

        val query = session.createQuery(criteria)
        val results = query.resultList
        val distinctTypes = results.map { it }

        val contractInterfaceToConcreteTypes = mutableMapOf<String, MutableSet<String>>()
        distinctTypes.forEach { type ->
            val concreteType: Class<ContractState> = uncheckedCast(Class.forName(type))
            val contractInterfaces = deriveContractInterfaces(concreteType)
            contractInterfaces.map {
                val contractInterface = contractInterfaceToConcreteTypes.getOrPut(it.name, { mutableSetOf() })
                contractInterface.add(concreteType.name)
            }
        }
        return contractInterfaceToConcreteTypes
    }

    private fun <T : ContractState> deriveContractInterfaces(clazz: Class<T>): Set<Class<T>> {
        val myInterfaces: MutableSet<Class<T>> = mutableSetOf()
        clazz.interfaces.forEach {
            if (it != ContractState::class.java) {
                myInterfaces.add(uncheckedCast(it))
                myInterfaces.addAll(deriveContractInterfaces(uncheckedCast(it)))
            }
        }
        return myInterfaces
    }
}
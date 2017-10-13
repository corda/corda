package net.corda.node.services.vault

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.*
import net.corda.core.node.StateLoader
import net.corda.core.node.services.*
import net.corda.core.node.services.StatesNotAvailableException
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.node.services.vault.SortAttribute
import net.corda.core.messaging.DataFeed
import net.corda.core.node.services.VaultQueryException
import net.corda.core.node.services.vault.*
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.SerializationDefaults.STORAGE_CONTEXT
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.CoreTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.*
import net.corda.node.services.api.VaultServiceInternal
import net.corda.node.services.persistence.HibernateConfiguration
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.utilities.DatabaseTransactionManager
import net.corda.node.utilities.bufferUntilDatabaseCommit
import net.corda.node.utilities.currentDBSession
import net.corda.node.utilities.wrapWithDatabaseTransaction
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
 * Currently, the node vault service is a very simple RDBMS backed implementation.  It will change significantly when
 * we add further functionality as the design for the vault and vault service matures.
 *
 * This class needs database transactions to be in-flight during method calls and init, and will throw exceptions if
 * this is not the case.
 *
 * TODO: keep an audit trail with time stamps of previously unconsumed states "as of" a particular point in time.
 * TODO: have transaction storage do some caching.
 */
class NodeVaultService(private val clock: Clock, private val keyManagementService: KeyManagementService, private val stateLoader: StateLoader, hibernateConfig: HibernateConfiguration) : SingletonSerializeAsToken(), VaultServiceInternal {

    private companion object {
        val log = loggerFor<NodeVaultService>()
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
                        contractState = stateAndRef.value.state.serialize(context = STORAGE_CONTEXT).bytes,
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
        fun makeUpdate(tx: WireTransaction): Vault.Update<ContractState> {
            val myKeys = keyManagementService.filterMyKeys(tx.outputs.flatMap { it.data.participants.map { it.owningKey } })
            val ourNewStates = tx.outputs.
                    filter { isRelevant(it.data, myKeys.toSet()) }.
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
        fun makeUpdate(tx: NotaryChangeWireTransaction): Vault.Update<ContractState> {
            // We need to resolve the full transaction here because outputs are calculated from inputs
            // We also can't do filtering beforehand, since output encumbrance pointers get recalculated based on
            // input positions
            val ltx = tx.resolve(stateLoader, emptyList())
            val myKeys = keyManagementService.filterMyKeys(ltx.outputs.flatMap { it.data.participants.map { it.owningKey } })
            val (consumedStateAndRefs, producedStates) = ltx.inputs.
                    zip(ltx.outputs).
                    filter { (_, output) -> isRelevant(output.data, myKeys.toSet()) }.
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

    // TODO: replace this method in favour of a VaultQuery query
    private fun loadStates(refs: Collection<StateRef>): HashSet<StateAndRef<ContractState>> {
        val states = HashSet<StateAndRef<ContractState>>()
        if (refs.isNotEmpty()) {
            val session = currentDBSession()
            val criteriaBuilder = session.criteriaBuilder
            val criteriaQuery = criteriaBuilder.createQuery(VaultSchemaV1.VaultStates::class.java)
            val vaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
            val statusPredicate = criteriaBuilder.equal(vaultStates.get<Vault.StateStatus>(VaultSchemaV1.VaultStates::stateStatus.name), Vault.StateStatus.UNCONSUMED)
            val persistentStateRefs = refs.map { PersistentStateRef(it.txhash.bytes.toHexString(), it.index) }
            val compositeKey = vaultStates.get<PersistentStateRef>(VaultSchemaV1.VaultStates::stateRef.name)
            val stateRefsPredicate = criteriaBuilder.and(compositeKey.`in`(persistentStateRefs))
            criteriaQuery.where(statusPredicate, stateRefsPredicate)
            val results = session.createQuery(criteriaQuery).resultList
            results.asSequence().forEach {
                val txHash = SecureHash.parse(it.stateRef?.txId!!)
                val index = it.stateRef?.index!!
                val state = it.contractState.deserialize<TransactionState<ContractState>>(context = STORAGE_CONTEXT)
                states.add(StateAndRef(state, StateRef(txHash, index)))
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
                log.trace("Reserving soft lock states for $lockId: $stateRefs")
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
                    log.trace("Reverting $revertUpdatedRows partially soft locked states for $lockId")
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
                log.trace("Releasing $update soft locked states for $lockId")
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
                    log.trace("Releasing $updatedRows soft locked states for $lockId and stateRefs $stateRefs")
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
        log.info("Vault Query for contract type: $contractStateType, criteria: $criteria, pagination: $paging, sorting: $sorting")
        // calculate total results where a page specification has been defined
        var totalStates = -1L
        if (!paging.isDefault) {
            val count = builder { VaultSchemaV1.VaultStates::recordedTime.count() }
            val countCriteria = QueryCriteria.VaultCustomQueryCriteria(count, Vault.StateStatus.ALL)
            val results = queryBy(contractStateType, criteria.and(countCriteria))
            totalStates = results.otherResults[0] as Long
        }

        val session = getSession()

        session.use {
            val criteriaQuery = criteriaBuilder.createQuery(Tuple::class.java)
            val queryRootVaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)

            // TODO: revisit (use single instance of parser for all queries)
            val criteriaParser = HibernateQueryCriteriaParser(contractStateType, contractStateTypeMappings, criteriaBuilder, criteriaQuery, queryRootVaultStates)

            try {
                // parse criteria and build where predicates
                criteriaParser.parse(criteria, sorting)

                // prepare query for execution
                val query = session.createQuery(criteriaQuery)

                // pagination checks
                if (!paging.isDefault) {
                    // pagination
                    if (paging.pageNumber < DEFAULT_PAGE_NUM) throw VaultQueryException("Page specification: invalid page number ${paging.pageNumber} [page numbers start from $DEFAULT_PAGE_NUM]")
                    if (paging.pageSize < 1) throw VaultQueryException("Page specification: invalid page size ${paging.pageSize} [must be a value between 1 and $MAX_PAGE_SIZE]")
                }

                query.firstResult = (paging.pageNumber - 1) * paging.pageSize
                query.maxResults = paging.pageSize + 1  // detection too many results

                // execution
                val results = query.resultList

                // final pagination check (fail-fast on too many results when no pagination specified)
                if (paging.isDefault && results.size > DEFAULT_PAGE_SIZE)
                    throw VaultQueryException("Please specify a `PageSpecification` as there are more results [${results.size}] than the default page size [$DEFAULT_PAGE_SIZE]")

                val statesAndRefs: MutableList<StateAndRef<T>> = mutableListOf()
                val statesMeta: MutableList<Vault.StateMetadata> = mutableListOf()
                val otherResults: MutableList<Any> = mutableListOf()

                results.asSequence()
                        .forEachIndexed { index, result ->
                            if (result[0] is VaultSchemaV1.VaultStates) {
                                if (!paging.isDefault && index == paging.pageSize) // skip last result if paged
                                    return@forEachIndexed
                                val vaultState = result[0] as VaultSchemaV1.VaultStates
                                val stateRef = StateRef(SecureHash.parse(vaultState.stateRef!!.txId!!), vaultState.stateRef!!.index!!)
                                val state = vaultState.contractState.deserialize<TransactionState<T>>(context = STORAGE_CONTEXT)
                                statesMeta.add(Vault.StateMetadata(stateRef,
                                        vaultState.contractStateClassName,
                                        vaultState.recordedTime,
                                        vaultState.consumedTime,
                                        vaultState.stateStatus,
                                        vaultState.notary,
                                        vaultState.lockId,
                                        vaultState.lockUpdateTime))
                                statesAndRefs.add(StateAndRef(state, stateRef))
                            } else {
                                // TODO: improve typing of returned other results
                                log.debug { "OtherResults: ${Arrays.toString(result.toArray())}" }
                                otherResults.addAll(result.toArray().asList())
                            }
                        }

                return Vault.Page(states = statesAndRefs, statesMetadata = statesMeta, stateTypes = criteriaParser.stateTypes, totalStatesAvailable = totalStates, otherResults = otherResults)
            } catch (e: java.lang.Exception) {
                log.error(e.message)
                throw e.cause ?: e
            }
        }
    }

    @Throws(VaultQueryException::class)
    override fun <T : ContractState> _trackBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractStateType: Class<out T>): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        return mutex.locked {
            val snapshotResults = _queryBy(criteria, paging, sorting, contractStateType)
            val updates: Observable<Vault.Update<T>> = uncheckedCast(_updatesPublisher.bufferUntilSubscribed().filter { it.containsType(contractStateType, snapshotResults.stateTypes) })
            DataFeed(snapshotResults, updates)
        }
    }

    private fun getSession(): Session {
        return sessionFactory.withOptions().
                connection(DatabaseTransactionManager.current().connection).
                openSession()
    }

    /**
     * Derive list from existing vault states and then incrementally update using vault observables
     */
    private fun bootstrapContractStateTypes(): MutableMap<String, MutableSet<String>> {
        val criteria = criteriaBuilder.createQuery(String::class.java)
        val vaultStates = criteria.from(VaultSchemaV1.VaultStates::class.java)
        criteria.select(vaultStates.get("contractStateClassName")).distinct(true)
        val session = getSession()
        session.use {
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

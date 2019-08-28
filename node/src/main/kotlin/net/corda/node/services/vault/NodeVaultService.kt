package net.corda.node.services.vault

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.containsAny
import net.corda.core.internal.*
import net.corda.core.messaging.DataFeed
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.KeyManagementService
import net.corda.core.node.services.StatesNotAvailableException
import net.corda.core.node.services.Vault
import net.corda.core.node.services.Vault.ConstraintInfo.Companion.constraintInfo
import net.corda.core.node.services.VaultQueryException
import net.corda.core.node.services.vault.*
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.*
import net.corda.core.utilities.*
import net.corda.node.services.api.SchemaService
import net.corda.node.services.api.VaultServiceInternal
import net.corda.node.services.schema.PersistentStateService
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.utilities.InfrequentlyMutatedCache
import net.corda.nodeapi.internal.persistence.*
import org.hibernate.Session
import rx.Observable
import rx.subjects.PublishSubject
import java.security.PublicKey
import java.time.Clock
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
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
        cacheFactory: NamedCacheFactory,
        private val appClassloader: ClassLoader
) : SingletonSerializeAsToken(), VaultServiceInternal {
    companion object {
        private val log = contextLogger()
        private val detailedLogger = detailedLogger()

        /**
         * Establish whether a given state is relevant to a node, given the node's public keys.
         *
         * A state is relevant if any of the participants (or the owner for ownable states) has an owning key matching one of this node's
         * public keys.
         */
        fun isRelevant(state: ContractState, myKeys: Set<PublicKey>): Boolean {
            val keysToCheck = when (state) {
                is OwnableState -> listOf(state.owner.owningKey)
                else -> state.participants.map { it.owningKey }
            }
            return keysToCheck.any { it.containsAny(myKeys) }
        }
    }

    private class InnerState {
        val _updatesPublisher = PublishSubject.create<Vault.Update<ContractState>>()!!
        val _rawUpdatesPublisher = PublishSubject.create<Vault.Update<ContractState>>()!!
        val _updatesInDbTx = _updatesPublisher.wrapWithDatabaseTransaction().asObservable()!!

        // For use during publishing only.
        val updatesPublisher: rx.Observer<Vault.Update<ContractState>> get() = _updatesPublisher.bufferUntilDatabaseCommit().tee(_rawUpdatesPublisher)
    }

    private val concurrentBox = ConcurrentBox(InnerState())
    private val criteriaBuilder: CriteriaBuilder by lazy { database.hibernateConfig.sessionFactoryForRegisteredSchemas.criteriaBuilder }
    private val persistentStateService = PersistentStateService(schemaService)

    /**
     * Maintain a list of contract state interfaces to concrete types stored in the vault
     * for usage in generic queries of type queryBy<LinearState> or queryBy<FungibleState<*>>
     */
    @VisibleForTesting
    internal val contractStateTypeMappings = ConcurrentHashMap<String, MutableSet<String>>()

    /**
     * This caches what states are in the vault for a particular transaction.
     */
    @VisibleForTesting
    internal val producedStatesMapping = InfrequentlyMutatedCache<SecureHash, BitSet>("NodeVaultService_producedStates", cacheFactory)

    override fun start() {
        bootstrapContractStateTypes()
        rawUpdates.subscribe { update ->
            (update.produced + update.references).forEach {
                val concreteType = it.state.data.javaClass
                log.trace { "State update of type: $concreteType" }
                val seen = contractStateTypeMappings.any { it.value.contains(concreteType.name) }
                if (!seen) {
                    val contractTypes = deriveContractTypes(concreteType)
                    contractTypes.map {
                        val contractStateType = contractStateTypeMappings.getOrPut(it.name) { CopyOnWriteArraySet() }
                        contractStateType.add(concreteType.name)
                    }
                }
            }
        }
    }

    private fun saveStates(session: Session, states: Map<StateRef, StateAndRef<ContractState>>, now: Instant, produced: Boolean) {
        states.forEach { stateAndRef ->
            val stateOnly = stateAndRef.value.state.data
            val uuid = if (produced && stateOnly is FungibleState<*>) {
                FlowStateMachineImpl.currentStateMachine()?.id?.uuid?.toString()
            } else null
            if (uuid != null) {
                FlowStateMachineImpl.currentStateMachine()?.hasSoftLockedStates = true
                log.trace { "Reserving soft lock for flow id $uuid and state ${stateAndRef.key}" }
            }         // TODO: Optimise this.
            //
            // For EVERY state to be committed to the vault, this checks whether it is spendable by the recording
            // node. The behaviour is as follows:
            //
            // 1) All vault updates marked as RELEVANT will, of course, all have relevancy_status = 1 in the
            //    "vault_states" table.
            // 2) For ALL_VISIBLE updates, those which are not relevant according to the relevancy rules will have
            //    relevancy_status = 0 in the "vault_states" table.
            //
            // This is useful when it comes to querying for fungible states, when we do not want irrelevant states
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
            val persistentStateRef = PersistentStateRef(stateAndRef.key)
            // This check is done to set the "relevancyStatus". When one performs a vault query, it is possible to return ALL states, ONLY
            // RELEVANT states or NOT relevant states.
            val isRelevant = isRelevant(stateOnly, keyManagementService.filterMyKeys(keys).toSet())
            val constraintInfo = Vault.ConstraintInfo(stateAndRef.value.state.constraint)
            // Save a row for each party in the state_party table.
            // TODO: Perhaps these can be stored in a batch?
            stateOnly.participants.groupBy { it.owningKey }.forEach { participants ->
                val persistentParty = VaultSchemaV1.PersistentParty(persistentStateRef, participants.value.first())
                detailedLogger.trace { "Party(action=save_start;party=${persistentParty.x500Name})" }
                session.save(persistentParty)
                detailedLogger.trace { "Party(action=save_end;party=${persistentParty.x500Name})" }
            }
            val stateToAdd = VaultSchemaV1.VaultStates(
                    notary = stateAndRef.value.state.notary,
                    contractStateClassName = stateAndRef.value.state.data.javaClass.name,
                    stateStatus = Vault.StateStatus.UNCONSUMED,
                    lockId = uuid,
                    lockUpdateTime = if (uuid == null) null else now,
                    recordedTime = clock.instant(),
                    relevancyStatus = if (isRelevant) Vault.RelevancyStatus.RELEVANT else Vault.RelevancyStatus.NOT_RELEVANT,
                    constraintType = constraintInfo.type(),
                    constraintData = constraintInfo.data()
            )
            stateToAdd.stateRef = persistentStateRef
            detailedLogger.trace { "State(action=save_start;className=${stateToAdd.contractStateClassName};status=${stateToAdd.stateStatus})" }
            session.save(stateToAdd)
            detailedLogger.trace { "State(action=save_end;className=${stateToAdd.contractStateClassName};status=${stateToAdd.stateStatus})" }
        }
    }

    private fun recordUpdate(update: Vault.Update<ContractState>): Vault.Update<ContractState> {
        if (!update.isEmpty()) {
            val producedStateRefs = update.produced.map { it.ref }
            val producedStateRefsMap = update.produced.associateBy { it.ref }
            val consumedStateRefs = update.consumed.map { it.ref }
            val referenceStateRefsMap = update.references.associateBy { it.ref }
            log.trace { "Removing $consumedStateRefs consumed contract states and adding $producedStateRefs produced contract states to the database." }

            val session = currentDBSession()
            val now = clock.instant()

            // Persist the outputs.
            saveStates(session, producedStateRefsMap, now, true)

            // Persist the reference states.
            saveStates(session, referenceStateRefsMap, now, false)
            // Invalidate the cached vault states for any newly produced reference states.
            referenceStateRefsMap.keys.map { it.txhash }.distinct().map { producedStatesMapping.invalidate(it) }

            // Persist the consumed inputs.
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

    @VisibleForTesting
    internal val publishUpdates get() = concurrentBox.content.updatesPublisher

    /** Groups adjacent transactions into batches to generate separate net updates per transaction type. */
    override fun notifyAll(statesToRecord: StatesToRecord, txns: Iterable<CoreTransaction>) {
        if (statesToRecord == StatesToRecord.NONE || !txns.any()) {
            txns.forEach { producedStatesMapping.get(it.id) { BitSet(0) } }
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

        fun <T> withValidDeserialization(list: List<T>, txId: SecureHash): Map<Int, T> = (0 until list.size).mapNotNull { idx ->
            try {
                idx to list[idx]
            } catch (e: TransactionDeserialisationException) {
                // When resolving transaction dependencies we might encounter contracts we haven't installed locally.
                // This will cause a failure as we can't deserialize such states in the context of the `appClassloader`.
                // For now we ignore these states.
                // In the future we will use the AttachmentsClassloader to correctly deserialize and asses the relevancy.
                log.debug { "Could not deserialize state $idx from transaction $txId. Cause: $e" }
                null
            }
        }.toMap()

        // Returns only output states that can be deserialised successfully.
        fun WireTransaction.deserializableOutputStates(): Map<Int, TransactionState<ContractState>> = withValidDeserialization(this.outputs, this.id)

        // Returns only reference states that can be deserialised successfully.
        fun LedgerTransaction.deserializableRefStates(): Map<Int, StateAndRef<ContractState>> = withValidDeserialization(this.references, this.id)

        fun makeUpdate(tx: WireTransaction): Vault.Update<ContractState>? {
            val outputs: Map<Int, TransactionState<ContractState>> = tx.deserializableOutputStates()
            val outputsBitSet = BitSet(outputs.size)
            val ourNewStates = when (statesToRecord) {
                StatesToRecord.NONE -> throw AssertionError("Should not reach here")
                StatesToRecord.ONLY_RELEVANT -> outputs.filter {(_, state)->
                    isRelevant(state.data, keyManagementService.filterMyKeys(outputs.flatMap { it.value.data.participants.map { it.owningKey } }).toSet())
                }
                StatesToRecord.ALL_VISIBLE -> outputs
            }.map {
                outputsBitSet[it.key] = true
                tx.outRef<ContractState>(it.key)
            }
            val cachedBitSet = producedStatesMapping.get(tx.id) { outputsBitSet }
            if (cachedBitSet != outputsBitSet) {
                // If any outputBitSet bits are not set in the cached value, invalidate.
                val intersection = outputsBitSet.clone() as BitSet
                intersection.and(cachedBitSet)
                if (intersection != outputsBitSet) {
                    // For some reason, we cached the vault entries for this transaction previously.
                    producedStatesMapping.invalidate(tx.id)
                }
            }

            // Retrieve all unconsumed states for this transaction's inputs
            val consumedStates = loadStatesWithVaultFilter(tx.inputs)

            // Is transaction irrelevant? If so, then we don't care about the reference states either.
            if (consumedStates.isEmpty() && ourNewStates.isEmpty()) {
                log.trace { "tx ${tx.id} was irrelevant to this vault, ignoring" }
                return null
            }

            // This list should only contain NEW states which we have not seen before as an output in another transaction. If we can't
            // obtain the references from the vault then the reference must be a state we have not seen before, therefore we should store it
            // in the vault. If StateToRecord is set to ALL_VISIBLE or ONLY_RELEVANT then we should store all of the previously unseen
            // states in the reference list. The assumption is that we might need to inspect them at some point if they were referred to
            // in the contracts of the input or output states. If states to record is none then we shouldn't record any reference states.
            val newReferenceStateAndRefs = if (tx.references.isEmpty()) {
                emptyList()
            } else {
                when (statesToRecord) {
                    StatesToRecord.NONE -> throw AssertionError("Should not reach here")
                    StatesToRecord.ALL_VISIBLE, StatesToRecord.ONLY_RELEVANT -> {
                        val notSeenReferences = tx.references - loadStatesWithVaultFilter(tx.references).map { it.ref }
                        // TODO: This is expensive - is there another way?
                        tx.toLedgerTransaction(servicesForResolution).deserializableRefStates()
                                .filter { (_, stateAndRef) -> stateAndRef.ref in notSeenReferences }
                                .values
                    }
                }
            }

            return Vault.Update(consumedStates.toSet(), ourNewStates.toSet(), references = newReferenceStateAndRefs.toSet())
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

            val referenceStateAndRefs = ltx.references

            val updateType = if (tx is ContractUpgradeWireTransaction) {
                Vault.UpdateType.CONTRACT_UPGRADE
            } else {
                Vault.UpdateType.NOTARY_CHANGE
            }
            return Vault.Update(consumedStateAndRefs.toSet(), producedStateAndRefs.toSet(), null, updateType, referenceStateAndRefs.toSet())
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
                detailedLogger.trace { "States(action=loading;refs=$refs)" }
                query.resultList.map { it[0] as PersistentStateRef }.groupBy { it.txId }.forEach {
                    // Record what states were found, in the cache and the results.
                    val secureHash = SecureHash.parse(it.key)
                    val outputsBitSet = BitSet(0) // This is auto-expanded when setting higher bits.
                    states.addAll(it.value.map {
                        outputsBitSet[it.index] = true
                        StateRef(secureHash, it.index)
                    }.filter { it in refs })
                    // Cache the result for future lookups.
                    producedStatesMapping.get(secureHash) { outputsBitSet }
                }
                detailedLogger.trace { "States(action=loaded;refs=$refs)" }
            }
        }
        return servicesForResolution.loadStates(states)
    }

    private fun processAndNotify(updates: List<Vault.Update<ContractState>>) {
        if (updates.isEmpty()) return
        val netUpdate = updates.reduce { update1, update2 -> update1 + update2 }
        if (!netUpdate.isEmpty()) {
            recordUpdate(netUpdate)
            persistentStateService.persist(netUpdate.produced + netUpdate.references)
            // flowId was required by SoftLockManager to perform auto-registration of soft locks for new states
            val uuid = (Strand.currentStrand() as? FlowStateMachineImpl<*>)?.id?.uuid
            val vaultUpdate = if (uuid != null) netUpdate.copy(flowId = uuid) else netUpdate
            concurrentBox.concurrent {
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
    override fun <T : FungibleState<*>> tryLockFungibleStatesForSpending(
            lockId: UUID,
            eligibleStatesQuery: QueryCriteria,
            amount: Amount<*>,
            contractStateType: Class<out T>
    ): List<StateAndRef<T>> {
        if (amount.quantity == 0L) {
            return emptyList()
        }

        // Helper to unwrap the token from the Issued object if one exists.
        fun unwrapIssuedAmount(amount: Amount<*>): Any {
            val token = amount.token
            return when (token) {
                is Issued<*> -> token.product
                else -> token
            }
        }

        val unwrappedToken = unwrapIssuedAmount(amount)

        // Enrich QueryCriteria with additional default attributes (such as soft locks).
        // We only want to return RELEVANT states here.
        val sortAttribute = SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF)
        val sorter = Sort(setOf(Sort.SortColumn(sortAttribute, Sort.Direction.ASC)))
        val enrichedCriteria = QueryCriteria.VaultQueryCriteria(
                contractStateTypes = setOf(contractStateType),
                softLockingCondition = QueryCriteria.SoftLockingCondition(QueryCriteria.SoftLockingType.UNLOCKED_AND_SPECIFIED, listOf(lockId)),
                relevancyStatus = Vault.RelevancyStatus.RELEVANT
        )
        val results = queryBy(contractStateType, enrichedCriteria.and(eligibleStatesQuery), sorter)

        var claimedAmount = 0L
        val claimedStates = mutableListOf<StateAndRef<T>>()
        for (state in results.states) {
            // This method handles Amount<Issued<T>> in FungibleAsset and Amount<T> in FungibleState.
            val issuedAssetToken = unwrapIssuedAmount(state.state.data.amount)

            if (issuedAssetToken == unwrappedToken) {
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

    @Throws(VaultQueryException::class)
    override fun <T : ContractState> _queryBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractStateType: Class<out T>): Vault.Page<T> {
        try {
            return _queryBy(criteria, paging, sorting, contractStateType, false)
        } catch (e: VaultQueryException) {
            throw e
        } catch (e: Exception) {
            throw VaultQueryException("An error occurred while attempting to query the vault: ${e.message}", e)
        }
    }

    @Throws(VaultQueryException::class)
    private fun <T : ContractState> _queryBy(criteria: QueryCriteria, paging_: PageSpecification, sorting: Sort, contractStateType: Class<out T>, skipPagingChecks: Boolean): Vault.Page<T> {
        // We decrement by one if the client requests MAX_PAGE_SIZE, assuming they can not notice this because they don't have enough memory
        // to request `MAX_PAGE_SIZE` states at once.
        val paging = if (paging_.pageSize == Integer.MAX_VALUE) {
            paging_.copy(pageSize = Integer.MAX_VALUE - 1)
        } else {
            paging_
        }
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

            detailedLogger.trace { "Contract(action=query_start;type=$contractStateType;criteria=$criteria;pagination=$paging;sorting=$paging)" }

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
                if (paging.pageSize < 1) throw VaultQueryException("Page specification: invalid page size ${paging.pageSize} [minimum is 1]")
                if (paging.pageSize > MAX_PAGE_SIZE) throw VaultQueryException("Page specification: invalid page size ${paging.pageSize} [maximum is $MAX_PAGE_SIZE]")
            }

            // For both SQLServer and PostgresSQL, firstResult must be >= 0. So we set a floor at 0.
            // TODO: This is a catch-all solution. But why is the default pageNumber set to be -1 in the first place?
            // Even if we set the default pageNumber to be 1 instead, that may not cover the non-default cases.
            // So the floor may be necessary anyway.
            query.firstResult = maxOf(0, (paging.pageNumber - 1) * paging.pageSize)
            val pageSize = paging.pageSize + 1
            query.maxResults = if (pageSize > 0) pageSize else Integer.MAX_VALUE // detection too many results, protected against overflow

            // execution
            val results = query.resultList

            detailedLogger.trace { "Contract(action=query_end;type=$contractStateType;criteria=$criteria;pagination=$paging;sorting=$paging)" }

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
                                    vaultState.relevancyStatus,
                                    constraintInfo(vaultState.constraintType, vaultState.constraintData)
                            ))
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

    /**
     * Returns a [DataFeed] containing the results of the provided query, along with the associated observable, containing any subsequent updates.
     *
     * Note that this method can be invoked concurrently with [NodeVaultService.notifyAll], which means there could be race conditions between reads
     * performed here and writes performed there. These are prevented, using the following approach:
     * - Observable updates emitted by [NodeVaultService.notifyAll] are buffered until the transaction's commit point
     *   This means that it's as if publication is performed, after the transaction is committed.
     * - Observable updates tracked by [NodeVaultService._trackBy] are buffered before the transaction (for the provided query) is open
     *   and until the client's subscription. So, it's as if the customer is subscribed to the observable before the read's transaction is open.
     *
     * The combination of the 2 conditions described above guarantee that there can be no possible interleaving, where some states are not observed in the query
     * (i.e. because read transaction opens, before write transaction is closed) and at the same time not included in the observable (i.e. because subscription
     * is done before the publication of updates). However, this guarantee cannot be provided, in cases where the client invokes [VaultService.trackBy] with an open
     * transaction.
     */
    @Throws(VaultQueryException::class)
    override fun <T : ContractState> _trackBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractStateType: Class<out T>): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        return concurrentBox.exclusive {
            val updates: Observable<Vault.Update<T>> = uncheckedCast(_updatesPublisher.bufferUntilSubscribed())
            if (contextTransactionOrNull != null) {
                log.warn("trackBy is called with an already existing, open DB transaction. As a result, there might be states missing from both the snapshot and observable, included in the returned data feed, because of race conditions.")
            }
            val snapshotResults = _queryBy(criteria, paging, sorting, contractStateType)
            val snapshotStatesRefs = snapshotResults.statesMetadata.map { it.ref }.toSet()
            val snapshotConsumedStatesRefs = snapshotResults.statesMetadata.filter { it.consumedTime != null }
                    .map { it.ref }.toSet()
            val filteredUpdates = updates.filter { it.containsType(contractStateType, snapshotResults.stateTypes) }
                    .map { filterContractStates(it, contractStateType) }
                    .filter { !hasBeenSeen(it, snapshotStatesRefs, snapshotConsumedStatesRefs) }

            DataFeed(snapshotResults, filteredUpdates)
        }
    }

    private fun <T : ContractState> filterContractStates(update: Vault.Update<T>, contractStateType: Class<out T>) =
            update.copy(consumed = filterByContractState(contractStateType, update.consumed),
                    produced = filterByContractState(contractStateType, update.produced))

    private fun <T : ContractState> filterByContractState(contractStateType: Class<out T>, stateAndRefs: Set<StateAndRef<T>>) =
            stateAndRefs.filter { contractStateType.isAssignableFrom(it.state.data.javaClass) }.toSet()

    /**
     * Filters out updates that have been seen, aka being reflected in the query's result snapshot.
     *
     * An update is reflected in the snapshot, if both of the following conditions hold:
     * - all the states produced by the update are included in the snapshot (regardless of whether they are consumed).
     * - all the states consumed by the update are included in the snapshot, AND they are consumed.
     *
     * Note: An update can contain multiple transactions (with netting performed on them). As a result, some of these transactions
     *       can be included in the snapshot result, while some are not. In this case, since we are not capable of reverting the netting and doing
     *       partial exclusion, we decide to return some more updates, instead of losing them completely (not returning them either in
     *       the snapshot or in the observable).
     */
    private fun <T: ContractState> hasBeenSeen(update: Vault.Update<T>, snapshotStatesRefs: Set<StateRef>, snapshotConsumedStatesRefs: Set<StateRef>): Boolean {
        val updateProducedStatesRefs = update.produced.map { it.ref }.toSet()
        val updateConsumedStatesRefs = update.consumed.map { it.ref }.toSet()

        return snapshotStatesRefs.containsAll(updateProducedStatesRefs) && snapshotConsumedStatesRefs.containsAll(updateConsumedStatesRefs)
    }

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
                uncheckedCast(Class.forName(type, true, appClassloader))
            } catch (e: ClassNotFoundException) {
                unknownTypes += type
                null
            }
            concreteType?.let {
                val contractTypes = deriveContractTypes(it)
                contractTypes.map {
                    val contractStateType = contractStateTypeMappings.getOrPut(it.name) { CopyOnWriteArraySet() }
                    contractStateType.add(concreteType.name)
                }
            }
        }
        if (unknownTypes.isNotEmpty()) {
            log.warn("There are unknown contract state types in the vault, which will prevent these states from being used. The relevant CorDapps must be loaded for these states to be used. The types not on the classpath are ${unknownTypes.joinToString(", ", "[", "]")}.")
        }
    }

    private fun <T : ContractState> deriveContractTypes(clazz: Class<T>): Set<Class<T>> {
        val myTypes : MutableSet<Class<T>> = CopyOnWriteArraySet()
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

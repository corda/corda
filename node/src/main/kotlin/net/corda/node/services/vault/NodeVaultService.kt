package net.corda.node.services.vault

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.ThreadBox
import net.corda.core.internal.VisibleForTesting
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
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.SerializationDefaults.STORAGE_CONTEXT
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.CoreTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.*
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.utilities.DatabaseTransactionManager
import net.corda.node.utilities.bufferUntilDatabaseCommit
import net.corda.node.utilities.wrapWithDatabaseTransaction
import rx.Observable
import rx.subjects.PublishSubject
import java.security.PublicKey
import java.time.Instant
import java.util.*
import javax.persistence.criteria.Predicate

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
class NodeVaultService(private val services: ServiceHub) : SingletonSerializeAsToken(), VaultService {

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

            val session = DatabaseTransactionManager.current().session
            producedStateRefsMap.forEach { stateAndRef ->
                val state = VaultSchemaV1.VaultStates(
                        notary = stateAndRef.value.state.notary,
                        contractStateClassName = stateAndRef.value.state.data.javaClass.name,
                        contractState = stateAndRef.value.state.serialize(context = STORAGE_CONTEXT).bytes,
                        stateStatus = Vault.StateStatus.UNCONSUMED,
                        recordedTime = services.clock.instant())
                state.stateRef = PersistentStateRef(stateAndRef.key)
                session.save(state)
            }
            consumedStateRefs.forEach { stateRef ->
                val state = session.get<VaultSchemaV1.VaultStates>(VaultSchemaV1.VaultStates::class.java, PersistentStateRef(stateRef))
                state?.run {
                    stateStatus = Vault.StateStatus.CONSUMED
                    consumedTime = services.clock.instant()
                    // remove lock (if held)
                    if (lockId != null) {
                        lockId = null
                        lockUpdateTime = services.clock.instant()
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

    override val updatesPublisher: PublishSubject<Vault.Update<ContractState>>
        get() = mutex.locked { _updatesPublisher }

    /**
     * Splits the provided [txns] into batches of [WireTransaction] and [NotaryChangeWireTransaction].
     * This is required because the batches get aggregated into single updates, and we want to be able to
     * indicate whether an update consists entirely of regular or notary change transactions, which may require
     * different processing logic.
     */
    fun notifyAll(txns: Iterable<CoreTransaction>) {
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

    /** Same as notifyAll but with a single transaction. */
    fun notify(tx: CoreTransaction) = notifyAll(listOf(tx))
    
    private fun notifyRegular(txns: Iterable<WireTransaction>) {
        fun makeUpdate(tx: WireTransaction): Vault.Update<ContractState> {
            val myKeys = services.keyManagementService.filterMyKeys(tx.outputs.flatMap { it.data.participants.map { it.owningKey } })
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
        val ourKeys = services.keyManagementService.keys
        fun makeUpdate(tx: NotaryChangeWireTransaction): Vault.Update<ContractState> {
            // We need to resolve the full transaction here because outputs are calculated from inputs
            // We also can't do filtering beforehand, since output encumbrance pointers get recalculated based on
            // input positions
            val ltx = tx.resolve(services, emptyList())
            val myKeys = services.keyManagementService.filterMyKeys(ltx.outputs.flatMap { it.data.participants.map { it.owningKey } })
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
            val session = DatabaseTransactionManager.current().session
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
        DatabaseTransactionManager.current().session.save(txnNoteEntity)
    }

    override fun getTransactionNotes(txnId: SecureHash): Iterable<String> {
        val session = DatabaseTransactionManager.current().session
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
        val softLockTimestamp = services.clock.instant()
        try {
            val session = DatabaseTransactionManager.current().session
            val criteriaBuilder = session.criteriaBuilder
            val criteriaUpdate = criteriaBuilder.createCriteriaUpdate(VaultSchemaV1.VaultStates::class.java)
            val vaultStates = criteriaUpdate.from(VaultSchemaV1.VaultStates::class.java)
            val stateStatusPredication = criteriaBuilder.equal(vaultStates.get<Vault.StateStatus>(VaultSchemaV1.VaultStates::stateStatus.name), Vault.StateStatus.UNCONSUMED)
            val lockIdPredicate = criteriaBuilder.or(vaultStates.get<String>(VaultSchemaV1.VaultStates::lockId.name).isNull,
                                  criteriaBuilder.equal(vaultStates.get<String>(VaultSchemaV1.VaultStates::lockId.name), lockId.toString()))
            val persistentStateRefs = stateRefs.map { PersistentStateRef(it.txhash.bytes.toHexString(), it.index) }
            val compositeKey = vaultStates.get<PersistentStateRef>(VaultSchemaV1.VaultStates::stateRef.name)
            val stateRefsPredicate = criteriaBuilder.and(compositeKey.`in`(persistentStateRefs))
            criteriaUpdate.set(vaultStates.get<String>(VaultSchemaV1.VaultStates::lockId.name), lockId.toString())
            criteriaUpdate.set(vaultStates.get<Instant>(VaultSchemaV1.VaultStates::lockUpdateTime.name), softLockTimestamp)
            criteriaUpdate.where(stateStatusPredication, lockIdPredicate, stateRefsPredicate)
            val updatedRows = session.createQuery(criteriaUpdate).executeUpdate()
            if (updatedRows > 0 && updatedRows == stateRefs.size) {
                log.trace("Reserving soft lock states for $lockId: $stateRefs")
                FlowStateMachineImpl.currentStateMachine()?.hasSoftLockedStates = true
            } else {
                // revert partial soft locks
                val criteriaRevertUpdate = criteriaBuilder.createCriteriaUpdate(VaultSchemaV1.VaultStates::class.java)
                val vaultStatesRevert = criteriaRevertUpdate.from(VaultSchemaV1.VaultStates::class.java)
                val lockIdPredicateRevert = criteriaBuilder.equal(vaultStatesRevert.get<String>(VaultSchemaV1.VaultStates::lockId.name), lockId.toString())
                val lockUpdateTime = criteriaBuilder.equal(vaultStatesRevert.get<Instant>(VaultSchemaV1.VaultStates::lockUpdateTime.name), softLockTimestamp)
                val persistentStateRefsRevert = stateRefs.map { PersistentStateRef(it.txhash.bytes.toHexString(), it.index) }
                val compositeKeyRevert = vaultStatesRevert.get<PersistentStateRef>(VaultSchemaV1.VaultStates::stateRef.name)
                val stateRefsPredicateRevert = criteriaBuilder.and(compositeKeyRevert.`in`(persistentStateRefsRevert))
                criteriaRevertUpdate.set(vaultStatesRevert.get<String>(VaultSchemaV1.VaultStates::lockId.name), criteriaBuilder.nullLiteral(String::class.java))
                criteriaRevertUpdate.where(lockUpdateTime, lockIdPredicateRevert, stateRefsPredicateRevert)
                val revertUpdatedRows = session.createQuery(criteriaRevertUpdate).executeUpdate()
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
        val softLockTimestamp = services.clock.instant()
        val session = DatabaseTransactionManager.current().session
        val criteriaBuilder = session.criteriaBuilder
        if (stateRefs == null) {
            val criteriaUpdate = criteriaBuilder.createCriteriaUpdate(VaultSchemaV1.VaultStates::class.java)
            val vaultStates = criteriaUpdate.from(VaultSchemaV1.VaultStates::class.java)
            val stateStatusPredication = criteriaBuilder.equal(vaultStates.get<Vault.StateStatus>(VaultSchemaV1.VaultStates::stateStatus.name), Vault.StateStatus.UNCONSUMED)
            val lockIdPredicate = criteriaBuilder.equal(vaultStates.get<String>(VaultSchemaV1.VaultStates::lockId.name), lockId.toString())
            criteriaUpdate.set<String>(vaultStates.get<String>(VaultSchemaV1.VaultStates::lockId.name), criteriaBuilder.nullLiteral(String::class.java))
            criteriaUpdate.set(vaultStates.get<Instant>(VaultSchemaV1.VaultStates::lockUpdateTime.name), softLockTimestamp)
            criteriaUpdate.where(stateStatusPredication, lockIdPredicate)
            val update = session.createQuery(criteriaUpdate).executeUpdate()
            if (update > 0) {
                log.trace("Releasing $update soft locked states for $lockId")
            }
        } else {
            try {
                val criteriaUpdate = criteriaBuilder.createCriteriaUpdate(VaultSchemaV1.VaultStates::class.java)
                val vaultStates = criteriaUpdate.from(VaultSchemaV1.VaultStates::class.java)
                val stateStatusPredication = criteriaBuilder.equal(vaultStates.get<Vault.StateStatus>(VaultSchemaV1.VaultStates::stateStatus.name), Vault.StateStatus.UNCONSUMED)
                val lockIdPredicate = criteriaBuilder.equal(vaultStates.get<String>(VaultSchemaV1.VaultStates::lockId.name), lockId.toString())
                val persistentStateRefs = stateRefs.map { PersistentStateRef(it.txhash.bytes.toHexString(), it.index) }
                val compositeKey = vaultStates.get<PersistentStateRef>(VaultSchemaV1.VaultStates::stateRef.name)
                val stateRefsPredicate = criteriaBuilder.and(compositeKey.`in`(persistentStateRefs))
                criteriaUpdate.set<String>(vaultStates.get<String>(VaultSchemaV1.VaultStates::lockId.name), criteriaBuilder.nullLiteral(String::class.java))
                criteriaUpdate.set(vaultStates.get<Instant>(VaultSchemaV1.VaultStates::lockUpdateTime.name), softLockTimestamp)
                criteriaUpdate.where(stateStatusPredication, lockIdPredicate, stateRefsPredicate)
                val updatedRows = session.createQuery(criteriaUpdate).executeUpdate()
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
                                                                                  contractType: Class<out T>): List<StateAndRef<T>> {
        if (amount.quantity == 0L) {
            return emptyList()
        }

        // Enrich QueryCriteria with additional default attributes (such as soft locks)
        val sortAttribute = SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF)
        val sorter = Sort(setOf(Sort.SortColumn(sortAttribute, Sort.Direction.ASC)))
        val enrichedCriteria = QueryCriteria.VaultQueryCriteria(
                contractStateTypes = setOf(contractType),
                softLockingCondition = QueryCriteria.SoftLockingCondition(QueryCriteria.SoftLockingType.UNLOCKED_AND_SPECIFIED, listOf(lockId)))
        val results = services.vaultQueryService.queryBy(contractType, enrichedCriteria.and(eligibleStatesQuery), sorter)

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
    internal fun isRelevant(state: ContractState, myKeys: Set<PublicKey>): Boolean {
        val keysToCheck = when (state) {
            is OwnableState -> listOf(state.owner.owningKey)
            else -> state.participants.map { it.owningKey }
        }
        return keysToCheck.any { it in myKeys }
    }
}

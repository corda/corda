package net.corda.node.services.vault

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import io.requery.PersistenceException
import io.requery.TransactionIsolation
import io.requery.kotlin.`in`
import io.requery.kotlin.eq
import io.requery.kotlin.isNull
import io.requery.kotlin.notNull
import io.requery.query.RowExpression
import net.corda.contracts.asset.Cash
import net.corda.contracts.asset.OnLedgerAsset
import net.corda.contracts.clause.AbstractConserveAmount
import net.corda.core.ThreadBox
import net.corda.core.bufferUntilSubscribed
import net.corda.core.contracts.*
import net.corda.core.crypto.*
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.StatesNotAvailableException
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.unconsumedStates
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.serialization.*
import net.corda.core.tee
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.trace
import net.corda.node.services.database.RequeryConfiguration
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.services.vault.schemas.*
import net.corda.node.utilities.bufferUntilDatabaseCommit
import net.corda.node.utilities.wrapWithDatabaseTransaction
import rx.Observable
import rx.subjects.PublishSubject
import java.security.PublicKey
import java.sql.SQLException
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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
class NodeVaultService(private val services: ServiceHub, dataSourceProperties: Properties) : SingletonSerializeAsToken(), VaultService {

    private companion object {
        val log = loggerFor<NodeVaultService>()

        // Define composite primary key used in Requery Expression
        val stateRefCompositeColumn: RowExpression = RowExpression.of(listOf(VaultStatesEntity.TX_ID, VaultStatesEntity.INDEX))
    }

    val configuration = RequeryConfiguration(dataSourceProperties)
    val session = configuration.sessionForModel(Models.VAULT)

    private class InnerState {
        val _updatesPublisher = PublishSubject.create<Vault.Update>()!!
        val _rawUpdatesPublisher = PublishSubject.create<Vault.Update>()!!
        val _updatesInDbTx = _updatesPublisher.wrapWithDatabaseTransaction().asObservable()!!

        // For use during publishing only.
        val updatesPublisher: rx.Observer<Vault.Update> get() = _updatesPublisher.bufferUntilDatabaseCommit().tee(_rawUpdatesPublisher)
    }
    private val mutex = ThreadBox(InnerState())

    private fun recordUpdate(update: Vault.Update): Vault.Update {
        if (update != Vault.NoUpdate) {
            val producedStateRefs = update.produced.map { it.ref }
            val producedStateRefsMap = update.produced.associateBy { it.ref }
            val consumedStateRefs = update.consumed.map { it.ref }
            log.trace { "Removing $consumedStateRefs consumed contract states and adding $producedStateRefs produced contract states to the database." }

            session.withTransaction(TransactionIsolation.REPEATABLE_READ) {
                producedStateRefsMap.forEach { it ->
                    val state = VaultStatesEntity().apply {
                        txId = it.key.txhash.toString()
                        index = it.key.index
                        stateStatus = Vault.StateStatus.UNCONSUMED
                        contractStateClassName = it.value.state.data.javaClass.name
                        contractState = it.value.state.serialize(storageKryo()).bytes
                        notaryName = it.value.state.notary.name
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

    // TODO: consider moving this logic outside the vault
    // TODO: revisit the concurrency safety of this logic when we move beyond single threaded SMM.
    //       For example, we update currency totals in a non-deterministic order and so expose ourselves to deadlock.
    private fun maybeUpdateCashBalances(update: Vault.Update) {
        if (update.containsType<Cash.State>()) {
            val consumed = sumCashStates(update.consumed)
            val produced = sumCashStates(update.produced)
            (produced.keys + consumed.keys).map { currency ->
                val producedAmount = produced[currency] ?: Amount(0, currency)
                val consumedAmount = consumed[currency] ?: Amount(0, currency)

                val cashBalanceEntity = VaultCashBalancesEntity()
                cashBalanceEntity.currency = currency.currencyCode
                cashBalanceEntity.amount = producedAmount.quantity - consumedAmount.quantity

                session.withTransaction(TransactionIsolation.REPEATABLE_READ) {
                    val state = findByKey(VaultCashBalancesEntity::class, currency.currencyCode)
                    state?.run {
                        amount += producedAmount.quantity - consumedAmount.quantity
                    }
                    upsert(state ?: cashBalanceEntity)
                    val total = state?.amount ?: cashBalanceEntity.amount
                    log.trace { "Updating Cash balance for $currency by ${cashBalanceEntity.amount} pennies (total: $total)" }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun sumCashStates(states: Iterable<StateAndRef<ContractState>>): Map<Currency, Amount<Currency>> {
        return states.mapNotNull { (it.state.data as? FungibleAsset<Currency>)?.amount }
                .groupBy { it.token.product }
                .mapValues { it.value.map { Amount(it.quantity, it.token.product) }.sumOrThrow() }
    }

    override val cashBalances: Map<Currency, Amount<Currency>> get() {
        val cashBalancesByCurrency =
                session.withTransaction(TransactionIsolation.REPEATABLE_READ) {
                    val balances = select(VaultSchema.VaultCashBalances::class)
                    balances.get().toList()
                }
        return cashBalancesByCurrency.associateBy({ Currency.getInstance(it.currency) },
                { Amount(it.amount, Currency.getInstance(it.currency)) })
    }

    override val rawUpdates: Observable<Vault.Update>
        get() = mutex.locked { _rawUpdatesPublisher }

    override val updates: Observable<Vault.Update>
        get() = mutex.locked { _updatesInDbTx }

    override fun track(): Pair<Vault<ContractState>, Observable<Vault.Update>> {
        return mutex.locked {
            Pair(Vault(unconsumedStates<ContractState>()), _updatesPublisher.bufferUntilSubscribed().wrapWithDatabaseTransaction())
        }
    }

    override fun <T : ContractState> states(clazzes: Set<Class<T>>, statuses: EnumSet<Vault.StateStatus>, includeSoftLockedStates: Boolean): Iterable<StateAndRef<T>> {
        val stateAndRefs =
                session.withTransaction(TransactionIsolation.REPEATABLE_READ) {
                    val query = select(VaultSchema.VaultStates::class)
                            .where(VaultSchema.VaultStates::stateStatus `in` statuses)
                    // TODO: temporary fix to continue supporting track() function (until becomes Typed)
                    if (!clazzes.map { it.name }.contains(ContractState::class.java.name))
                        query.and(VaultSchema.VaultStates::contractStateClassName `in` (clazzes.map { it.name }))
                    if (!includeSoftLockedStates)
                        query.and(VaultSchema.VaultStates::lockId.isNull())
                    val iterator = query.get().iterator()
                    Sequence { iterator }
                            .map { it ->
                                val stateRef = StateRef(SecureHash.parse(it.txId), it.index)
                                val state = it.contractState.deserialize<TransactionState<T>>(storageKryo())
                                Vault.StateMetadata(stateRef, it.contractStateClassName, it.recordedTime, it.consumedTime, it.stateStatus, it.notaryName, it.notaryKey, it.lockId, it.lockUpdateTime)
                                StateAndRef(state, stateRef)
                            }
                }
        return stateAndRefs.asIterable()
    }

    override fun statesForRefs(refs: List<StateRef>): Map<StateRef, TransactionState<*>?> {
        val stateAndRefs =
                session.withTransaction(TransactionIsolation.REPEATABLE_READ) {
                    var results: List<StateAndRef<*>> = emptyList()
                    refs.forEach {
                        val result = select(VaultSchema.VaultStates::class)
                                .where(VaultSchema.VaultStates::stateStatus eq Vault.StateStatus.UNCONSUMED)
                                .and(VaultSchema.VaultStates::txId eq it.txhash.toString())
                                .and(VaultSchema.VaultStates::index eq it.index)
                        result.get()?.each {
                            val stateRef = StateRef(SecureHash.parse(it.txId), it.index)
                            val state = it.contractState.deserialize<TransactionState<*>>(storageKryo())
                            results += StateAndRef(state, stateRef)
                        }
                    }
                    results
                }

        return stateAndRefs.associateBy({ it.ref }, { it.state })
    }

    override fun <T : ContractState> queryBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort?): Vault.Page<T> {

        TODO("not implemented")

        // If [VaultQueryCriteria.PageSpecification] specified
        // must return (CloseableIterator) result.get().iterator(skip, take)
        // where
        //  skip = Max[(pageNumber - 1),0] * pageSize
        //  take = pageSize
    }

    override fun <T : ContractState> trackBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort?): Vault.QueryResults<T> {
        TODO("not implemented")

        return mutex.locked {
            Vault.QueryResults(queryBy(criteria),
                              _updatesPublisher.bufferUntilSubscribed().wrapWithDatabaseTransaction())
        }
    }

    override fun notifyAll(txns: Iterable<WireTransaction>) {
        val ourKeys = services.keyManagementService.keys.keys
        val netDelta = txns.fold(Vault.NoUpdate) { netDelta, txn -> netDelta + makeUpdate(txn, ourKeys) }
        if (netDelta != Vault.NoUpdate) {
            recordUpdate(netDelta)
            maybeUpdateCashBalances(netDelta)
            mutex.locked {
                // flowId required by SoftLockManager to perform auto-registration of soft locks for new states
                val uuid = (Strand.currentStrand() as? FlowStateMachineImpl<*>)?.id?.uuid
                val vaultUpdate = if (uuid != null) netDelta.copy(flowId = uuid) else netDelta
                updatesPublisher.onNext(vaultUpdate)
            }
        }
    }

    override fun addNoteToTransaction(txnId: SecureHash, noteText: String) {
        session.withTransaction(TransactionIsolation.REPEATABLE_READ) {
            val txnNoteEntity = VaultTxnNoteEntity()
            txnNoteEntity.txId = txnId.toString()
            txnNoteEntity.note = noteText
            insert(txnNoteEntity)
        }
    }

    override fun getTransactionNotes(txnId: SecureHash): Iterable<String> {
        return session.withTransaction(TransactionIsolation.REPEATABLE_READ) {
            (select(VaultSchema.VaultTxnNote::class) where (VaultSchema.VaultTxnNote::txId eq txnId.toString())).get().asIterable().map { it.note }
        }
    }

    @Throws(StatesNotAvailableException::class)
    override fun softLockReserve(lockId: UUID, stateRefs: Set<StateRef>) {
        if (stateRefs.isNotEmpty()) {
            val softLockTimestamp = services.clock.instant()
            val stateRefArgs = stateRefArgs(stateRefs)
            try {
                session.withTransaction(TransactionIsolation.REPEATABLE_READ) {
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
    }

    override fun softLockRelease(lockId: UUID, stateRefs: Set<StateRef>?) {
        if (stateRefs == null) {
            session.withTransaction(TransactionIsolation.REPEATABLE_READ) {
                val update = update(VaultStatesEntity::class)
                        .set(VaultStatesEntity.LOCK_ID, null)
                        .set(VaultStatesEntity.LOCK_UPDATE_TIME, services.clock.instant())
                        .where(VaultStatesEntity.STATE_STATUS eq Vault.StateStatus.UNCONSUMED)
                        .and(VaultStatesEntity.LOCK_ID eq lockId.toString()).get()
                if (update.value() > 0) {
                    log.trace("Releasing ${update.value()} soft locked states for $lockId")
                }
            }
        } else if (stateRefs.isNotEmpty()) {
            try {
                session.withTransaction(TransactionIsolation.REPEATABLE_READ) {
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

    // coin selection retry loop counter, sleep (msecs) and lock for selecting states
    val MAX_RETRIES = 5
    val RETRY_SLEEP = 100
    val spendLock: ReentrantLock = ReentrantLock()

    @Suspendable
    override fun <T : ContractState> unconsumedStatesForSpending(amount: Amount<Currency>, onlyFromIssuerParties: Set<AbstractParty>?, notary: Party?, lockId: UUID, withIssuerRefs: Set<OpaqueBytes>?): List<StateAndRef<T>> {

        val issuerKeysStr = onlyFromIssuerParties?.fold("") { left, right -> left + "('${right.owningKey.toBase58String()}')," }?.dropLast(1)
        val issuerRefsStr = withIssuerRefs?.fold("") { left, right -> left + "('${right.bytes.toHexString()}')," }?.dropLast(1)

        val stateAndRefs = mutableListOf<StateAndRef<T>>()

        // TODO: Need to provide a database provider independent means of performing this function.
        //       We are using an H2 specific means of selecting a minimum set of rows that match a request amount of coins:
        //       1) There is no standard SQL mechanism of calculating a cumulative total on a field and restricting row selection on the
        //          running total of such an accumulator
        //       2) H2 uses session variables to perform this accumulator function:
        //          http://www.h2database.com/html/functions.html#set
        //       3) H2 does not support JOIN's in FOR UPDATE (hence we are forced to execute 2 queries)

        for (retryCount in 1..MAX_RETRIES) {

            spendLock.withLock {
                val statement = configuration.jdbcSession().createStatement()
                try {
                    statement.execute("CALL SET(@t, 0);")

                    // we select spendable states irrespective of lock but prioritised by unlocked ones (Eg. null)
                    // the softLockReserve update will detect whether we try to lock states locked by others
                    val selectJoin = """
                        SELECT vs.transaction_id, vs.output_index, vs.contract_state, ccs.pennies, SET(@t, ifnull(@t,0)+ccs.pennies) total_pennies, vs.lock_id
                        FROM vault_states AS vs, contract_cash_states AS ccs
                        WHERE vs.transaction_id = ccs.transaction_id AND vs.output_index = ccs.output_index
                        AND vs.state_status = 0
                        AND ccs.ccy_code = '${amount.token}' and @t < ${amount.quantity}
                        AND (vs.lock_id = '$lockId' OR vs.lock_id is null)
                        """ +
                            (if (notary != null)
                                " AND vs.notary_key = '${notary.owningKey.toBase58String()}'" else "") +
                            (if (issuerKeysStr != null)
                                " AND ccs.issuer_key IN ($issuerKeysStr)" else "") +
                            (if (issuerRefsStr != null)
                                " AND ccs.issuer_ref IN ($issuerRefsStr)" else "")

                    // Retrieve spendable state refs
                    val rs = statement.executeQuery(selectJoin)
                    stateAndRefs.clear()
                    log.debug(selectJoin)
                    var totalPennies = 0L
                    while (rs.next()) {
                        val txHash = SecureHash.parse(rs.getString(1))
                        val index = rs.getInt(2)
                        val stateRef = StateRef(txHash, index)
                        val state = rs.getBytes(3).deserialize<TransactionState<T>>(storageKryo())
                        val pennies = rs.getLong(4)
                        totalPennies = rs.getLong(5)
                        val rowLockId = rs.getString(6)
                        stateAndRefs.add(StateAndRef(state, stateRef))
                        log.trace { "ROW: $rowLockId ($lockId): $stateRef : $pennies ($totalPennies)" }
                    }

                    if (stateAndRefs.isNotEmpty() && totalPennies >= amount.quantity) {
                        // we should have a minimum number of states to satisfy our selection `amount` criteria
                        log.trace("Coin selection for $amount retrieved ${stateAndRefs.count()} states totalling $totalPennies pennies: $stateAndRefs")

                        // update database
                        softLockReserve(lockId, stateAndRefs.map { it.ref }.toSet())
                        return stateAndRefs
                    }
                    log.trace("Coin selection requested $amount but retrieved $totalPennies pennies with state refs: ${stateAndRefs.map { it.ref }}")
                    // retry as more states may become available
                } catch (e: SQLException) {
                    log.error("""Failed retrieving unconsumed states for: amount [$amount], onlyFromIssuerParties [$onlyFromIssuerParties], notary [$notary], lockId [$lockId]
                            $e.
                        """)
                } catch (e: StatesNotAvailableException) {
                    stateAndRefs.clear()
                    log.warn(e.message)
                    // retry only if there are locked states that may become available again (or consumed with change)
                } finally {
                    statement.close()
                }
            }

            log.warn("Coin selection failed on attempt $retryCount")
            // TODO: revisit the back off strategy for contended spending.
            if (retryCount != MAX_RETRIES) {
                FlowStateMachineImpl.sleep(RETRY_SLEEP * retryCount.toLong())
            }
        }

        log.warn("Insufficient spendable states identified for $amount")
        return stateAndRefs
    }

    override fun <T : ContractState> softLockedStates(lockId: UUID?): List<StateAndRef<T>> {
        val stateAndRefs =
                session.withTransaction(TransactionIsolation.REPEATABLE_READ) {
                    val query = select(VaultSchema.VaultStates::class)
                            .where(VaultSchema.VaultStates::stateStatus eq Vault.StateStatus.UNCONSUMED)
                            .and(VaultSchema.VaultStates::contractStateClassName eq Cash.State::class.java.name)
                    if (lockId != null)
                        query.and(VaultSchema.VaultStates::lockId eq lockId)
                    else
                        query.and(VaultSchema.VaultStates::lockId.notNull())
                    query.get()
                            .map { it ->
                                val stateRef = StateRef(SecureHash.parse(it.txId), it.index)
                                val state = it.contractState.deserialize<TransactionState<T>>(storageKryo())
                                StateAndRef(state, stateRef)
                            }.toList()
                }
        return stateAndRefs
    }

    /**
     * Generate a transaction that moves an amount of currency to the given pubkey.
     *
     * @param onlyFromParties if non-null, the asset states will be filtered to only include those issued by the set
     *                        of given parties. This can be useful if the party you're trying to pay has expectations
     *                        about which type of asset claims they are willing to accept.
     */
    @Suspendable
    override fun generateSpend(tx: TransactionBuilder,
                               amount: Amount<Currency>,
                               to: PublicKey,
                               onlyFromParties: Set<AbstractParty>?): Pair<TransactionBuilder, List<PublicKey>> {
        // Retrieve unspent and unlocked cash states that meet our spending criteria.
        val acceptableCoins = unconsumedStatesForSpending<Cash.State>(amount, onlyFromParties, tx.notary, tx.lockId)
        return OnLedgerAsset.generateSpend(tx, amount, to, acceptableCoins,
                { state, amount, owner -> deriveState(state, amount, owner) },
                { Cash().generateMoveCommand() })
    }

    private fun deriveState(txState: TransactionState<Cash.State>, amount: Amount<Issued<Currency>>, owner: PublicKey)
            = txState.copy(data = txState.data.copy(amount = amount, owner = owner))

    private fun makeUpdate(tx: WireTransaction, ourKeys: Set<PublicKey>): Vault.Update {
        val ourNewStates = tx.outputs.
                filter { isRelevant(it.data, ourKeys) }.
                map { tx.outRef<ContractState>(it.data) }

        // Retrieve all unconsumed states for this transaction's inputs
        val consumedStates = HashSet<StateAndRef<ContractState>>()
        if (tx.inputs.isNotEmpty()) {
            session.withTransaction(TransactionIsolation.REPEATABLE_READ) {
                val result = select(VaultStatesEntity::class).
                        where(stateRefCompositeColumn.`in`(stateRefArgs(tx.inputs))).
                        and(VaultSchema.VaultStates::stateStatus eq Vault.StateStatus.UNCONSUMED)
                result.get().forEach {
                    val txHash = SecureHash.parse(it.txId)
                    val index = it.index
                    val state = it.contractState.deserialize<TransactionState<ContractState>>(storageKryo())
                    consumedStates.add(StateAndRef(state, StateRef(txHash, index)))
                }
            }
        }

        // Is transaction irrelevant?
        if (consumedStates.isEmpty() && ourNewStates.isEmpty()) {
            log.trace { "tx ${tx.id} was irrelevant to this vault, ignoring" }
            return Vault.NoUpdate
        }

        return Vault.Update(consumedStates, ourNewStates.toHashSet())
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

    private fun isRelevant(state: ContractState, ourKeys: Set<PublicKey>) = when (state) {
        is OwnableState -> state.owner.containsAny(ourKeys)
    // It's potentially of interest to the vault
        is LinearState -> state.isRelevant(ourKeys)
        else -> false
    }

    /**
     * Helper method to generate a string formatted list of Composite Keys for Requery Expression clause
     */
    private fun stateRefArgs(stateRefs: Iterable<StateRef>): List<List<Any>> {
        return stateRefs.map { listOf("'${it.txhash}'", it.index) }
    }
}

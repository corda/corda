package net.corda.node.services.vault

import io.requery.TransactionIsolation
import io.requery.kotlin.`in`
import io.requery.kotlin.eq
import net.corda.contracts.asset.Cash
import net.corda.core.ThreadBox
import net.corda.core.bufferUntilSubscribed
import net.corda.core.contracts.*
import net.corda.core.crypto.AbstractParty
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.SecureHash
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.unconsumedStates
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.serialization.storageKryo
import net.corda.core.tee
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.trace
import net.corda.node.services.database.RequeryConfiguration
import net.corda.node.services.vault.schemas.*
import net.corda.node.utilities.bufferUntilDatabaseCommit
import net.corda.node.utilities.wrapWithDatabaseTransaction
import rx.Observable
import rx.subjects.PublishSubject
import java.security.PublicKey
import java.util.*

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
    }

    val configuration = RequeryConfiguration(dataSourceProperties)
    val session = configuration.sessionForModel(Models.VAULT)

    private val mutex = ThreadBox(content = object {

        val _updatesPublisher = PublishSubject.create<Vault.Update>()
        val _rawUpdatesPublisher = PublishSubject.create<Vault.Update>()
        val _updatesInDbTx = _updatesPublisher.wrapWithDatabaseTransaction().asObservable()

        // For use during publishing only.
        val updatesPublisher: rx.Observer<Vault.Update> get() = _updatesPublisher.bufferUntilDatabaseCommit().tee(_rawUpdatesPublisher)

        fun recordUpdate(update: Vault.Update): Vault.Update {
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
                    consumedStateRefs.forEach { stateRef ->
                        val queryKey = io.requery.proxy.CompositeKey(mapOf(VaultStatesEntity.TX_ID to stateRef.txhash.toString(),
                                                                           VaultStatesEntity.INDEX to stateRef.index))
                        val state = findByKey(VaultStatesEntity::class, queryKey)
                        state?.run {
                            stateStatus = Vault.StateStatus.CONSUMED
                            consumedTime = services.clock.instant()
                            update(state)
                        }
                    }
                }
            }
            return update
        }

        // TODO: consider moving this logic outside the vault
        fun maybeUpdateCashBalances(update: Vault.Update) {
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
    })

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

    override fun <T: ContractState> states(clazzes: Set<Class<T>>, statuses: EnumSet<Vault.StateStatus>): Iterable<StateAndRef<T>> {
        val stateAndRefs =
            session.withTransaction(TransactionIsolation.REPEATABLE_READ) {
                var result = select(VaultSchema.VaultStates::class)
                                .where(VaultSchema.VaultStates::stateStatus `in` statuses)
                // TODO: temporary fix to continue supporting track() function (until becomes Typed)
                if (!clazzes.map {it.name}.contains(ContractState::class.java.name))
                    result.and (VaultSchema.VaultStates::contractStateClassName `in` (clazzes.map { it.name }))
                val iterator = result.get().iterator()
                Sequence{iterator}
                        .map { it ->
                            val stateRef = StateRef(SecureHash.parse(it.txId), it.index)
                            val state = it.contractState.deserialize<TransactionState<T>>(storageKryo())
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

    override fun notifyAll(txns: Iterable<WireTransaction>) {
        val ourKeys = services.keyManagementService.keys.keys
        val netDelta = txns.fold(Vault.NoUpdate) { netDelta, txn -> netDelta + makeUpdate(txn, ourKeys) }
        if (netDelta != Vault.NoUpdate) {
            mutex.locked {
                recordUpdate(netDelta)
                maybeUpdateCashBalances(netDelta)
                updatesPublisher.onNext(netDelta)
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

    override fun generateSpend(tx: TransactionBuilder,
                               amount: Amount<Currency>,
                               to: CompositeKey,
                               onlyFromParties: Set<AbstractParty>?): Pair<TransactionBuilder, List<CompositeKey>> {
        // Discussion
        //
        // This code is analogous to the Wallet.send() set of methods in bitcoinj, and has the same general outline.
        //
        // First we must select a set of asset states (which for convenience we will call 'coins' here, as in bitcoinj).
        // The input states can be considered our "vault", and may consist of different products, and with different
        // issuers and deposits.
        //
        // Coin selection is a complex problem all by itself and many different approaches can be used. It is easily
        // possible for different actors to use different algorithms and approaches that, for example, compete on
        // privacy vs efficiency (number of states created). Some spends may be artificial just for the purposes of
        // obfuscation and so on.
        //
        // Having selected input states of the correct asset, we must craft output states for the amount we're sending and
        // the "change", which goes back to us. The change is required to make the amounts balance. We may need more
        // than one change output in order to avoid merging assets from different deposits. The point of this design
        // is to ensure that ledger entries are immutable and globally identifiable.
        //
        // Finally, we add the states to the provided partial transaction.

        val assetsStates = unconsumedStates<Cash.State>()

        val currency = amount.token
        var acceptableCoins = run {
            val ofCurrency = assetsStates.filter { it.state.data.amount.token.product == currency }
            if (onlyFromParties != null)
                ofCurrency.filter { it.state.data.amount.token.issuer.party in onlyFromParties }
            else
                ofCurrency
        }
        tx.notary = acceptableCoins.firstOrNull()?.state?.notary
        // TODO: We should be prepared to produce multiple transactions spending inputs from
        // different notaries, or at least group states by notary and take the set with the
        // highest total value
        acceptableCoins = acceptableCoins.filter { it.state.notary == tx.notary }

        val (gathered, gatheredAmount) = gatherCoins(acceptableCoins, amount)
        val takeChangeFrom = gathered.firstOrNull()
        val change = if (takeChangeFrom != null && gatheredAmount > amount) {
            Amount(gatheredAmount.quantity - amount.quantity, takeChangeFrom.state.data.amount.token)
        } else {
            null
        }
        val keysUsed = gathered.map { it.state.data.owner }.toSet()

        val states = gathered.groupBy { it.state.data.amount.token.issuer }.map {
            val coins = it.value
            val totalAmount = coins.map { it.state.data.amount }.sumOrThrow()
            deriveState(coins.first().state, totalAmount, to)
        }.sortedBy { it.data.amount.quantity }

        val outputs = if (change != null) {
            // Just copy a key across as the change key. In real life of course, this works but leaks private data.
            // In bitcoinj we derive a fresh key here and then shuffle the outputs to ensure it's hard to follow
            // value flows through the transaction graph.
            val existingOwner = gathered.first().state.data.owner
            // Add a change output and adjust the last output downwards.
            states.subList(0, states.lastIndex) +
                    states.last().let {
                        val spent = it.data.amount.withoutIssuer() - change.withoutIssuer()
                        deriveState(it, Amount(spent.quantity, it.data.amount.token), it.data.owner)
                    } +
                    states.last().let {
                        deriveState(it, Amount(change.quantity, it.data.amount.token), existingOwner)
                    }
        } else states

        for (state in gathered) tx.addInputState(state)
        for (state in outputs) tx.addOutputState(state)

        // What if we already have a move command with the right keys? Filter it out here or in platform code?
        val keysList = keysUsed.toList()
        tx.addCommand(Cash().generateMoveCommand(), keysList)

        // update Vault
        //        notify(tx.toWireTransaction())
        // Vault update must be completed AFTER transaction is recorded to ledger storage!!!
        // (this is accomplished within the recordTransaction function)

        return Pair(tx, keysList)
    }

    private fun deriveState(txState: TransactionState<Cash.State>, amount: Amount<Issued<Currency>>, owner: CompositeKey)
            = txState.copy(data = txState.data.copy(amount = amount, owner = owner))

    /**
     * Gather assets from the given list of states, sufficient to match or exceed the given amount.
     *
     * @param acceptableCoins list of states to use as inputs.
     * @param amount the amount to gather states up to.
     * @throws InsufficientBalanceException if there isn't enough value in the states to cover the requested amount.
     */
    @Throws(InsufficientBalanceException::class)
    private fun gatherCoins(acceptableCoins: Collection<StateAndRef<Cash.State>>,
                            amount: Amount<Currency>): Pair<ArrayList<StateAndRef<Cash.State>>, Amount<Currency>> {
        val gathered = arrayListOf<StateAndRef<Cash.State>>()
        var gatheredAmount = Amount(0, amount.token)
        for (c in acceptableCoins) {
            if (gatheredAmount >= amount) break
            gathered.add(c)
            gatheredAmount += Amount(c.state.data.amount.quantity, amount.token)
        }

        if (gatheredAmount < amount)
            throw InsufficientBalanceException(amount - gatheredAmount)

        return Pair(gathered, gatheredAmount)
    }

    private fun makeUpdate(tx: WireTransaction, ourKeys: Set<PublicKey>): Vault.Update {
        val ourNewStates = tx.outputs.
                filter { isRelevant(it.data, ourKeys) }.
                map { tx.outRef<ContractState>(it.data) }

        // Retrieve all unconsumed states for this transaction's inputs
        val consumedStates = HashSet<StateAndRef<ContractState>>()
        if (tx.inputs.isNotEmpty()) {
            val stateRefs = tx.inputs.fold("") { stateRefs, it -> stateRefs + "('${it.txhash}','${it.index}')," }.dropLast(1)
            // TODO: using native JDBC until requery supports SELECT WHERE COMPOSITE_KEY IN
            // https://github.com/requery/requery/issues/434
            val statement = configuration.jdbcSession().createStatement()
            val rs = statement.executeQuery("SELECT transaction_id, output_index, contract_state " +
                    "FROM vault_states " +
                    "WHERE ((transaction_id, output_index) IN ($stateRefs)) " +
                    "AND (state_status = 0)")
            while (rs.next()) {
                val txHash = SecureHash.parse(rs.getString(1))
                val index = rs.getInt(2)
                val state = rs.getBytes(3).deserialize<TransactionState<ContractState>>(storageKryo())
                consumedStates.add(StateAndRef(state, StateRef(txHash, index)))
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
}
package com.r3corda.node.services.vault

import com.google.common.collect.Sets
import com.r3corda.contracts.asset.Cash
import com.r3corda.core.ThreadBox
import com.r3corda.core.bufferUntilSubscribed
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.node.services.Vault
import com.r3corda.core.node.services.VaultService
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.core.transactions.TransactionBuilder
import com.r3corda.core.transactions.WireTransaction
import com.r3corda.core.utilities.loggerFor
import com.r3corda.core.utilities.trace
import com.r3corda.node.utilities.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
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
class NodeVaultService(private val services: ServiceHub) : SingletonSerializeAsToken(), VaultService {

    private companion object {
        val log = loggerFor<NodeVaultService>()
    }

    private object StatesSetTable : JDBCHashedTable("${NODE_DATABASE_PREFIX}vault_unconsumed_states") {
        val stateRef = stateRef("transaction_id", "output_index")
    }

    private object TransactionNotesTable : JDBCHashedTable("${NODE_DATABASE_PREFIX}vault_txn_notes") {
        val txnId = secureHash("txnId")
        val notes = text("notes")
    }

    private val mutex = ThreadBox(content = object {
        val unconsumedStates = object : AbstractJDBCHashSet<StateRef, StatesSetTable>(StatesSetTable) {
            override fun elementFromRow(row: ResultRow): StateRef = StateRef(row[table.stateRef.txId], row[table.stateRef.index])

            override fun addElementToInsert(insert: InsertStatement, entry: StateRef, finalizables: MutableList<() -> Unit>) {
                insert[table.stateRef.txId] = entry.txhash
                insert[table.stateRef.index] = entry.index
            }
        }

        val transactionNotes = object : AbstractJDBCHashMap<SecureHash, Set<String>, TransactionNotesTable>(TransactionNotesTable, loadOnInit = false) {
            override fun keyFromRow(row: ResultRow): SecureHash {
                return row[table.txnId]
            }

            override fun valueFromRow(row: ResultRow): Set<String> {
                return row[table.notes].split(delimiters = ";").toSet()
            }

            override fun addKeyToInsert(insert: InsertStatement, entry: Map.Entry<SecureHash, Set<String>>, finalizables: MutableList<() -> Unit>) {
                insert[table.txnId] = entry.key
            }

            override fun addValueToInsert(insert: InsertStatement, entry: Map.Entry<SecureHash, Set<String>>, finalizables: MutableList<() -> Unit>) {
                insert[table.notes] = entry.value.joinToString(separator = ";")
            }
        }

        val _updatesPublisher = PublishSubject.create<Vault.Update>()

        fun allUnconsumedStates(): Iterable<StateAndRef<ContractState>> {
            // Order by txhash for if and when transaction storage has some caching.
            // Map to StateRef and then to StateAndRef.  Use Sequence to avoid conversion to ArrayList that Iterable.map() performs.
            return unconsumedStates.asSequence().map {
                val storedTx = services.storageService.validatedTransactions.getTransaction(it.txhash) ?: throw Error("Found transaction hash ${it.txhash} in unconsumed contract states that is not in transaction storage.")
                StateAndRef(storedTx.tx.outputs[it.index], it)
            }.asIterable()
        }

        fun recordUpdate(update: Vault.Update): Vault.Update {
            if (update != Vault.NoUpdate) {
                val producedStateRefs = update.produced.map { it.ref }
                val consumedStateRefs = update.consumed
                log.trace { "Removing $consumedStateRefs consumed contract states and adding $producedStateRefs produced contract states to the database." }
                unconsumedStates.removeAll(consumedStateRefs)
                unconsumedStates.addAll(producedStateRefs)
            }
            return update
        }
    })

    override val currentVault: Vault get() = mutex.locked { Vault(allUnconsumedStates(), transactionNotes) }

    override val updates: Observable<Vault.Update>
        get() = mutex.locked { _updatesPublisher }

    override fun track(): Pair<Vault, Observable<Vault.Update>> {
        return mutex.locked {
            Pair(Vault(allUnconsumedStates(), transactionNotes), _updatesPublisher.bufferUntilSubscribed())
        }
    }

    /**
     * Returns a snapshot of the heads of LinearStates.
     *
     * TODO: Represent this using an actual JDBCHashMap or look at vault design further.
     */
    override val linearHeads: Map<UniqueIdentifier, StateAndRef<LinearState>>
        get() = currentVault.states.filterStatesOfType<LinearState>().associateBy { it.state.data.linearId }.mapValues { it.value }

    override fun notifyAll(txns: Iterable<WireTransaction>): Vault {
        val ourKeys = services.keyManagementService.keys.keys
        val netDelta = txns.fold(Vault.NoUpdate) { netDelta, txn -> netDelta + makeUpdate(txn, netDelta, ourKeys) }
        if (netDelta != Vault.NoUpdate) {
            mutex.locked {
                recordUpdate(netDelta)
                _updatesPublisher.onNext(netDelta)
            }
        }
        return currentVault
    }

    override fun addNoteToTransaction(txnId: SecureHash, noteText: String) {
        mutex.locked {
            val notes = transactionNotes.getOrPut(key = txnId, defaultValue = {
                setOf(noteText)
            })
            transactionNotes.put(txnId, notes.plus(noteText))
        }
    }

    override fun getTransactionNotes(txnId: SecureHash): Iterable<String> {
        mutex.locked {
            return transactionNotes.get(txnId)!!.asIterable()
        }
    }

    /**
     * Generate a transaction that moves an amount of currency to the given pubkey.
     *
     * @param onlyFromParties if non-null, the asset states will be filtered to only include those issued by the set
     *                        of given parties. This can be useful if the party you're trying to pay has expectations
     *                        about which type of asset claims they are willing to accept.
     */
    override fun generateSpend(tx: TransactionBuilder,
                               amount: Amount<Currency>,
                               to: PublicKey,
                               onlyFromParties: Set<Party>?): Pair<TransactionBuilder, List<PublicKey>> {
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

        val assetsStates = currentVault.statesOfType<Cash.State>()

        val currency = amount.token
        var acceptableCoins = run {
            val ofCurrency = assetsStates.filter { it.state.data.amount.token.product == currency }
            if (onlyFromParties != null)
                ofCurrency.filter { it.state.data.deposit.party in onlyFromParties }
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
            Amount(gatheredAmount.quantity - amount.quantity, takeChangeFrom.state.data.issuanceDef)
        } else {
            null
        }
        val keysUsed = gathered.map { it.state.data.owner }.toSet()

        val states = gathered.groupBy { it.state.data.deposit }.map {
            val coins = it.value
            val totalAmount = coins.map { it.state.data.amount }.sumOrThrow()
            deriveState(coins.first().state, totalAmount, to)
        }

        val outputs = if (change != null) {
            // Just copy a key across as the change key. In real life of course, this works but leaks private data.
            // In bitcoinj we derive a fresh key here and then shuffle the outputs to ensure it's hard to follow
            // value flows through the transaction graph.
            val changeKey = gathered.first().state.data.owner
            // Add a change output and adjust the last output downwards.
            states.subList(0, states.lastIndex) +
                    states.last().let { deriveState(it, it.data.amount - change, it.data.owner) } +
                    deriveState(gathered.last().state, change, changeKey)
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

    private fun deriveState(txState: TransactionState<Cash.State>, amount: Amount<Issued<Currency>>, owner: PublicKey)
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

    private fun makeUpdate(tx: WireTransaction, netDelta: Vault.Update, ourKeys: Set<PublicKey>): Vault.Update {
        val ourNewStates = tx.outputs.
                filter { isRelevant(it.data, ourKeys) }.
                map { tx.outRef<ContractState>(it.data) }

        // Now calculate the states that are being spent by this transaction.
        val consumed = tx.inputs.toHashSet()
        // We use Guava union here as it's lazy for contains() which is how retainAll() is implemented.
        // i.e. retainAll() iterates over consumed, checking contains() on the parameter.  Sets.union() does not physically create
        // a new collection and instead contains() just checks the contains() of both parameters, and so we don't end up
        // iterating over all (a potentially very large) unconsumedStates at any point.
        mutex.locked {
            consumed.retainAll(Sets.union(netDelta.produced, unconsumedStates))
        }

        // Is transaction irrelevant?
        if (consumed.isEmpty() && ourNewStates.isEmpty()) {
            log.trace { "tx ${tx.id} was irrelevant to this vault, ignoring" }
            return Vault.NoUpdate
        }

        return Vault.Update(consumed, ourNewStates.toHashSet())
    }

    private fun isRelevant(state: ContractState, ourKeys: Set<PublicKey>): Boolean {
        return if (state is OwnableState) {
            state.owner in ourKeys
        } else if (state is LinearState) {
            // It's potentially of interest to the vault
            state.isRelevant(ourKeys)
        } else {
            false
        }
    }
}

package com.r3corda.node.services.vault

import com.google.common.collect.Sets
import com.r3corda.core.ThreadBox
import com.r3corda.core.bufferUntilSubscribed
import com.r3corda.core.contracts.*
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.node.services.Vault
import com.r3corda.core.node.services.VaultService
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.core.transactions.WireTransaction
import com.r3corda.core.utilities.loggerFor
import com.r3corda.core.utilities.trace
import com.r3corda.node.utilities.AbstractJDBCHashSet
import com.r3corda.node.utilities.JDBCHashedTable
import com.r3corda.node.utilities.NODE_DATABASE_PREFIX
import com.r3corda.node.utilities.stateRef
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import rx.Observable
import rx.subjects.PublishSubject
import java.security.PublicKey

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

    private val mutex = ThreadBox(object {
        val unconsumedStates = object : AbstractJDBCHashSet<StateRef, StatesSetTable>(StatesSetTable) {
            override fun elementFromRow(row: ResultRow): StateRef = StateRef(row[table.stateRef.txId], row[table.stateRef.index])

            override fun addElementToInsert(insert: InsertStatement, entry: StateRef, finalizables: MutableList<() -> Unit>) {
                insert[table.stateRef.txId] = entry.txhash
                insert[table.stateRef.index] = entry.index
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

    override val currentVault: Vault get() = mutex.locked { Vault(allUnconsumedStates()) }

    override val updates: Observable<Vault.Update>
        get() = mutex.locked { _updatesPublisher }

    override fun track(): Pair<Vault, Observable<Vault.Update>> {
        return mutex.locked {
            Pair(Vault(allUnconsumedStates()), _updatesPublisher.bufferUntilSubscribed())
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

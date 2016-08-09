package com.r3corda.node.services.wallet

import com.r3corda.core.contracts.ContractState
import com.r3corda.core.contracts.StateAndRef
import com.r3corda.core.contracts.StateRef
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.node.services.Wallet
import com.r3corda.core.testing.InMemoryWalletService
import com.r3corda.core.utilities.loggerFor
import com.r3corda.core.utilities.trace
import com.r3corda.node.utilities.databaseTransaction
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SchemaUtils.create

/**
 * Currently, the node wallet service is a very simple RDBMS backed implementation.  It will change significantly when
 * we add further functionality as the design for the wallet and wallet service matures.
 *
 * TODO: move query / filter criteria into the database query.
 * TODO: keep an audit trail with time stamps of previously unconsumed states "as of" a particular point in time.
 * TODO: have transaction storage do some caching.
 */
class NodeWalletService(services: ServiceHub) : InMemoryWalletService(services) {

    override val log = loggerFor<NodeWalletService>()

    // For now we are just tracking the current state, with no historical reporting ability.
    private object UnconsumedStates : Table("vault_unconsumed_states") {
        val txhash = binary("transaction_id", 32).primaryKey()
        val index = integer("output_index").primaryKey()
    }

    init {
        // TODO: at some future point, we'll use some schema creation tool to deploy database artifacts if the database
        //       is not yet initalised to the right version of the schema.
        createTablesIfNecessary()

        // Note that our wallet implementation currently does nothing with respect to attempting to apply criteria in the database.
        mutex.locked { wallet = Wallet(allUnconsumedStates()) }

        // Now we need to make sure we listen to updates
        updates.subscribe { recordUpdate(it) }
    }

    private fun recordUpdate(update: Wallet.Update) {
        val producedStateRefs = update.produced.map { it.ref }
        val consumedStateRefs = update.consumed
        log.trace { "Removing $consumedStateRefs consumed contract states and adding $producedStateRefs produced contract states to the database." }
        databaseTransaction {
            // Note we also remove the produced in case we are re-inserting in some form of recovery situation.
            for (consumed in (consumedStateRefs + producedStateRefs)) {
                UnconsumedStates.deleteWhere {
                    (UnconsumedStates.txhash eq consumed.txhash.bits) and (UnconsumedStates.index eq consumed.index)
                }
            }
            for (produced in producedStateRefs) {
                UnconsumedStates.insert {
                    it[txhash] = produced.txhash.bits
                    it[index] = produced.index
                }
            }
        }
    }

    private fun createTablesIfNecessary() {
        log.trace { "Creating database tables if necessary." }
        databaseTransaction {
            create(UnconsumedStates)
        }
    }

    private fun allUnconsumedStates(): Iterable<StateAndRef<ContractState>> {
        // Order by txhash for if and when transaction storage has some caching.
        // Map to StateRef and then to StateAndRef.
        return databaseTransaction {
            UnconsumedStates.selectAll().orderBy(UnconsumedStates.txhash)
                    .map { StateRef(SecureHash.SHA256(it[UnconsumedStates.txhash]), it[UnconsumedStates.index]) }
                    .map {
                        val storedTx = services.storageService.validatedTransactions.getTransaction(it.txhash) ?: throw Error("Found transaction hash ${it.txhash} in unconsumed contract states that is not in transaction storage.")
                        StateAndRef(storedTx.tx.outputs[it.index], it)
                    }
        }
    }
}
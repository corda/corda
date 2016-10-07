package com.r3corda.node.services.transactions

import com.r3corda.core.ThreadBox
import com.r3corda.core.contracts.StateRef
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.services.UniquenessException
import com.r3corda.core.node.services.UniquenessProvider
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.core.utilities.loggerFor
import com.r3corda.node.utilities.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/** A RDBMS backed Uniqueness provider */
@ThreadSafe
class PersistentUniquenessProvider() : UniquenessProvider, SingletonSerializeAsToken() {
    companion object {
        private val TABLE_NAME = "${NODE_DATABASE_PREFIX}notary_commit_log"
        private val log = loggerFor<PersistentUniquenessProvider>()
    }

    /**
     * For each input state store the consuming transaction information.
     */
    private object Table : JDBCHashedTable(TABLE_NAME) {
        val output = stateRef("transaction_id", "output_index")
        val consumingTxHash = secureHash("consuming_transaction_id")
        val consumingIndex = integer("consuming_input_index")
        val requestingParty = party("requesting_party_name", "requesting_party_key")
    }

    private val committedStates = ThreadBox(object : AbstractJDBCHashMap<StateRef, UniquenessProvider.ConsumingTx, Table>(Table, loadOnInit = false) {
        override fun keyFromRow(row: ResultRow): StateRef = StateRef(row[table.output.txId], row[table.output.index])

        override fun valueFromRow(row: ResultRow): UniquenessProvider.ConsumingTx = UniquenessProvider.ConsumingTx(
                row[table.consumingTxHash],
                row[table.consumingIndex],
                Party(row[table.requestingParty.name], row[table.requestingParty.owningKey])
        )

        override fun addKeyToInsert(insert: InsertStatement,
                                    entry: Map.Entry<StateRef, UniquenessProvider.ConsumingTx>,
                                    finalizables: MutableList<() -> Unit>) {
            insert[table.output.txId] = entry.key.txhash
            insert[table.output.index] = entry.key.index
        }

        override fun addValueToInsert(insert: InsertStatement,
                                      entry: Map.Entry<StateRef, UniquenessProvider.ConsumingTx>,
                                      finalizables: MutableList<() -> Unit>) {
            insert[table.consumingTxHash] = entry.value.id
            insert[table.consumingIndex] = entry.value.inputIndex
            insert[table.requestingParty.name] = entry.value.requestingParty.name
            insert[table.requestingParty.owningKey] = entry.value.requestingParty.owningKey
        }
    })

    override fun commit(states: List<StateRef>, txId: SecureHash, callerIdentity: Party) {
        val conflict = committedStates.locked {
            val conflictingStates = LinkedHashMap<StateRef, UniquenessProvider.ConsumingTx>()
            for (inputState in states) {
                val consumingTx = get(inputState)
                if (consumingTx != null) conflictingStates[inputState] = consumingTx
            }
            if (conflictingStates.isNotEmpty()) {
                log.debug("Failure, input states already committed: ${conflictingStates.keys.toString()}")
                UniquenessProvider.Conflict(conflictingStates)
            } else {
                states.forEachIndexed { i, stateRef ->
                    put(stateRef, UniquenessProvider.ConsumingTx(txId, i, callerIdentity))
                }
                log.debug("Successfully committed all input states: $states")
                null
            }
        }

        if (conflict != null) throw UniquenessException(conflict)
    }
}
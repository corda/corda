package com.r3corda.node.services.transactions

import com.r3corda.core.ThreadBox
import com.r3corda.core.contracts.StateRef
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.services.UniquenessException
import com.r3corda.core.node.services.UniquenessProvider
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.node.utilities.JDBCHashMap
import com.r3corda.node.utilities.databaseTransaction
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/** A a RDBMS backed Uniqueness provider */
@ThreadSafe
class PersistentUniquenessProvider() : UniquenessProvider, SingletonSerializeAsToken() {
    companion object {
        private val TABLE_NAME = "notary_commit_log"
    }

    /**
     * For each input state store the consuming transaction information.
     * TODO: remove databaseTransaction here once node initialisation is wrapped in it
     */
    val committedStates = ThreadBox(databaseTransaction {
        JDBCHashMap<StateRef, UniquenessProvider.ConsumingTx>(TABLE_NAME, loadOnInit = false)
    })

    override fun commit(states: List<StateRef>, txId: SecureHash, callerIdentity: Party) {
        val conflict = committedStates.locked {
            // TODO: remove databaseTransaction here once protocols are wrapped in it
            databaseTransaction {
                val conflictingStates = LinkedHashMap<StateRef, UniquenessProvider.ConsumingTx>()
                for (inputState in states) {
                    val consumingTx = get(inputState)
                    if (consumingTx != null) conflictingStates[inputState] = consumingTx
                }
                if (conflictingStates.isNotEmpty()) {
                    UniquenessProvider.Conflict(conflictingStates)
                } else {
                    states.forEachIndexed { i, stateRef ->
                        put(stateRef, UniquenessProvider.ConsumingTx(txId, i, callerIdentity))
                    }
                    null
                }
            }
        }

        if (conflict != null) throw UniquenessException(conflict)
    }
}
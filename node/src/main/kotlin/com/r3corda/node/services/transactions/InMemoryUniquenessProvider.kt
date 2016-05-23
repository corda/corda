package com.r3corda.node.services.transactions

import com.r3corda.core.ThreadBox
import com.r3corda.core.contracts.StateRef
import com.r3corda.core.contracts.WireTransaction
import com.r3corda.core.crypto.Party
import com.r3corda.core.node.services.UniquenessException
import com.r3corda.core.node.services.UniquenessProvider
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/** A dummy Uniqueness provider that stores the whole history of consumed states in memory */
@ThreadSafe
class InMemoryUniquenessProvider() : UniquenessProvider {
    /** For each input state store the consuming transaction information */
    private val committedStates = ThreadBox(HashMap<StateRef, UniquenessProvider.ConsumingTx>())

    // TODO: the uniqueness provider shouldn't be able to see all tx outputs and commands
    override fun commit(tx: WireTransaction, callerIdentity: Party) {
        val inputStates = tx.inputs
        committedStates.locked {
            val conflictingStates = LinkedHashMap<StateRef, UniquenessProvider.ConsumingTx>()
            for (inputState in inputStates) {
                val consumingTx = get(inputState)
                if (consumingTx != null) conflictingStates[inputState] = consumingTx
            }
            if (conflictingStates.isNotEmpty()) {
                val conflict = UniquenessProvider.Conflict(conflictingStates)
                throw UniquenessException(conflict)
            } else {
                inputStates.forEachIndexed { i, stateRef ->
                    put(stateRef, UniquenessProvider.ConsumingTx(tx.id, i, callerIdentity))
                }
            }

        }
    }
}
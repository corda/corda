package com.r3corda.node.services.transactions

import com.r3corda.core.ThreadBox
import com.r3corda.core.contracts.StateRef
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.services.UniquenessException
import com.r3corda.core.node.services.UniquenessProvider
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/** A dummy Uniqueness provider that stores the whole history of consumed states in memory */
@ThreadSafe
class InMemoryUniquenessProvider() : UniquenessProvider {
    /** For each input state store the consuming transaction information */
    private val committedStates = ThreadBox(HashMap<StateRef, UniquenessProvider.ConsumingTx>())

    override fun commit(states: List<StateRef>, txId: SecureHash, callerIdentity: Party) {
        committedStates.locked {
            val conflictingStates = LinkedHashMap<StateRef, UniquenessProvider.ConsumingTx>()
            for (inputState in states) {
                val consumingTx = get(inputState)
                if (consumingTx != null) conflictingStates[inputState] = consumingTx
            }
            if (conflictingStates.isNotEmpty()) {
                val conflict = UniquenessProvider.Conflict(conflictingStates)
                throw UniquenessException(conflict)
            } else {
                states.forEachIndexed { i, stateRef ->
                    put(stateRef, UniquenessProvider.ConsumingTx(txId, i, callerIdentity))
                }
            }

        }
    }
}
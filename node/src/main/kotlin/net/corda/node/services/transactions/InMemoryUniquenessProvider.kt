package net.corda.node.services.transactions

import net.corda.core.ThreadBox
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.UniquenessException
import net.corda.core.node.services.UniquenessProvider
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
            states.forEachIndexed { i, inputState ->
                val consumingTx = get(inputState)
                // Allow re-commit if consumingTx doesn't change.
                if (consumingTx != null && consumingTx != UniquenessProvider.ConsumingTx(txId, i, callerIdentity))
                    conflictingStates[inputState] = consumingTx
            }
            if (conflictingStates.isNotEmpty()) {
                val conflict = UniquenessProvider.Conflict(conflictingStates)
                throw UniquenessException(conflict)
            } else {
                states.forEachIndexed { i, stateRef ->
                    // TODO: Put is a noop in the re-commit case.
                    put(stateRef, UniquenessProvider.ConsumingTx(txId, i, callerIdentity))
                }
            }

        }
    }
}

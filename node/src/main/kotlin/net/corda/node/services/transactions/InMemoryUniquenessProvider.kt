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
            for (inputState in states) {
                val consumingTx = get(inputState)
                // If one or more inputs have been used as input for a transaction with a different ID we have a conflict.
                // The ID is a SecureHash over the transaction, it can be seen as the "output". Identical states can not
                // be used as input for multiple transactions (e.g. double spending is not allowed).
                // TODO:
                // - Only allow the same callerIdentity to re-commit the inputs?
                // - Only allow re-commit for identical parameters?
                // - Add a time limit to re-committing or restrict the number of attempts to re-commit the input states?
                if (consumingTx != null && consumingTx.id != txId)
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

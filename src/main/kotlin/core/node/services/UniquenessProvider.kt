package core.node.services

import core.Party
import core.StateRef
import core.ThreadBox
import core.WireTransaction
import core.crypto.SecureHash
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/**
 * A service that records input states of the given transaction and provides conflict information
 * if any of the inputs have already been used in another transaction
 */
interface UniquenessProvider {
    /** Commits all input states of the given transaction */
    fun commit(tx: WireTransaction, callerIdentity: Party)

    /** Specifies the consuming transaction for every conflicting state */
    data class Conflict(val stateHistory: Map<StateRef, ConsumingTx>)

    /**
     * Specifies the transaction id, the position of the consumed state in the inputs, and
     * the caller identity requesting the commit
     *
     * TODO: need to do more design work to prevent privacy problems: knowing the id of a
     *       transaction, by the rules of our system the party can obtain it and see its contents.
     *       This allows a party to just submit invalid transactions with outputs it was aware of and
     *       find out where exactly they were spent.
     */
    data class ConsumingTx(val id: SecureHash, val inputIndex: Int, val requestingParty: Party)
}

class UniquenessException(val error: UniquenessProvider.Conflict) : Exception()

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
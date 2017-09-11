package net.corda.core.node.services

import net.corda.core.CordaException
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

/**
 * A service that records input states of the given transaction and provides conflict information
 * if any of the inputs have already been used in another transaction.
 *
 * A uniqueness provider is expected to be used from within the context of a flow.
 */
interface UniquenessProvider {
    /** Commits all input states of the given transaction */
    fun commit(states: List<StateRef>, txId: SecureHash, callerIdentity: Party)

    /** Specifies the consuming transaction for every conflicting state */
    @CordaSerializable
    data class Conflict(val stateHistory: Map<StateRef, ConsumingTx>)

    /**
     * Specifies the transaction id, the position of the consumed state in the inputs, and
     * the caller identity requesting the commit.
     *
     * TODO: need to do more design work to prevent privacy problems: knowing the id of a
     *       transaction, by the rules of our system the party can obtain it and see its contents.
     *       This allows a party to just submit invalid transactions with outputs it was aware of and
     *       find out where exactly they were spent.
     */
    @CordaSerializable
    data class ConsumingTx(val id: SecureHash, val inputIndex: Int, val requestingParty: Party)
}

class UniquenessException(val error: UniquenessProvider.Conflict) : CordaException(UniquenessException::class.java.name)
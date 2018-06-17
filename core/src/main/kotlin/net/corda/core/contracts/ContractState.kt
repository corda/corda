package net.corda.core.contracts

import net.corda.core.KeepForDJVM
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.CordaSerializable

// DOCSTART 1
/**
 * A contract state (or just "state") contains opaque data used by a contract program. It can be thought of as a disk
 * file that the program can use to persist data across transactions. States are immutable: once created they are never
 * updated, instead, any changes must generate a new successor state. States can be updated (consumed) only once: the
 * notary is responsible for ensuring there is no "double spending" by only signing a transaction if the input states
 * are all free.
 */
@KeepForDJVM
@CordaSerializable
interface ContractState {
    /**
     * A _participant_ is any party that is able to consume this state in a valid transaction.
     *
     * The list of participants is required for certain types of transactions. For example, when changing the notary
     * for this state, every participant has to be involved and approve the transaction
     * so that they receive the updated state, and don't end up in a situation where they can no longer use a state
     * they possess, since someone consumed that state during the notary change process.
     *
     * The participants list should normally be derived from the contents of the state.
     */
    val participants: List<AbstractParty>
}
// DOCEND 1

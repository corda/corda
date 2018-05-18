package io.cryptoblk.core

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

inline fun <reified T : ContractState> ServiceHub.queryStateByRef(ref: StateRef): StateAndRef<T> {
    val results = vaultService.queryBy<T>(QueryCriteria.VaultQueryCriteria(stateRefs = kotlin.collections.listOf(ref)))
    return results.states.firstOrNull() ?: throw IllegalArgumentException("State (type=${T::class}) corresponding to the reference $ref not found (or is spent).")
}

/**
 * Shorthand when a single party signs a TX and then returns a result that uses the signed TX (e.g. includes the TX id)
 */
@Suspendable
fun <R> FlowLogic<R>.finalize(tx: TransactionBuilder, returnWithSignedTx: (stx: SignedTransaction) -> R): R {
    val stx = serviceHub.signInitialTransaction(tx)
    subFlow(FinalityFlow(stx)) // it'll send to all participants in the state by default
    return returnWithSignedTx(stx)
}

/**
 * Corda fails when it tries to store the same attachment hash twice. And it's convenient to also do nothing if no attachment is provided.
 * This doesn't fix the same-attachment problem completely but should at least help in testing with the same file.
 */
fun TransactionBuilder.addAttachmentOnce(att: SecureHash?): TransactionBuilder {
    if (att == null) return this
    if (att !in this.attachments())
        this.addAttachment(att)
    return this
}

// checks the instance type, so the cast is safe
@Suppress("UNCHECKED_CAST")
inline fun <reified T : ContractState> List<StateAndRef<ContractState>>.entriesOfType(): List<StateAndRef<T>> = this.mapNotNull {
    if (T::class.java.isInstance(it.state.data)) it as StateAndRef<T> else null
}

/**
 * Used when multiple objects may be created in the same transaction and need to refer to each other. If a state
 * contains this object as a reference to another object and txhash is null, the same txhash as of the containing/outer state
 * should be used. If txhash is not null, then this works exactly like StateRef.
 *
 * WARNING:
 *  - if the outer state gets updated but its referenced state does not (in the same tx) then
 *      - this reference in parent state must be updated with the real txhash: [StateRefHere.copyWith]
 *      - otherwise it will be unresolvable (could be solved by disallowing copy on this)
 */
// do not make it a data class
@CordaSerializable
class StateRefHere(val txhash: SecureHash?, val index: Int) {
    constructor(ref: StateRef) : this(ref.txhash, ref.index)

    fun toStateRef(parent: SecureHash) = StateRef(txhash ?: parent, index)

    // not standard copy
    fun copyWith(parent: SecureHash): StateRefHere {
        return StateRefHere(txhash ?: parent, index)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is StateRefHere) return false
        return (this.txhash == other.txhash) && (this.index == other.index)
    }
}
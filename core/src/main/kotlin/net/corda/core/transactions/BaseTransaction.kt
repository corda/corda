package net.corda.core.transactions

import net.corda.core.DoNotImplement
import net.corda.core.KeepForDJVM
import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.internal.castIfPossible
import net.corda.core.internal.indexOfOrThrow
import net.corda.core.internal.uncheckedCast
import java.util.function.Predicate

/**
 * An abstract class defining fields shared by all transaction types in the system.
 */
@KeepForDJVM
@DoNotImplement
abstract class BaseTransaction : NamedByHash {
    /** A list of reusable reference data states which can be referred to by other contracts in this transaction. */
    abstract val references: List<*>
    /** The inputs of this transaction. Note that in BaseTransaction subclasses the type of this list may change! */
    abstract val inputs: List<*>
    /** Ordered list of states defined by this transaction, along with the associated notaries. */
    abstract val outputs: List<TransactionState<ContractState>>
    /**
     * If present, the notary for this transaction. If absent then the transaction is not notarised at all.
     * This is intended for issuance/genesis transactions that don't consume any other states and thus can't
     * double spend anything.
     */
    abstract val notary: Party?

    protected open fun checkBaseInvariants() {
        checkNotarySetIfInputsPresent()
        checkNoDuplicateInputs()
        checkForInputsAndReferencesOverlap()
    }

    private fun checkNotarySetIfInputsPresent() {
        if (inputs.isNotEmpty() || references.isNotEmpty()) {
            check(notary != null) { "The notary must be specified explicitly for any transaction that has inputs" }
        }
    }

    private fun checkNoDuplicateInputs() {
        check(inputs.size == inputs.toSet().size) { "Duplicate input states detected" }
        check(references.size == references.toSet().size) { "Duplicate reference states detected" }
    }

    private fun checkForInputsAndReferencesOverlap() {
        val intersection = inputs intersect references
        require(intersection.isEmpty()) {
            "A StateRef cannot be both an input and a reference input in the same transaction. Offending " +
                    "StateRefs: $intersection"
        }
    }

    /**
     * Returns a [StateAndRef] for the given output index.
     */
    fun <T : ContractState> outRef(index: Int): StateAndRef<T> = StateAndRef(uncheckedCast(outputs[index]), StateRef(id, index))

    /**
     * Returns a [StateAndRef] for the requested output state, or throws [IllegalArgumentException] if not found.
     */
    fun <T : ContractState> outRef(state: ContractState): StateAndRef<T> = outRef(outputStates.indexOfOrThrow(state))

    /**
     * Helper property to return a list of [ContractState] objects, rather than the often less convenient [TransactionState]
     */
    val outputStates: List<ContractState> get() = outputs.map { it.data }

    /**
     * Helper to simplify getting an indexed output.
     * @param index the position of the item in the output.
     * @return The ContractState at the requested index
     */
    fun getOutput(index: Int): ContractState = outputs[index].data

    /**
     * Helper to simplify getting all output states of a particular class, interface, or base class.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * Clazz must be an extension of [ContractState].
     * @return the possibly empty list of output states matching the clazz restriction.
     */
    fun <T : ContractState> outputsOfType(clazz: Class<T>): List<T> = outputs.mapNotNull { clazz.castIfPossible(it.data) }

    inline fun <reified T : ContractState> outputsOfType(): List<T> = outputsOfType(T::class.java)

    /**
     * Helper to simplify filtering outputs according to a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * Clazz must be an extension of [ContractState].
     * @param predicate A filtering function taking a state of type T and returning true if it should be included in the list.
     * The class filtering is applied before the predicate.
     * @return the possibly empty list of output states matching the predicate and clazz restrictions.
     */
    fun <T : ContractState> filterOutputs(clazz: Class<T>, predicate: Predicate<T>): List<T> {
        return outputsOfType(clazz).filter { predicate.test(it) }
    }

    inline fun <reified T : ContractState> filterOutputs(crossinline predicate: (T) -> Boolean): List<T> {
        return filterOutputs(T::class.java, Predicate { predicate(it) })
    }

    /**
     * Helper to simplify finding a single output matching a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * Clazz must be an extension of [ContractState].
     * @param predicate A filtering function taking a state of type T and returning true if this is the desired item.
     * The class filtering is applied before the predicate.
     * @return the single item matching the predicate.
     * @throws IllegalArgumentException if no item, or multiple items are found matching the requirements.
     */
    fun <T : ContractState> findOutput(clazz: Class<T>, predicate: Predicate<T>): T {
        return outputsOfType(clazz).single { predicate.test(it) }
    }

    inline fun <reified T : ContractState> findOutput(crossinline predicate: (T) -> Boolean): T {
        return findOutput(T::class.java, Predicate { predicate(it) })
    }

    /**
     * Helper to simplify getting all output [StateAndRef] items of a particular state class, interface, or base class.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * Clazz must be an extension of [ContractState].
     * @return the possibly empty list of output [StateAndRef<T>] states matching the clazz restriction.
     */
    fun <T : ContractState> outRefsOfType(clazz: Class<T>): List<StateAndRef<T>> {
        return outputs.mapIndexedNotNull { index, state ->
            clazz.castIfPossible(state.data)?.let { StateAndRef<T>(uncheckedCast(state), StateRef(id, index)) }
        }
    }

    inline fun <reified T : ContractState> outRefsOfType(): List<StateAndRef<T>> = outRefsOfType(T::class.java)

    /**
     * Helper to simplify filtering output [StateAndRef] items according to a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * Clazz must be an extension of [ContractState].
     * @param predicate A filtering function taking a state of type T and returning true if it should be included in the list.
     * The class filtering is applied before the predicate.
     * @return the possibly empty list of output [StateAndRef] states matching the predicate and clazz restrictions.
     */
    fun <T : ContractState> filterOutRefs(clazz: Class<T>, predicate: Predicate<T>): List<StateAndRef<T>> {
        return outRefsOfType(clazz).filter { predicate.test(it.state.data) }
    }

    inline fun <reified T : ContractState> filterOutRefs(crossinline predicate: (T) -> Boolean): List<StateAndRef<T>> {
        return filterOutRefs(T::class.java, Predicate { predicate(it) })
    }

    /**
     * Helper to simplify finding a single output [StateAndRef] matching a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * Clazz must be an extension of [ContractState].
     * @param predicate A filtering function taking a state of type T and returning true if this is the desired item.
     * The class filtering is applied before the predicate.
     * @return the single [StateAndRef] item matching the predicate.
     * @throws IllegalArgumentException if no item, or multiple items are found matching the requirements.
     */
    fun <T : ContractState> findOutRef(clazz: Class<T>, predicate: Predicate<T>): StateAndRef<T> {
        return outRefsOfType(clazz).single { predicate.test(it.state.data) }
    }

    inline fun <reified T : ContractState> findOutRef(crossinline predicate: (T) -> Boolean): StateAndRef<T> {
        return findOutRef(T::class.java, Predicate { predicate(it) })
    }

    override fun toString(): String = "${javaClass.simpleName}(id=$id)"
}
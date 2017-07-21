package net.corda.core.transactions

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey
import java.util.*
import java.util.function.Predicate

/**
 * A LedgerTransaction is derived from a [WireTransaction]. It is the result of doing the following operations:
 *
 * - Downloading and locally storing all the dependencies of the transaction.
 * - Resolving the input states and loading them into memory.
 * - Doing some basic key lookups on the [Command]s to see if any keys are from a recognised party, thus converting the
 *   [Command] objects into [AuthenticatedObject].
 * - Deserialising the output states.
 *
 * All the above refer to inputs using a (txhash, output index) pair.
 */
// TODO LedgerTransaction is not supposed to be serialisable as it references attachments, etc. The verification logic
// currently sends this across to out-of-process verifiers. We'll need to change that first.
// DOCSTART 1
@CordaSerializable
class LedgerTransaction(
        /** The resolved input states which will be consumed/invalidated by the execution of this transaction. */
        override val inputs: List<StateAndRef<*>>,
        outputs: List<TransactionState<ContractState>>,
        /** Arbitrary data passed to the program of each input state. */
        val commands: List<AuthenticatedObject<CommandData>>,
        /** A list of [Attachment] objects identified by the transaction that are needed for this transaction to verify. */
        val attachments: List<Attachment>,
        /** The hash of the original serialised WireTransaction. */
        override val id: SecureHash,
        notary: Party?,
        signers: List<PublicKey>,
        timeWindow: TimeWindow?,
        type: TransactionType
) : BaseTransaction(inputs, outputs, notary, signers, type, timeWindow) {
    //DOCEND 1
    init {
        checkInvariants()
    }

    val inputStates: List<ContractState> get() = inputs.map { it.state.data }

    /**
     * Returns the typed input StateAndRef at the specified index
     * @param index The index into the inputs.
     * @return The [StateAndRef]
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : ContractState> inRef(index: Int) = inputs[index] as StateAndRef<T>

    /**
     * Verifies this transaction and throws an exception if not valid, depending on the type. For general transactions:
     *
     * - The contracts are run with the transaction as the input.
     * - The list of keys mentioned in commands is compared against the signers list.
     *
     * @throws TransactionVerificationException if anything goes wrong.
     */
    @Throws(TransactionVerificationException::class)
    fun verify() = type.verify(this)

    // TODO: When we upgrade to Kotlin 1.1 we can make this a data class again and have the compiler generate these.

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        if (!super.equals(other)) return false

        other as LedgerTransaction

        if (inputs != other.inputs) return false
        if (outputs != other.outputs) return false
        if (commands != other.commands) return false
        if (attachments != other.attachments) return false
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + inputs.hashCode()
        result = 31 * result + outputs.hashCode()
        result = 31 * result + commands.hashCode()
        result = 31 * result + attachments.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }

    /**
     * Given a type and a function that returns a grouping key, associates inputs and outputs together so that they
     * can be processed as one. The grouping key is any arbitrary object that can act as a map key (so must implement
     * equals and hashCode).
     *
     * The purpose of this function is to simplify the writing of verification logic for transactions that may contain
     * similar but unrelated state evolutions which need to be checked independently. Consider a transaction that
     * simultaneously moves both dollars and euros (e.g. is an atomic FX trade). There may be multiple dollar inputs and
     * multiple dollar outputs, depending on things like how fragmented the owner's vault is and whether various privacy
     * techniques are in use. The quantity of dollars on the output side must sum to the same as on the input side, to
     * ensure no money is being lost track of. This summation and checking must be repeated independently for each
     * currency. To solve this, you would use groupStates with a type of Cash.State and a selector that returns the
     * currency field: the resulting list can then be iterated over to perform the per-currency calculation.
     */
    // DOCSTART 2
    fun <T : ContractState, K : Any> groupStates(ofType: Class<T>, selector: (T) -> K): List<InOutGroup<T, K>> {
        val inputs = inputsOfType(ofType)
        val outputs = outputsOfType(ofType)

        val inGroups: Map<K, List<T>> = inputs.groupBy(selector)
        val outGroups: Map<K, List<T>> = outputs.groupBy(selector)

        val result = ArrayList<InOutGroup<T, K>>()

        for ((k, v) in inGroups.entries)
            result.add(InOutGroup(v, outGroups[k] ?: emptyList(), k))
        for ((k, v) in outGroups.entries) {
            if (inGroups[k] == null)
                result.add(InOutGroup(emptyList(), v, k))
        }

        return result
    }
    // DOCEND 2

    /** See the documentation for the reflection-based version of [groupStates] */
    inline fun <reified T : ContractState, K : Any> groupStates(noinline selector: (T) -> K): List<InOutGroup<T, K>> {
        return groupStates(T::class.java, selector)
    }

    /** Utilities for contract writers to incorporate into their logic. */

    /**
     * A set of related inputs and outputs that are connected by some common attributes. An InOutGroup is calculated
     * using [groupStates] and is useful for handling cases where a transaction may contain similar but unrelated
     * state evolutions, for example, a transaction that moves cash in two different currencies. The numbers must add
     * up on both sides of the transaction, but the values must be summed independently per currency. Grouping can
     * be used to simplify this logic.
     */
    // DOCSTART 3
    data class InOutGroup<out T : ContractState, out K : Any>(val inputs: List<T>, val outputs: List<T>, val groupingKey: K)
    // DOCEND 3

    /**
     * Helper to simplify getting an indexed input [ContractState].
     * @param index the position of the item in the inputs.
     * @return The [StateAndRef] at the requested index
     */
    fun getInput(index: Int): ContractState = inputs[index].state.data

    /**
     * Helper to simplify getting all inputs states of a particular class, interface, or base class.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [ContractState].
     * @return the possibly empty list of inputs matching the clazz restriction.
     */
    fun <T : ContractState> inputsOfType(clazz: Class<T>): List<T> {
        @Suppress("UNCHECKED_CAST")
        return inputs.map { it.state.data }.filterIsInstance(clazz)
    }

    /**
     * Helper to simplify getting all inputs states of a particular class, interface, or base class.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [ContractState].
     * @return the possibly empty list of inputs [StateAndRef] matching the clazz restriction.
     */
    fun <T : ContractState> inRefsOfType(clazz: Class<T>): List<StateAndRef<T>> {
        @Suppress("UNCHECKED_CAST")
        return inputs.filter { clazz.isInstance(it.state.data) }.map { it as StateAndRef<T> }
    }

    /**
     * Helper to simplify filtering inputs according to a [Predicate].
     * @param predicate A filtering function taking a state of type T and returning true if it should be included in the list.
     * The class filtering is applied before the predicate.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [ContractState].
     * @return the possibly empty list of input states matching the predicate and clazz restrictions.
     */
    fun <T : ContractState> filterInputs(predicate: Predicate<T>, clazz: Class<T>): List<T> = inputsOfType(clazz).filter { predicate.test(it) }

    /**
     * Helper to simplify filtering inputs according to a [Predicate].
     * @param predicate A filtering function taking a state of type T and returning true if it should be included in the list.
     * The class filtering is applied before the predicate.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [ContractState].
     * @return the possibly empty list of inputs [StateAndRef] matching the predicate and clazz restrictions.
     */
    fun <T : ContractState> filterInRefs(predicate: Predicate<T>, clazz: Class<T>): List<StateAndRef<T>> = inRefsOfType(clazz).filter { predicate.test(it.state.data) }

    /**
     * Helper to simplify finding a single input [ContractState] matching a [Predicate].
     * @param predicate A filtering function taking a state of type T and returning true if this is the desired item.
     * The class filtering is applied before the predicate.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of ContractState.
     * @return the single item matching the predicate.
     * @throws IllegalArgumentException if no item, or multiple items are found matching the requirements.
     */
    fun <T : ContractState> findInput(predicate: Predicate<T>, clazz: Class<T>): T = filterInputs(predicate, clazz).single()

    /**
     * Helper to simplify finding a single input matching a [Predicate].
     * @param predicate A filtering function taking a state of type T and returning true if this is the desired item.
     * The class filtering is applied before the predicate.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of ContractState.
     * @return the single item matching the predicate.
     * @throws IllegalArgumentException if no item, or multiple items are found matching the requirements.
     */
    fun <T : ContractState> findInRef(predicate: Predicate<T>, clazz: Class<T>): StateAndRef<T> = filterInRefs(predicate, clazz).single()

    /**
     * Helper to simplify getting an indexed command.
     * @param index the position of the item in the commands.
     * @return The Command at the requested index
     */
    fun getCommand(index: Int): Command = Command(commands[index].value, commands[index].signers)

    /**
     * Helper to simplify getting all [Command] items with a [CommandData] of a particular class, interface, or base class.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [CommandData].
     * @return the possibly empty list of commands with [CommandData] values matching the clazz restriction.
     */
    fun <T : CommandData> commandsOfType(clazz: Class<T>): List<Command> {
        return commands.filter { clazz.isInstance(it.value) }.map { Command(it.value, it.signers) }
    }

    /**
     * Helper to simplify filtering [Command] items according to a [Predicate].
     * @param predicate A filtering function taking a [CommandData] item of type T and returning true if it should be included in the list.
     * The class filtering is applied before the predicate.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [CommandData].
     * @return the possibly empty list of [Command] items with [CommandData] values matching the predicate and clazz restrictions.
     */
    fun <T : CommandData> filterCommands(predicate: Predicate<T>, clazz: Class<T>): List<Command> {
        @Suppress("UNCHECKED_CAST")
        return commandsOfType(clazz).filter { predicate.test(it.value as T) }
    }


    /**
     * Helper to simplify finding a single [Command] items according to a [Predicate].
     * @param predicate A filtering function taking a [CommandData] item of type T and returning true if it should be included in the list.
     * The class filtering is applied before the predicate.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [CommandData].
     * @return the [Command] item with [CommandData] values matching the predicate and clazz restrictions.
     * @throws IllegalArgumentException if no items, or multiple items matched the requirements.
     */
    fun <T : CommandData> findCommand(predicate: Predicate<T>, clazz: Class<T>): Command {
        return filterCommands(predicate, clazz).single()
    }

    /**
     * Helper to simplify getting an indexed attachment.
     * @param index the position of the item in the attachments.
     * @return The Attachment at the requested index.
     */
    fun getAttachment(index: Int): Attachment = attachments[index]

    /**
     * Helper to simplify getting an indexed attachment.
     * @param id the SecureHash of the desired attachment.
     * @return The Attachment with the matching id.
     * @throws IllegalArgumentException if no item matches the id.
     */
    fun getAttachment(id: SecureHash): Attachment = attachments.single { it.id == id }

    //Kotlin extension methods to take advantage of Kotlin's smart type inference when querying the LedgerTransaction
    inline fun <reified T : ContractState> inputsOfType(): List<T> = this.inputsOfType(T::class.java)

    inline fun <reified T : ContractState> inRefsOfType(): List<StateAndRef<T>> = this.inRefsOfType(T::class.java)

    inline fun <reified T : ContractState> filterInputs(crossinline predicate: (T) -> Boolean): List<T> {
        return filterInputs(Predicate { predicate(it) }, T::class.java)
    }

    inline fun <reified T : ContractState> filterInRefs(crossinline predicate: (T) -> Boolean): List<StateAndRef<T>> {
        return filterInRefs(Predicate { predicate(it) }, T::class.java)
    }

    inline fun <reified T : ContractState> findInRef(crossinline predicate: (T) -> Boolean): StateAndRef<T> {
        return findInRef(Predicate { predicate(it) }, T::class.java)
    }

    inline fun <reified T : ContractState> findInput(crossinline predicate: (T) -> Boolean): T {
        return findInput(Predicate { predicate(it) }, T::class.java)
    }

    inline fun <reified T : CommandData> commandsOfType(): List<Command> = this.commandsOfType(T::class.java)

    inline fun <reified T : CommandData> filterCommands(crossinline predicate: (T) -> Boolean): List<Command> {
        return filterCommands(Predicate { predicate(it) }, T::class.java)
    }

    inline fun <reified T : CommandData> findCommand(crossinline predicate: (T) -> Boolean): Command {
        return findCommand(Predicate { predicate(it) }, T::class.java)
    }
}


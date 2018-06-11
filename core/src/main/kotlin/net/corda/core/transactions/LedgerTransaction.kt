package net.corda.core.transactions

import net.corda.core.KeepForDJVM
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.internal.AttachmentWithContext
import net.corda.core.internal.castIfPossible
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.Try
import java.util.*
import java.util.function.Predicate

/**
 * A LedgerTransaction is derived from a [WireTransaction]. It is the result of doing the following operations:
 *
 * - Downloading and locally storing all the dependencies of the transaction.
 * - Resolving the input states and loading them into memory.
 * - Doing some basic key lookups on the [Command]s to see if any keys are from a recognised party, thus converting the
 *   [Command] objects into [CommandWithParties].
 * - Deserialising the output states.
 *
 * All the above refer to inputs using a (txhash, output index) pair.
 */
// TODO LedgerTransaction is not supposed to be serialisable as it references attachments, etc. The verification logic
// currently sends this across to out-of-process verifiers. We'll need to change that first.
// DOCSTART 1
@KeepForDJVM
@CordaSerializable
data class LedgerTransaction @JvmOverloads constructor(
        /** The resolved input states which will be consumed/invalidated by the execution of this transaction. */
        override val inputs: List<StateAndRef<ContractState>>,
        override val outputs: List<TransactionState<ContractState>>,
        /** Arbitrary data passed to the program of each input state. */
        val commands: List<CommandWithParties<CommandData>>,
        /** A list of [Attachment] objects identified by the transaction that are needed for this transaction to verify. */
        val attachments: List<Attachment>,
        /** The hash of the original serialised WireTransaction. */
        override val id: SecureHash,
        override val notary: Party?,
        val timeWindow: TimeWindow?,
        val privacySalt: PrivacySalt,
        private val networkParameters: NetworkParameters? = null
) : FullTransaction() {
    //DOCEND 1
    init {
        checkBaseInvariants()
        if (timeWindow != null) check(notary != null) { "Transactions with time-windows must be notarised" }
        checkNoNotaryChange()
        checkEncumbrancesValid()
    }

    private companion object {
        private fun contractClassFor(className: ContractClassName, classLoader: ClassLoader?): Try<Class<out Contract>> {
            return Try.on {
                (classLoader ?: this::class.java.classLoader)
                        .loadClass(className)
                        .asSubclass(Contract::class.java)
            }
        }

        private fun stateToContractClass(state: TransactionState<ContractState>): Try<Class<out Contract>> {
            return contractClassFor(state.contract, state.data::class.java.classLoader)
        }
    }

    private val contracts: Map<ContractClassName, Try<Class<out Contract>>> = (inputs.map { it.state } + outputs)
            .map { it.contract to stateToContractClass(it) }.toMap()

    val inputStates: List<ContractState> get() = inputs.map { it.state.data }

    /**
     * Returns the typed input StateAndRef at the specified index
     * @param index The index into the inputs.
     * @return The [StateAndRef]
     */
    fun <T : ContractState> inRef(index: Int): StateAndRef<T> = uncheckedCast(inputs[index])

    /**
     * Verifies this transaction and runs contract code. At this stage it is assumed that signatures have already been verified.
     *
     * @throws TransactionVerificationException if anything goes wrong.
     */
    @Throws(TransactionVerificationException::class)
    fun verify() {
        verifyConstraints()
        verifyContracts()
    }

    /**
     * Verify that all contract constraints are valid for each state before running any contract code
     *
     * In case the transaction was created on this node then the attachments will contain the hash of the current cordapp jars.
     * In case this verifies an older transaction or one originated on a different node, then this verifies that the attachments
     * are valid.
     *
     * @throws TransactionVerificationException if the constraints fail to verify
     */
    private fun verifyConstraints() {
        val contractAttachments = attachments.filterIsInstance<ContractAttachment>()
        (inputs.map { it.state } + outputs).forEach { state ->
            val stateAttachments = contractAttachments.filter { state.contract in it.allContracts }
            if (stateAttachments.isEmpty()) throw TransactionVerificationException.MissingAttachmentRejection(id, state.contract)

            val uniqueAttachmentsForStateContract = stateAttachments.distinctBy { it.id }

            // In case multiple attachments have been added for the same contract, fail because this transaction will not be able to be verified
            // because it will break the no-overlap rule that we have implemented in our Classloaders
            if (uniqueAttachmentsForStateContract.size > 1) {
                throw TransactionVerificationException.ConflictingAttachmentsRejection(id, state.contract)
            }

            val contractAttachment = uniqueAttachmentsForStateContract.first()
            val constraintAttachment = AttachmentWithContext(contractAttachment, state.contract, networkParameters?.whitelistedContractImplementations)
            if (!state.constraint.isSatisfiedBy(constraintAttachment)) {
                throw TransactionVerificationException.ContractConstraintRejection(id, state.contract)
            }
        }
    }

    /**
     * Check the transaction is contract-valid by running the verify() for each input and output state contract.
     * If any contract fails to verify, the whole transaction is considered to be invalid.
     */
    private fun verifyContracts() {
        val contractInstances = ArrayList<Contract>(contracts.size)
        for ((key, result) in contracts) {
            when (result) {
                is Try.Failure -> throw TransactionVerificationException.ContractCreationError(id, key, result.exception)
                is Try.Success -> {
                    try {
                        contractInstances.add(result.value.newInstance())
                    } catch (e: Throwable) {
                        throw TransactionVerificationException.ContractCreationError(id, result.value.name, e)
                    }
                }
            }
        }
        contractInstances.forEach { contract ->
            try {
                contract.verify(this)
            } catch (e: Throwable) {
                throw TransactionVerificationException.ContractRejection(id, contract, e)
            }
        }
    }

    /**
     * Make sure the notary has stayed the same. As we can't tell how inputs and outputs connect, if there
     * are any inputs, all outputs must have the same notary.
     *
     * TODO: Is that the correct set of restrictions? May need to come back to this, see if we can be more
     *       flexible on output notaries.
     */
    private fun checkNoNotaryChange() {
        if (notary != null && inputs.isNotEmpty()) {
            outputs.forEach {
                if (it.notary != notary) {
                    throw TransactionVerificationException.NotaryChangeInWrongTransactionType(id, notary, it.notary)
                }
            }
        }
    }

    private fun checkEncumbrancesValid() {
        // Validate that all encumbrances exist within the set of input states.
        val encumberedInputs = inputs.filter { it.state.encumbrance != null }
        encumberedInputs.forEach { (state, ref) ->
            val encumbranceStateExists = inputs.any {
                it.ref.txhash == ref.txhash && it.ref.index == state.encumbrance
            }
            if (!encumbranceStateExists) {
                throw TransactionVerificationException.TransactionMissingEncumbranceException(
                        id,
                        state.encumbrance!!,
                        TransactionVerificationException.Direction.INPUT
                )
            }
        }

        // Check that, in the outputs, an encumbered state does not refer to itself as the encumbrance,
        // and that the number of outputs can contain the encumbrance.
        for ((i, output) in outputs.withIndex()) {
            val encumbranceIndex = output.encumbrance ?: continue
            if (encumbranceIndex == i || encumbranceIndex >= outputs.size) {
                throw TransactionVerificationException.TransactionMissingEncumbranceException(
                        id,
                        encumbranceIndex,
                        TransactionVerificationException.Direction.OUTPUT)
            }
        }
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
    @KeepForDJVM
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
    fun <T : ContractState> inputsOfType(clazz: Class<T>): List<T> = inputs.mapNotNull { clazz.castIfPossible(it.state.data) }

    inline fun <reified T : ContractState> inputsOfType(): List<T> = inputsOfType(T::class.java)

    /**
     * Helper to simplify getting all inputs states of a particular class, interface, or base class.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [ContractState].
     * @return the possibly empty list of inputs [StateAndRef] matching the clazz restriction.
     */
    fun <T : ContractState> inRefsOfType(clazz: Class<T>): List<StateAndRef<T>> {
        return inputs.mapNotNull { if (clazz.isInstance(it.state.data)) uncheckedCast<StateAndRef<ContractState>, StateAndRef<T>>(it) else null }
    }

    inline fun <reified T : ContractState> inRefsOfType(): List<StateAndRef<T>> = inRefsOfType(T::class.java)

    /**
     * Helper to simplify filtering inputs according to a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [ContractState].
     * @param predicate A filtering function taking a state of type T and returning true if it should be included in the list.
     * The class filtering is applied before the predicate.
     * @return the possibly empty list of input states matching the predicate and clazz restrictions.
     */
    fun <T : ContractState> filterInputs(clazz: Class<T>, predicate: Predicate<T>): List<T> {
        return inputsOfType(clazz).filter { predicate.test(it) }
    }

    inline fun <reified T : ContractState> filterInputs(crossinline predicate: (T) -> Boolean): List<T> {
        return filterInputs(T::class.java, Predicate { predicate(it) })
    }

    /**
     * Helper to simplify filtering inputs according to a [Predicate].
     * @param predicate A filtering function taking a state of type T and returning true if it should be included in the list.
     * The class filtering is applied before the predicate.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [ContractState].
     * @return the possibly empty list of inputs [StateAndRef] matching the predicate and clazz restrictions.
     */
    fun <T : ContractState> filterInRefs(clazz: Class<T>, predicate: Predicate<T>): List<StateAndRef<T>> {
        return inRefsOfType(clazz).filter { predicate.test(it.state.data) }
    }

    inline fun <reified T : ContractState> filterInRefs(crossinline predicate: (T) -> Boolean): List<StateAndRef<T>> {
        return filterInRefs(T::class.java, Predicate { predicate(it) })
    }

    /**
     * Helper to simplify finding a single input [ContractState] matching a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of ContractState.
     * @param predicate A filtering function taking a state of type T and returning true if this is the desired item.
     * The class filtering is applied before the predicate.
     * @return the single item matching the predicate.
     * @throws IllegalArgumentException if no item, or multiple items are found matching the requirements.
     */
    fun <T : ContractState> findInput(clazz: Class<T>, predicate: Predicate<T>): T {
        return inputsOfType(clazz).single { predicate.test(it) }
    }

    inline fun <reified T : ContractState> findInput(crossinline predicate: (T) -> Boolean): T {
        return findInput(T::class.java, Predicate { predicate(it) })
    }

    /**
     * Helper to simplify finding a single input matching a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of ContractState.
     * @param predicate A filtering function taking a state of type T and returning true if this is the desired item.
     * The class filtering is applied before the predicate.
     * @return the single item matching the predicate.
     * @throws IllegalArgumentException if no item, or multiple items are found matching the requirements.
     */
    fun <T : ContractState> findInRef(clazz: Class<T>, predicate: Predicate<T>): StateAndRef<T> {
        return inRefsOfType(clazz).single { predicate.test(it.state.data) }
    }

    inline fun <reified T : ContractState> findInRef(crossinline predicate: (T) -> Boolean): StateAndRef<T> {
        return findInRef(T::class.java, Predicate { predicate(it) })
    }

    /**
     * Helper to simplify getting an indexed command.
     * @param index the position of the item in the commands.
     * @return The Command at the requested index
     */
    fun <T : CommandData> getCommand(index: Int): Command<T> = Command(uncheckedCast(commands[index].value), commands[index].signers)

    /**
     * Helper to simplify getting all [Command] items with a [CommandData] of a particular class, interface, or base class.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [CommandData].
     * @return the possibly empty list of commands with [CommandData] values matching the clazz restriction.
     */
    fun <T : CommandData> commandsOfType(clazz: Class<T>): List<Command<T>> {
        return commands.mapNotNull { (signers, _, value) -> clazz.castIfPossible(value)?.let { Command(it, signers) } }
    }

    inline fun <reified T : CommandData> commandsOfType(): List<Command<T>> = commandsOfType(T::class.java)

    /**
     * Helper to simplify filtering [Command] items according to a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [CommandData].
     * @param predicate A filtering function taking a [CommandData] item of type T and returning true if it should be included in the list.
     * The class filtering is applied before the predicate.
     * @return the possibly empty list of [Command] items with [CommandData] values matching the predicate and clazz restrictions.
     */
    fun <T : CommandData> filterCommands(clazz: Class<T>, predicate: Predicate<T>): List<Command<T>> {
        return commandsOfType(clazz).filter { predicate.test(it.value) }
    }

    inline fun <reified T : CommandData> filterCommands(crossinline predicate: (T) -> Boolean): List<Command<T>> {
        return filterCommands(T::class.java, Predicate { predicate(it) })
    }

    /**
     * Helper to simplify finding a single [Command] items according to a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [CommandData].
     * @param predicate A filtering function taking a [CommandData] item of type T and returning true if it should be included in the list.
     * The class filtering is applied before the predicate.
     * @return the [Command] item with [CommandData] values matching the predicate and clazz restrictions.
     * @throws IllegalArgumentException if no items, or multiple items matched the requirements.
     */
    fun <T : CommandData> findCommand(clazz: Class<T>, predicate: Predicate<T>): Command<T> {
        return commandsOfType(clazz).single { predicate.test(it.value) }
    }

    inline fun <reified T : CommandData> findCommand(crossinline predicate: (T) -> Boolean): Command<T> {
        return findCommand(T::class.java, Predicate { predicate(it) })
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
    fun getAttachment(id: SecureHash): Attachment = attachments.first { it.id == id }

    fun copy(inputs: List<StateAndRef<ContractState>>,
             outputs: List<TransactionState<ContractState>>,
             commands: List<CommandWithParties<CommandData>>,
             attachments: List<Attachment>,
             id: SecureHash,
             notary: Party?,
             timeWindow: TimeWindow?,
             privacySalt: PrivacySalt
    ) = copy(inputs = inputs, outputs = outputs, commands = commands, attachments = attachments, id = id, notary = notary, timeWindow = timeWindow, privacySalt = privacySalt, networkParameters = null)
}


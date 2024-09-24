package net.corda.core.transactions

import net.corda.core.CordaInternal
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.Command
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.ComponentGroupEnum
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.PrivacySalt
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.DigestService
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.internal.AbstractVerifier
import net.corda.core.internal.SerializedStateAndRef
import net.corda.core.internal.Verifier
import net.corda.core.internal.castIfPossible
import net.corda.core.internal.deserialiseCommands
import net.corda.core.internal.deserialiseComponentGroup
import net.corda.core.internal.eagerDeserialise
import net.corda.core.internal.isUploaderTrusted
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.DeprecatedConstructorForDeserialization
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.internal.AttachmentsClassLoaderCache
import net.corda.core.serialization.internal.AttachmentsClassLoaderBuilder
import net.corda.core.utilities.contextLogger
import java.util.Collections.unmodifiableList
import java.util.function.Predicate
import java.util.function.Supplier

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
 *
 * Usage notes:
 *
 * [LedgerTransaction] is an abstraction that is meant to be used during the transaction verification stage.
 * It needs full access to input states that might be in transactions that are encrypted and unavailable for code running outside the secure enclave.
 * Also, it might need to deserialize states with code that might not be available on the classpath.
 *
 * Because of this, trying to create or use a [LedgerTransaction] for any other purpose then transaction verification can result in unexpected exceptions,
 * which need de be handled.
 *
 * [LedgerTransaction]s should never be instantiated directly from client code, but rather via WireTransaction.toLedgerTransaction
 */
@Suppress("LongParameterList")
class LedgerTransaction
private constructor(
        // DOCSTART 1
        /** The resolved input states which will be consumed/invalidated by the execution of this transaction. */
        override val inputs: List<StateAndRef<ContractState>>,
        /** The outputs created by the transaction. */
        override val outputs: List<TransactionState<ContractState>>,
        /** Arbitrary data passed to the program of each input state. */
        val commands: List<CommandWithParties<CommandData>>,
        /** A list of [Attachment] objects identified by the transaction that are needed for this transaction to verify. */
        val attachments: List<Attachment>,
        /** The hash of the original serialised WireTransaction. */
        override val id: SecureHash,
        /** The notary that the tx uses, this must be the same as the notary of all the inputs, or null if there are no inputs. */
        override val notary: Party?,
        /** The time window within which the tx is valid, will be checked against notary pool member clocks. */
        val timeWindow: TimeWindow?,
        /** Random data used to make the transaction hash unpredictable even if the contents can be predicted; needed to avoid some obscure attacks. */
        val privacySalt: PrivacySalt,
        /**
         * Network parameters that were in force when the transaction was constructed. This is nullable only for backwards
         * compatibility for serialized transactions. In reality this field will always be set when on the normal codepaths.
         */
        override val networkParameters: NetworkParameters?,
        /** Referenced states, which are like inputs but won't be consumed. */
        override val references: List<StateAndRef<ContractState>>,
        //DOCEND 1

        private val componentGroups: List<ComponentGroup>?,
        private val serializedInputs: List<SerializedStateAndRef>?,
        private val serializedReferences: List<SerializedStateAndRef>?,
        private val isAttachmentTrusted: (Attachment) -> Boolean,
        private val verifierFactory: (LedgerTransaction, SerializationContext) -> Verifier,
        private val attachmentsClassLoaderCache: AttachmentsClassLoaderCache?,
        val digestService: DigestService
) : FullTransaction() {

    /**
     * Old version of [LedgerTransaction] constructor for ABI compatibility.
     */
    @DeprecatedConstructorForDeserialization(1)
    private constructor(
        inputs: List<StateAndRef<ContractState>>,
        outputs: List<TransactionState<ContractState>>,
        commands: List<CommandWithParties<CommandData>>,
        attachments: List<Attachment>,
        id: SecureHash,
        notary: Party?,
        timeWindow: TimeWindow?,
        privacySalt: PrivacySalt,
        networkParameters: NetworkParameters?,
        references: List<StateAndRef<ContractState>>,
        componentGroups: List<ComponentGroup>?,
        serializedInputs: List<SerializedStateAndRef>?,
        serializedReferences: List<SerializedStateAndRef>?,
        isAttachmentTrusted: (Attachment) -> Boolean,
        verifierFactory: (LedgerTransaction, SerializationContext) -> Verifier,
        attachmentsClassLoaderCache: AttachmentsClassLoaderCache?
    ) : this(
            inputs, outputs, commands, attachments, id, notary, timeWindow, privacySalt,
            networkParameters, references, componentGroups, serializedInputs, serializedReferences,
            isAttachmentTrusted, verifierFactory, attachmentsClassLoaderCache, DigestService.sha2_256)

    companion object {
        private val logger = contextLogger()

        private fun <T> protect(list: List<T>): List<T> {
            return list.run {
                if (isEmpty()) {
                    emptyList()
                } else {
                    unmodifiableList(this)
                }
            }
        }

        private fun <T> protectOrNull(list: List<T>?): List<T>? = list?.let(::protect)

        @CordaInternal
        internal fun create(
                inputs: List<StateAndRef<ContractState>>,
                outputs: List<TransactionState<ContractState>>,
                commands: List<CommandWithParties<CommandData>>,
                attachments: List<Attachment>,
                id: SecureHash,
                notary: Party?,
                timeWindow: TimeWindow?,
                privacySalt: PrivacySalt,
                networkParameters: NetworkParameters,
                references: List<StateAndRef<ContractState>>,
                componentGroups: List<ComponentGroup>? = null,
                serializedInputs: List<SerializedStateAndRef>? = null,
                serializedReferences: List<SerializedStateAndRef>? = null,
                isAttachmentTrusted: (Attachment) -> Boolean,
                attachmentsClassLoaderCache: AttachmentsClassLoaderCache?,
                digestService: DigestService
        ): LedgerTransaction {
            return LedgerTransaction(
                inputs = inputs,
                outputs = outputs,
                commands = commands,
                attachments = attachments,
                id = id,
                notary = notary,
                timeWindow = timeWindow,
                privacySalt = privacySalt,
                networkParameters = networkParameters,
                references = references,
                componentGroups = protectOrNull(componentGroups),
                serializedInputs = protectOrNull(serializedInputs),
                serializedReferences = protectOrNull(serializedReferences),
                isAttachmentTrusted = isAttachmentTrusted,
                verifierFactory = ::BasicVerifier,
                attachmentsClassLoaderCache = attachmentsClassLoaderCache,
                digestService = digestService
            )
        }

        /**
         * This factory function will create an instance of [LedgerTransaction]
         * that will be used for contract verification.
         * @see BasicVerifier
         */
        @CordaInternal
        fun createForContractVerify(
                inputs: List<StateAndRef<ContractState>>,
                outputs: List<TransactionState<ContractState>>,
                commands: List<CommandWithParties<CommandData>>,
                attachments: List<Attachment>,
                id: SecureHash,
                notary: Party?,
                timeWindow: TimeWindow?,
                privacySalt: PrivacySalt,
                networkParameters: NetworkParameters?,
                references: List<StateAndRef<ContractState>>,
                digestService: DigestService): LedgerTransaction {
            return LedgerTransaction(
                inputs = protect(inputs),
                outputs = protect(outputs),
                commands = protect(commands),
                attachments = protect(attachments),
                id = id,
                notary = notary,
                timeWindow = timeWindow,
                privacySalt = privacySalt,
                networkParameters = networkParameters,
                references = protect(references),
                componentGroups = null,
                serializedInputs = null,
                serializedReferences = null,
                isAttachmentTrusted = { true },
                verifierFactory = ::NoOpVerifier,
                attachmentsClassLoaderCache = null,
                digestService = digestService
                // This check accesses input states and must run on the LedgerTransaction
                // instance that is verified, not on the outer LedgerTransaction shell.
                // All states must also deserialize using the correct SerializationContext.
            ).also(LedgerTransaction::checkBaseInvariants)
        }
    }

    val inputStates: List<ContractState> get() = inputs.map { it.state.data }
    val referenceStates: List<ContractState> get() = references.map { it.state.data }

    /**
     * Returns the typed input StateAndRef at the specified index
     * @param index The index into the inputs.
     * @return The [StateAndRef]
     */
    fun <T : ContractState> inRef(index: Int): StateAndRef<T> = uncheckedCast(inputs[index])

    /**
     * Verifies this transaction and runs contract code. At this stage it is assumed that signatures have already been verified.
     *
     * The contract verification logic is run in a custom classloader created for the current transaction.
     * This classloader is only used during verification and does not leak to the client code.
     *
     * The reason for this is that classes (contract states) deserialized in this classloader would actually be a different type from what
     * the calling code would expect.
     *
     * @throws TransactionVerificationException if anything goes wrong.
     */
    @Throws(TransactionVerificationException::class)
    fun verify() {
        internalPrepareVerify(attachments).verify()
    }

    /**
     * This method has to be called in a context where it has access to the database.
     */
    @CordaInternal
    internal fun internalPrepareVerify(txAttachments: List<Attachment>): Verifier {
        // Switch thread local deserialization context to using a cached attachments classloader. This classloader enforces various rules
        // like no-overlap, package namespace ownership and (in future) deterministic Java.
        return AttachmentsClassLoaderBuilder.withAttachmentsClassloaderContext(
                txAttachments,
                getParamsWithGoo(),
                id,
                isAttachmentTrusted = isAttachmentTrusted,
                attachmentsClassLoaderCache = attachmentsClassLoaderCache) { serializationContext ->

            // Legacy check - warns if the LedgerTransaction was created incorrectly.
            checkLtxForVerification()

            // Create a copy of the outer LedgerTransaction which deserializes all fields using
            // the serialization context (or its deserializationClassloader).
            // Only the copy will be used for verification, and the outer shell will be discarded.
            // This artifice is required to preserve backwards compatibility.
            // NOTE: The Verifier creates the copies of the LedgerTransaction object now.
            verifierFactory(this, serializationContext)
        }
    }

    /**
     * Pass all of this [LedgerTransaction] object's serialized state to a [transformer] function.
     */
    @CordaInternal
    fun <T> transform(transformer: (List<ComponentGroup>, List<SerializedStateAndRef>, List<SerializedStateAndRef>) -> T): T {
        return transformer(componentGroups ?: emptyList(), serializedInputs ?: emptyList(), serializedReferences ?: emptyList())
    }

    /**
     * We need a way to customise transaction verification inside the
     * Node without changing either the wire format or any public APIs.
     */
    @CordaInternal
    fun specialise(alternateVerifier: (LedgerTransaction, SerializationContext) -> Verifier): LedgerTransaction = LedgerTransaction(
        inputs = inputs,
        outputs = outputs,
        commands = commands,
        attachments = attachments,
        id = id,
        notary = notary,
        timeWindow = timeWindow,
        privacySalt = privacySalt,
        networkParameters = networkParameters,
        references = references,
        componentGroups = componentGroups,
        serializedInputs = serializedInputs,
        serializedReferences = serializedReferences,
        isAttachmentTrusted = isAttachmentTrusted,
        verifierFactory = if (verifierFactory == ::NoOpVerifier) {
            throw IllegalStateException("Cannot specialise transaction while verifying contracts")
        } else {
            alternateVerifier
        },
        attachmentsClassLoaderCache = attachmentsClassLoaderCache,
        digestService = digestService
    )

    // Read network parameters with backwards compatibility goo.
    private fun getParamsWithGoo(): NetworkParameters {
        var params = networkParameters
        if (params == null) {
            // This path is triggered if someone used old constructors that were accidentally exposed; darn Kotlin's lack of package-private
            // visibility! We did originally try to maintain verification codepaths that supported lack of network parameters, but, it
            // got too convoluted and people kept just !! asserting the nullity away because on normal codepaths this is always set.
            logger.warn("Network parameters on the LedgerTransaction with id: $id are null. Please don't use deprecated constructors of the LedgerTransaction. " +
                    "Use WireTransaction.toLedgerTransaction instead. The result of the verify method would not be accurate.")
            // Roll the dice - we're probably in flow context if we got here at all, which means we can fish the current params out.
            params = getParamsFromFlowLogic()
            if (params == null)
                throw UnsupportedOperationException("Cannot verify a LedgerTransaction created using deprecated constructors outside of flow context.")
        }
        return params
    }

    private fun getParamsFromFlowLogic(): NetworkParameters? {
        return FlowLogic.currentTopLevel?.serviceHub?.networkParameters
    }

    /**
     */
    private fun checkLtxForVerification() {
        if (serializedInputs == null || serializedReferences == null || componentGroups == null) {
            logger.warn("The LedgerTransaction should not be instantiated directly from client code. Please use WireTransaction.toLedgerTransaction." +
                    "The result of the verify method might not be accurate.")
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
    data class InOutGroup<out T : ContractState, out K : Any>(val inputs: List<T>, val outputs: List<T>, val groupingKey: K)
    // DOCEND 3

    /**
     * Helper to simplify getting an indexed input [ContractState].
     * @param index the position of the item in the inputs.
     * @return The [StateAndRef] at the requested index
     */
    fun getInput(index: Int): ContractState = inputs[index].state.data

    /**
     * Helper to simplify getting an indexed reference input [ContractState].
     * @param index the position of the item in the references.
     * @return The [StateAndRef] at the requested index.
     */
    fun getReferenceInput(index: Int): ContractState = references[index].state.data

    /**
     * Helper to simplify getting all inputs states of a particular class, interface, or base class.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [ContractState].
     * @return the possibly empty list of inputs matching the clazz restriction.
     */
    fun <T : ContractState> inputsOfType(clazz: Class<T>): List<T> = inputs.mapNotNull { clazz.castIfPossible(it.state.data) }

    inline fun <reified T : ContractState> inputsOfType(): List<T> = inputsOfType(T::class.java)

    /**
     * Helper to simplify getting all reference input states of a particular class, interface, or base class.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [ContractState].
     * @return the possibly empty list of inputs matching the clazz restriction.
     */
    fun <T : ContractState> referenceInputsOfType(clazz: Class<T>): List<T> = references.mapNotNull { clazz.castIfPossible(it.state.data) }

    inline fun <reified T : ContractState> referenceInputsOfType(): List<T> = referenceInputsOfType(T::class.java)

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
     * Helper to simplify getting all reference input states of a particular class, interface, or base class.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [ContractState].
     * @return the possibly empty list of reference inputs [StateAndRef] matching the clazz restriction.
     */
    fun <T : ContractState> referenceInputRefsOfType(clazz: Class<T>): List<StateAndRef<T>> {
        return references.mapNotNull { if (clazz.isInstance(it.state.data)) uncheckedCast<StateAndRef<ContractState>, StateAndRef<T>>(it) else null }
    }

    inline fun <reified T : ContractState> referenceInputRefsOfType(): List<StateAndRef<T>> = referenceInputRefsOfType(T::class.java)

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
     * Helper to simplify filtering reference inputs according to a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [ContractState].
     * @param predicate A filtering function taking a state of type T and returning true if it should be included in the list.
     * The class filtering is applied before the predicate.
     * @return the possibly empty list of reference states matching the predicate and clazz restrictions.
     */
    fun <T : ContractState> filterReferenceInputs(clazz: Class<T>, predicate: Predicate<T>): List<T> {
        return referenceInputsOfType(clazz).filter { predicate.test(it) }
    }

    inline fun <reified T : ContractState> filterReferenceInputs(crossinline predicate: (T) -> Boolean): List<T> {
        return filterReferenceInputs(T::class.java, Predicate { predicate(it) })
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
     * Helper to simplify filtering reference inputs according to a [Predicate].
     * @param predicate A filtering function taking a state of type T and returning true if it should be included in the list.
     * The class filtering is applied before the predicate.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [ContractState].
     * @return the possibly empty list of references [StateAndRef] matching the predicate and clazz restrictions.
     */
    fun <T : ContractState> filterReferenceInputRefs(clazz: Class<T>, predicate: Predicate<T>): List<StateAndRef<T>> {
        return referenceInputRefsOfType(clazz).filter { predicate.test(it.state.data) }
    }

    inline fun <reified T : ContractState> filterReferenceInputRefs(crossinline predicate: (T) -> Boolean): List<StateAndRef<T>> {
        return filterReferenceInputRefs(T::class.java, Predicate { predicate(it) })
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
     * Helper to simplify finding a single reference inputs [ContractState] matching a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of ContractState.
     * @param predicate A filtering function taking a state of type T and returning true if this is the desired item.
     * The class filtering is applied before the predicate.
     * @return the single item matching the predicate.
     * @throws IllegalArgumentException if no item, or multiple items are found matching the requirements.
     */
    fun <T : ContractState> findReference(clazz: Class<T>, predicate: Predicate<T>): T {
        return referenceInputsOfType(clazz).single { predicate.test(it) }
    }

    inline fun <reified T : ContractState> findReference(crossinline predicate: (T) -> Boolean): T {
        return findReference(T::class.java, Predicate { predicate(it) })
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
     * Helper to simplify finding a single reference input matching a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of ContractState.
     * @param predicate A filtering function taking a state of type T and returning true if this is the desired item.
     * The class filtering is applied before the predicate.
     * @return the single item matching the predicate.
     * @throws IllegalArgumentException if no item, or multiple items are found matching the requirements.
     */
    fun <T : ContractState> findReferenceInputRef(clazz: Class<T>, predicate: Predicate<T>): StateAndRef<T> {
        return referenceInputRefsOfType(clazz).single { predicate.test(it.state.data) }
    }

    inline fun <reified T : ContractState> findReferenceInputRef(crossinline predicate: (T) -> Boolean): StateAndRef<T> {
        return findReferenceInputRef(T::class.java, Predicate { predicate(it) })
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

    operator fun component1(): List<StateAndRef<ContractState>> = inputs
    operator fun component2(): List<TransactionState<ContractState>> = outputs
    operator fun component3(): List<CommandWithParties<CommandData>> = commands
    operator fun component4(): List<Attachment> = attachments
    operator fun component5(): SecureHash = id
    operator fun component6(): Party? = notary
    operator fun component7(): TimeWindow? = timeWindow
    operator fun component8(): PrivacySalt = privacySalt
    operator fun component9(): NetworkParameters? = networkParameters
    operator fun component10(): List<StateAndRef<ContractState>> = references

    override fun equals(other: Any?): Boolean = this === other || other is LedgerTransaction && this.id == other.id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String {
        return """LedgerTransaction(
            |    id=$id
            |    inputs=$inputs
            |    outputs=$outputs
            |    commands=$commands
            |    attachments=$attachments
            |    notary=$notary
            |    timeWindow=$timeWindow
            |    references=$references
            |    networkParameters=$networkParameters
            |    privacySalt=$privacySalt
            |)""".trimMargin()
    }

    // Stuff that we can't remove and so is deprecated instead
    //
    @Deprecated("LedgerTransaction should not be created directly, use WireTransaction.toLedgerTransaction instead.")
    constructor(
            inputs: List<StateAndRef<ContractState>>,
            outputs: List<TransactionState<ContractState>>,
            commands: List<CommandWithParties<CommandData>>,
            attachments: List<Attachment>,
            id: SecureHash,
            notary: Party?,
            timeWindow: TimeWindow?,
            privacySalt: PrivacySalt
    ) : this(
            inputs = inputs,
            outputs = outputs,
            commands = commands,
            attachments = attachments,
            id = id,
            notary = notary,
            timeWindow = timeWindow,
            privacySalt = privacySalt,
            networkParameters = null,
            references = emptyList(),
            componentGroups = null,
            serializedInputs = null,
            serializedReferences = null,
            isAttachmentTrusted = Attachment::isUploaderTrusted,
            verifierFactory = ::BasicVerifier,
            attachmentsClassLoaderCache = null
    )

    @Deprecated("LedgerTransaction should not be created directly, use WireTransaction.toLedgerTransaction instead.")
    constructor(
            inputs: List<StateAndRef<ContractState>>,
            outputs: List<TransactionState<ContractState>>,
            commands: List<CommandWithParties<CommandData>>,
            attachments: List<Attachment>,
            id: SecureHash,
            notary: Party?,
            timeWindow: TimeWindow?,
            privacySalt: PrivacySalt,
            networkParameters: NetworkParameters
    ) : this(
            inputs = inputs,
            outputs = outputs,
            commands = commands,
            attachments = attachments,
            id = id,
            notary = notary,
            timeWindow = timeWindow,
            privacySalt = privacySalt,
            networkParameters = networkParameters,
            references = emptyList(),
            componentGroups = null,
            serializedInputs = null,
            serializedReferences = null,
            isAttachmentTrusted = Attachment::isUploaderTrusted,
            verifierFactory = ::BasicVerifier,
            attachmentsClassLoaderCache = null
    )

    @Deprecated("LedgerTransactions should not be created directly, use WireTransaction.toLedgerTransaction instead.")
    fun copy(inputs: List<StateAndRef<ContractState>>,
             outputs: List<TransactionState<ContractState>>,
             commands: List<CommandWithParties<CommandData>>,
             attachments: List<Attachment>,
             id: SecureHash,
             notary: Party?,
             timeWindow: TimeWindow?,
             privacySalt: PrivacySalt
    ): LedgerTransaction {
        return LedgerTransaction(
                inputs = inputs,
                outputs = outputs,
                commands = commands,
                attachments = attachments,
                id = id,
                notary = notary,
                timeWindow = timeWindow,
                privacySalt = privacySalt,
                networkParameters = networkParameters,
                references = references,
                componentGroups = componentGroups,
                serializedInputs = serializedInputs,
                serializedReferences = serializedReferences,
                isAttachmentTrusted = isAttachmentTrusted,
                verifierFactory = verifierFactory,
                attachmentsClassLoaderCache = attachmentsClassLoaderCache,
                digestService = digestService
        )
    }

    @Deprecated("LedgerTransactions should not be created directly, use WireTransaction.toLedgerTransaction instead.")
    fun copy(inputs: List<StateAndRef<ContractState>> = this.inputs,
             outputs: List<TransactionState<ContractState>> = this.outputs,
             commands: List<CommandWithParties<CommandData>> = this.commands,
             attachments: List<Attachment> = this.attachments,
             id: SecureHash = this.id,
             notary: Party? = this.notary,
             timeWindow: TimeWindow? = this.timeWindow,
             privacySalt: PrivacySalt = this.privacySalt,
             networkParameters: NetworkParameters? = this.networkParameters
    ): LedgerTransaction {
        return LedgerTransaction(
                inputs = inputs,
                outputs = outputs,
                commands = commands,
                attachments = attachments,
                id = id,
                notary = notary,
                timeWindow = timeWindow,
                privacySalt = privacySalt,
                networkParameters = networkParameters,
                references = references,
                componentGroups = componentGroups,
                serializedInputs = serializedInputs,
                serializedReferences = serializedReferences,
                isAttachmentTrusted = isAttachmentTrusted,
                verifierFactory = verifierFactory,
                attachmentsClassLoaderCache = attachmentsClassLoaderCache,
                digestService = digestService
        )
    }
}

/**
 * This is the default [Verifier] that configures Corda
 * to execute [Contract.verify(LedgerTransaction)].
 *
 * THIS CLASS IS NOT PUBLIC API, AND IS DELIBERATELY PRIVATE!
 */
@CordaInternal
private class BasicVerifier(
    ltx: LedgerTransaction,
    private val serializationContext: SerializationContext
) : AbstractVerifier(ltx, serializationContext.deserializationClassLoader) {

    init {
        // This is a sanity check: We should only instantiate this
        // class from [LedgerTransaction.internalPrepareVerify].
        require(serializationContext === SerializationFactory.defaultFactory.currentContext) {
            "BasicVerifier for TX ${ltx.id} created outside its SerializationContext"
        }

        // Fetch these commands' signing parties from the database.
        // Corda forbids database access during contract verification,
        // and so we must load the commands here eagerly instead.
        // THIS ALSO DESERIALISES THE COMMANDS USING THE WRONG CONTEXT
        // BECAUSE THAT CONTEXT WAS CHOSEN WHEN THE LAZY MAP WAS CREATED,
        // AND CHANGING THE DEFAULT CONTEXT HERE DOES NOT AFFECT IT.
        ltx.commands.eagerDeserialise()
    }

    override val transaction: Supplier<LedgerTransaction>
        get() = Supplier(::createTransaction)

    private fun createTransaction(): LedgerTransaction {
        // Deserialize all relevant classes using the serializationContext.
        return SerializationFactory.defaultFactory.withCurrentContext(serializationContext) {
            ltx.transform { componentGroups, serializedInputs, serializedReferences ->
                val deserializedInputs = serializedInputs.map(SerializedStateAndRef::toStateAndRef)
                val deserializedReferences = serializedReferences.map(SerializedStateAndRef::toStateAndRef)
                val deserializedOutputs = deserialiseComponentGroup(componentGroups, TransactionState::class, ComponentGroupEnum.OUTPUTS_GROUP, forceDeserialize = true)
                val deserializedCommands = deserialiseCommands(componentGroups, forceDeserialize = true, digestService = ltx.digestService)
                val authenticatedDeserializedCommands = deserializedCommands.mapIndexed { idx, cmd ->
                    // Requires ltx.commands to have been deserialized already.
                    @Suppress("DEPRECATION")   // Deprecated feature.
                    val parties = ltx.commands[idx].signingParties
                    CommandWithParties(cmd.signers, parties, cmd.value)
                }

                LedgerTransaction.createForContractVerify(
                    inputs = deserializedInputs,
                    outputs = deserializedOutputs,
                    commands = authenticatedDeserializedCommands,
                    attachments = ltx.attachments,
                    id = ltx.id,
                    notary = ltx.notary,
                    timeWindow = ltx.timeWindow,
                    privacySalt = ltx.privacySalt,
                    networkParameters = ltx.networkParameters,
                    references = deserializedReferences,
                    digestService = ltx.digestService
                )
            }
        }
    }
}

/**
 * A "do nothing" [Verifier] installed for contract verification.
 *
 * THIS CLASS IS NOT PUBLIC API, AND IS DELIBERATELY PRIVATE!
 */
@Suppress("unused_parameter")
@CordaInternal
private class NoOpVerifier(ltx: LedgerTransaction, serializationContext: SerializationContext) : Verifier {
    // Invoking LedgerTransaction.verify() from Contract.verify(LedgerTransaction)
    // will execute this function. But why would anyone do that?!
    override fun verify() {}
}

package net.corda.core.transactions

import co.paralleluniverse.strands.Strand
import net.corda.core.CordaInternal
import net.corda.core.DeleteForDJVM
import net.corda.core.contracts.*
import net.corda.core.cordapp.DEFAULT_CORDAPP_VERSION
import net.corda.core.contracts.ContractAttachment.Companion.getContractVersion
import net.corda.core.crypto.*
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.node.NetworkParameters
import net.corda.core.node.ServiceHub
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.ZoneVersionTooLowException
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.KeyManagementService
import net.corda.core.node.services.vault.AttachmentQueryCriteria
import net.corda.core.node.services.vault.AttachmentSort
import net.corda.core.node.services.vault.Builder
import net.corda.core.node.services.vault.Sort
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationFactory
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.lazyMapped
import net.corda.core.utilities.warnOnce
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.collections.ArrayList

/**
 * A TransactionBuilder is a transaction class that's mutable (unlike the others which are all immutable). It is
 * intended to be passed around contracts that may edit it by adding new states/commands. Then once the states
 * and commands are right, this class can be used as a holding bucket to gather signatures from multiple parties.
 *
 * The builder can be customised for specific transaction types, e.g. where additional processing is needed
 * before adding a state/command.
 *
 * @param notary Notary used for the transaction. If null, this indicates the transaction DOES NOT have a notary.
 * When this is set to a non-null value, an output state can be added by just passing in a [ContractState] – a
 * [TransactionState] with this notary specified will be generated automatically.
 */
@DeleteForDJVM
open class TransactionBuilder @JvmOverloads constructor(
        var notary: Party? = null,
        var lockId: UUID = (Strand.currentStrand() as? FlowStateMachine<*>)?.id?.uuid ?: UUID.randomUUID(),
        protected val inputs: MutableList<StateRef> = arrayListOf(),
        protected val attachments: MutableList<SecureHash> = arrayListOf(),
        protected val outputs: MutableList<TransactionState<ContractState>> = arrayListOf(),
        protected val commands: MutableList<Command<*>> = arrayListOf(),
        protected var window: TimeWindow? = null,
        protected var privacySalt: PrivacySalt = PrivacySalt(),
        protected val references: MutableList<StateRef> = arrayListOf(),
        protected val serviceHub: ServiceHub? = (Strand.currentStrand() as? FlowStateMachine<*>)?.serviceHub
) {

    private companion object {
        private val log = contextLogger()
    }

    private val inputsWithTransactionState = arrayListOf<StateAndRef<ContractState>>()
    private val referencesWithTransactionState = arrayListOf<TransactionState<ContractState>>()

    /**
     * Creates a copy of the builder.
     */
    fun copy(): TransactionBuilder {
        val t = TransactionBuilder(
                notary = notary,
                inputs = ArrayList(inputs),
                attachments = ArrayList(attachments),
                outputs = ArrayList(outputs),
                commands = ArrayList(commands),
                window = window,
                privacySalt = privacySalt,
                references = references,
                serviceHub = serviceHub
        )
        t.inputsWithTransactionState.addAll(this.inputsWithTransactionState)
        t.referencesWithTransactionState.addAll(this.referencesWithTransactionState)
        return t
    }

    // DOCSTART 1
    /** A more convenient way to add items to this transaction that calls the add* methods for you based on type */
    fun withItems(vararg items: Any) = apply {
        for (t in items) {
            when (t) {
                is StateAndRef<*> -> addInputState(t)
                is ReferencedStateAndRef<*> -> addReferenceState(t)
                is SecureHash -> addAttachment(t)
                is TransactionState<*> -> addOutputState(t)
                is StateAndContract -> addOutputState(t.state, t.contract)
                is ContractState -> throw UnsupportedOperationException("Removed as of V1: please use a StateAndContract instead")
                is Command<*> -> addCommand(t)
                is CommandData -> throw IllegalArgumentException("You passed an instance of CommandData, but that lacks the pubkey. You need to wrap it in a Command object first.")
                is TimeWindow -> setTimeWindow(t)
                is PrivacySalt -> setPrivacySalt(t)
                else -> throw IllegalArgumentException("Wrong argument type: ${t.javaClass}")
            }
        }
    }
    // DOCEND 1

    /**
     * Generates a [WireTransaction] from this builder, resolves any [AutomaticPlaceholderConstraint], and selects the attachments to use for this transaction.
     *
     * @returns A new [WireTransaction] that will be unaffected by further changes to this [TransactionBuilder].
     *
     * @throws ZoneVersionTooLowException if there are reference states and the zone minimum platform version is less than 4.
     */
    @Throws(MissingContractAttachments::class)
    fun toWireTransaction(services: ServicesForResolution): WireTransaction = toWireTransactionWithContext(services)

    @CordaInternal
    internal fun toWireTransactionWithContext(services: ServicesForResolution, serializationContext: SerializationContext? = null): WireTransaction {
        val referenceStates = referenceStates()
        if (referenceStates.isNotEmpty()) {
            services.ensureMinimumPlatformVersion(4, "Reference states")
        }

        val (allContractAttachments: Collection<SecureHash>, resolvedOutputs: List<TransactionState<ContractState>>) = selectContractAttachmentsAndOutputStateConstraints(services, serializationContext)

        // Final sanity check that all states have the correct constraints.
        for (state in (inputsWithTransactionState.map { it.state } + resolvedOutputs)) {
            checkConstraintValidity(state)
        }

        return SerializationFactory.defaultFactory.withCurrentContext(serializationContext) {
            WireTransaction(
                    createComponentGroups(
                            inputStates(),
                            resolvedOutputs,
                            commands,
                            (allContractAttachments + attachments).toSortedSet().toList(), // Sort the attachments to ensure transaction builds are stable.
                            notary,
                            window,
                            referenceStates,
                            services.networkParametersStorage.currentHash),
                    privacySalt
            )
        }
    }

    /**
     * This method is responsible for selecting the contract versions to be used for the current transaction and resolve the output state [AutomaticPlaceholderConstraint]s.
     * The contract attachments are used to create a deterministic Classloader to deserialise the transaction and to run the contract verification.
     *
     * The selection logic depends on the Attachment Constraints of the input, output and reference states, also on the explicitly set attachments.
     * TODO also on the versions of the attachments of the transactions generating the input states. ( after we add versioning)
     */
    private fun selectContractAttachmentsAndOutputStateConstraints(
            services: ServicesForResolution, serializationContext: SerializationContext?): Pair<Collection<SecureHash>, List<TransactionState<ContractState>>> {

        // Determine the explicitly set contract attachments.
        val explicitAttachmentContracts: List<Pair<ContractClassName, SecureHash>> = this.attachments
                .map(services.attachments::openAttachment)
                .mapNotNull { it as? ContractAttachment }
                .flatMap { attch ->
                    attch.allContracts.map { it to attch.id }
                }

        // And fail early if there's more than 1 for a contract.
        require(explicitAttachmentContracts.isEmpty() || explicitAttachmentContracts.groupBy { (ctr, _) -> ctr }.all { (_, groups) -> groups.size == 1 }) { "Multiple attachments set for the same contract." }

        val explicitAttachmentContractsMap: Map<ContractClassName, SecureHash> = explicitAttachmentContracts.toMap()

        val inputContractGroups: Map<ContractClassName, List<TransactionState<ContractState>>> = inputsWithTransactionState.map {it.state}.groupBy { it.contract }
        val outputContractGroups: Map<ContractClassName, List<TransactionState<ContractState>>> = outputs.groupBy { it.contract }

        val allContracts: Set<ContractClassName> = inputContractGroups.keys + outputContractGroups.keys

        // Handle reference states.
        // Filter out all contracts that might have been already used by 'normal' input or output states.
        val referenceStateGroups: Map<ContractClassName, List<TransactionState<ContractState>>> = referencesWithTransactionState.groupBy { it.contract }
        val refStateContractAttachments: List<AttachmentId> = referenceStateGroups
                .filterNot { it.key in allContracts }
                .map { refStateEntry ->
                    selectAttachmentThatSatisfiesConstraints(true, refStateEntry.key, refStateEntry.value, services)
                }

        val contractClassNameToInputStateRef : Map<ContractClassName, Set<StateRef>> = inputsWithTransactionState.map { Pair(it.state.contract,it.ref) }.groupBy { it.first }.mapValues { it.value.map { e -> e.second }.toSet() }

        // For each contract, resolve the AutomaticPlaceholderConstraint, and select the attachment.
        val contractAttachmentsAndResolvedOutputStates: List<Pair<Set<AttachmentId>, List<TransactionState<ContractState>>?>> = allContracts.toSet()
                .map { ctr ->
                    handleContract(ctr, inputContractGroups[ctr], contractClassNameToInputStateRef[ctr], outputContractGroups[ctr], explicitAttachmentContractsMap[ctr], services)
                }

        val resolvedStates: List<TransactionState<ContractState>> = contractAttachmentsAndResolvedOutputStates.mapNotNull { it.second }
                .flatten()

        // The output states need to preserve the order in which they were added.
        val resolvedOutputStatesInTheOriginalOrder: List<TransactionState<ContractState>> = outputStates().map { os -> resolvedStates.find { rs -> rs.data == os.data && rs.encumbrance == os.encumbrance }!! }

        val attachments: Collection<AttachmentId> = contractAttachmentsAndResolvedOutputStates.flatMap { it.first } + refStateContractAttachments

        return Pair(attachments, resolvedOutputStatesInTheOriginalOrder)
    }

    private val automaticConstraints = setOf(AutomaticPlaceholderConstraint, AutomaticHashConstraint)

    /**
     * Selects an attachment and resolves the constraints for the output states with [AutomaticPlaceholderConstraint].
     *
     * This is the place where the complex logic of the upgradability of contracts and constraint propagation is handled.
     *
     * * For contracts that *are not* annotated with @[NoConstraintPropagation], this will attempt to determine a constraint for the output states
     * that is a valid transition from all the constraints of the input states.
     *
     * * For contracts that *are* annotated with @[NoConstraintPropagation], this enforces setting an explicit output constraint.
     *
     * * For states with the [HashAttachmentConstraint], if an attachment with that hash is installed on the current node, then it will be inherited by the output states and selected for the transaction.
     * Otherwise a [MissingContractAttachments] is thrown.
     *
     * * For input states with [WhitelistedByZoneAttachmentConstraint] or a [AlwaysAcceptAttachmentConstraint] implementations, then the currently installed cordapp version is used.
     */
    private fun handleContract(
            contractClassName: ContractClassName,
            inputStates: List<TransactionState<ContractState>>?,
            inputStateRefs: Set<StateRef>?,
            outputStates: List<TransactionState<ContractState>>?,
            explicitContractAttachment: AttachmentId?,
            services: ServicesForResolution
    ): Pair<Set<AttachmentId>, List<TransactionState<ContractState>>?> {
        val inputsAndOutputs = (inputStates ?: emptyList()) + (outputStates ?: emptyList())

        // Hash to Signature constraints migration switchover
        // identify if any input-output pairs are transitioning from hash to signature constraints:
        // 1. output states contain implicitly selected hash constraint (pre-existing from set of unconsumed states in a nodes vault) or explicitly set SignatureConstraint
        // 2. node has signed jar for associated contract class and version
        val inputsHashConstraints = inputStates?.filter { it.constraint is HashAttachmentConstraint } ?: emptyList()
        val outputHashConstraints = outputStates?.filter { it.constraint is HashAttachmentConstraint } ?: emptyList()
        val outputSignatureConstraints = outputStates?.filter { it.constraint is SignatureAttachmentConstraint } ?: emptyList()
        if (inputsHashConstraints.isNotEmpty() && (outputHashConstraints.isNotEmpty() || outputSignatureConstraints.isNotEmpty())) {
            val attachmentQueryCriteria = AttachmentQueryCriteria.AttachmentsQueryCriteria(contractClassNamesCondition = Builder.equal(listOf(contractClassName)))
            val attachmentIds = services.attachments.queryAttachments(attachmentQueryCriteria)
            // only switchover if we have both signed and unsigned attachments for the given contract class name
            if (attachmentIds.isNotEmpty() && attachmentIds.size == 2)  {
                val attachmentsToUse = attachmentIds.map {
                    services.attachments.openAttachment(it)?.let { it as ContractAttachment }
                            ?: throw IllegalArgumentException("Contract attachment $it for $contractClassName is missing.")
                }
                val signedAttachment = attachmentsToUse.filter { it.isSigned }.firstOrNull() ?: throw IllegalArgumentException("Signed contract attachment for $contractClassName is missing.")
                val outputConstraints =
                    if (outputHashConstraints.isNotEmpty()) {
                        log.warn("Switching output states from hash to signed constraints using signers in signed contract attachment given by ${signedAttachment.id}")
                        val outputsSignatureConstraints = outputHashConstraints.map { it.copy(constraint = SignatureAttachmentConstraint(signedAttachment.signerKeys.first())) }
                        outputs.addAll(outputsSignatureConstraints)
                        outputs.removeAll(outputHashConstraints)
                        outputsSignatureConstraints
                    } else outputSignatureConstraints
                return Pair(attachmentIds.toSet(), outputConstraints)
            }
        }

        // Determine if there are any HashConstraints that pin the version of a contract. If there are, check if we trust them.
        val hashAttachments = inputsAndOutputs
                .filter { it.constraint is HashAttachmentConstraint }
                .map { state ->
                    val attachment = services.attachments.openAttachment((state.constraint as HashAttachmentConstraint).attachmentId)
                    if (attachment == null || attachment !is ContractAttachment || !isUploaderTrusted(attachment.uploader)) {
                        // This should never happen because these are input states that should have been validated already.
                        throw MissingContractAttachments(listOf(state))
                    }
                    attachment
                }.toSet()

        // Check that states with the HashConstraint don't conflict between themselves or with an explicitly set attachment.
        require(hashAttachments.size <= 1) {
            "Transaction was built with $contractClassName states with multiple HashConstraints. This is illegal, because it makes it impossible to validate with a single version of the contract code."
        }

        // This will contain the hash of the JAR that *has* to be used by this Transaction, because it is explicit. Or null if none.
        val forcedAttachmentId = explicitContractAttachment ?: hashAttachments.singleOrNull()?.id

        fun selectAttachment() = selectAttachmentThatSatisfiesConstraints(
                false,
                contractClassName,
                inputsAndOutputs.filterNot { it.constraint in automaticConstraints },
                services)

        // This will contain the hash of the JAR that will be used by this Transaction.
        val selectedAttachmentId = forcedAttachmentId ?: selectAttachment()

        val attachmentToUse = services.attachments.openAttachment(selectedAttachmentId)?.let { it as ContractAttachment }
                ?: throw IllegalArgumentException("Contract attachment $selectedAttachmentId for $contractClassName is missing.")

        // For Exit transactions (no output states) there is no need to resolve the output constraints.
        if (outputStates == null) {
            return Pair(setOf(selectedAttachmentId), null)
        }

        // If there are no automatic constraints, there is nothing to resolve.
        if (outputStates.none { it.constraint in automaticConstraints }) {
            return Pair(setOf(selectedAttachmentId), outputStates)
        }

        // The final step is to resolve AutomaticPlaceholderConstraint.
        val automaticConstraintPropagation = contractClassName.contractHasAutomaticConstraintPropagation(inputsAndOutputs.first().data::class.java.classLoader)

        // When automaticConstraintPropagation is disabled for a contract, output states must an explicit Constraint.
        require(automaticConstraintPropagation) { "Contract $contractClassName was marked with @NoConstraintPropagation, which means the constraint of the output states has to be set explicitly." }

        // This is the logic to determine the constraint which will replace the AutomaticPlaceholderConstraint.
        val defaultOutputConstraint = selectAttachmentConstraint(contractClassName, inputStates, attachmentToUse, services)

        // Sanity check that the selected attachment actually passes.
        val constraintAttachment = AttachmentWithContext(attachmentToUse, contractClassName, services.networkParameters)
        require(defaultOutputConstraint.isSatisfiedBy(constraintAttachment)) { "Selected output constraint: $defaultOutputConstraint not satisfying $selectedAttachmentId" }

        val resolvedOutputStates = outputStates.map {
            val outputConstraint = it.constraint
            if (outputConstraint in automaticConstraints) {
                it.copy(constraint = defaultOutputConstraint)
            } else {
                // If the constraint on the output state is already set, and is not a valid transition or can't be transitioned, then fail early.
                inputStates?.forEach { input ->
                    require(outputConstraint.canBeTransitionedFrom(input.constraint, constraintAttachment)) { "Output state constraint $outputConstraint cannot be transitioned from ${input.constraint}" }
                }
                require(outputConstraint.isSatisfiedBy(constraintAttachment)) { "Output state constraint check fails. $outputConstraint" }
                it
            }
        }

        val outputVersion = getContractVersion(attachmentToUse)
        inputStateRefs?.forEach {
            val inputMaxVersion = getContractVersion(services.loadContractAttachment(it))
            require(inputMaxVersion <= outputVersion) { //it's enough if once inputs violates the rule
                "No-Downgrade Rule has been breached for contract class $contractClassName. The output state contract version $outputVersion is lower that the version of the input state ($inputMaxVersion)."
            }
        }

        return Pair(setOf(selectedAttachmentId), resolvedOutputStates)
    }

    /**
     * If there are multiple input states with different constraints then run the constraint intersection logic to determine the resulting output constraint.
     * For issuing transactions where the attachmentToUse is JarSigned, then default to the SignatureConstraint with all the signatures.
     * TODO - in the future this step can actually create a new ContractAttachment by merging 2 signed jars of the same version.
     */
    private fun selectAttachmentConstraint(
            contractClassName: ContractClassName,
            inputStates: List<TransactionState<ContractState>>?,
            attachmentToUse: ContractAttachment,
            services: ServicesForResolution): AttachmentConstraint = when {
        inputStates != null -> attachmentConstraintsTransition(inputStates.groupBy { it.constraint }.keys, attachmentToUse)
        attachmentToUse.signerKeys.isNotEmpty() && services.networkParameters.minimumPlatformVersion < 4 -> {
            log.warnOnce("Signature constraints not available on network requiring a minimum platform version of 4. Current is: ${services.networkParameters.minimumPlatformVersion}.")
            if (useWhitelistedByZoneAttachmentConstraint(contractClassName, services.networkParameters)) {
                log.warnOnce("Reverting back to using whitelisted zone constraints for contract $contractClassName")
                WhitelistedByZoneAttachmentConstraint
            } else {
                log.warnOnce("Reverting back to using hash constraints for contract $contractClassName")
                HashAttachmentConstraint(attachmentToUse.id)
            }
        }
        attachmentToUse.signerKeys.isNotEmpty() -> makeSignatureAttachmentConstraint(attachmentToUse.signerKeys)
        useWhitelistedByZoneAttachmentConstraint(contractClassName, services.networkParameters) -> WhitelistedByZoneAttachmentConstraint
        else -> HashAttachmentConstraint(attachmentToUse.id)
    }

    /**
     * Given a set of [AttachmentConstraint]s, this function implements the rules on how constraints can evolve.
     *
     * This should be an exhaustive check, and should mirror [AttachmentConstraint.canBeTransitionedFrom].
     *
     * TODO - once support for third party signing is added, it should be implemented here. ( a constraint with 2 signatures is less restrictive than a constraint with 1 more signature)
     */
    private fun attachmentConstraintsTransition(constraints: Set<AttachmentConstraint>, attachmentToUse: ContractAttachment): AttachmentConstraint = when {

        // Sanity check.
        constraints.isEmpty() -> throw IllegalArgumentException("Cannot transition from no constraints.")

        // When all input states have the same constraint.
        constraints.size == 1 -> constraints.single()

        // Fail when combining the insecure AlwaysAcceptAttachmentConstraint with something else. The size must be at least 2 at this point.
        constraints.any { it is AlwaysAcceptAttachmentConstraint } ->
            throw IllegalArgumentException("Can't mix the AlwaysAcceptAttachmentConstraint with a secure constraint in the same transaction. This can be used to hide insecure transitions.")

        // Multiple states with Hash constraints with different hashes. This should not happen as we checked already.
        constraints.all { it is HashAttachmentConstraint } ->
            throw IllegalArgumentException("Cannot mix HashConstraints with different hashes in the same transaction.")

        // The HashAttachmentConstraint is the strongest constraint, so it wins when mixed with anything. As long as the actual constraints pass.
        // TODO - this could change if we decide to introduce a way to gracefully migrate from the Hash Constraint to the Signature Constraint.
        constraints.any { it is HashAttachmentConstraint } -> constraints.find { it is HashAttachmentConstraint }!!

        // TODO, we don't currently support mixing signature constraints with different signers. This will change once we introduce third party signers.
        constraints.all { it is SignatureAttachmentConstraint } ->
            throw IllegalArgumentException("Cannot mix SignatureAttachmentConstraints signed by different parties in the same transaction.")

        // This ensures a smooth migration from the Whitelist Constraint, given that for the transaction to be valid it still has to pass both constraints.
        // The transition is possible only when the SignatureConstraint contains ALL signers from the attachment.
        constraints.any { it is SignatureAttachmentConstraint } && constraints.any { it is WhitelistedByZoneAttachmentConstraint } -> {
            val signatureConstraint = constraints.mapNotNull { it as? SignatureAttachmentConstraint }.single()
            when {
                attachmentToUse.signerKeys.isEmpty() -> throw IllegalArgumentException("Cannot mix a state with the WhitelistedByZoneAttachmentConstraint and a state with the SignatureAttachmentConstraint, when the latest attachment is not signed. Please contact your Zone operator.")
                signatureConstraint.key.keys.containsAll(attachmentToUse.signerKeys) -> signatureConstraint
                else -> throw IllegalArgumentException("Attempting to transition a WhitelistedByZoneAttachmentConstraint state backed by an attachment signed by multiple parties to a weaker SignatureConstraint that does not require all those signatures. Please contact your Zone operator.")
            }
        }

        else -> throw IllegalArgumentException("Unexpected constraints $constraints.")
    }

    private fun makeSignatureAttachmentConstraint(attachmentSigners: List<PublicKey>) =
            SignatureAttachmentConstraint(CompositeKey.Builder().addKeys(attachmentSigners.map { it }).build())

    /**
     * This method should only be called for upgradeable contracts.
     *
     * For now we use the currently installed CorDapp version.
     */
    private fun selectAttachmentThatSatisfiesConstraints(isReference: Boolean, contractClassName: String, states: List<TransactionState<ContractState>>, services: ServicesForResolution): AttachmentId {
        val constraints = states.map { it.constraint }
        require(constraints.none { it in automaticConstraints })
        require(isReference || constraints.none { it is HashAttachmentConstraint })

        //TODO will be set by the code pending in the other PR
        val minimumRequiredContractClassVersion = DEFAULT_CORDAPP_VERSION

        //TODO consider move it to attachment service method e.g. getContractAttachmentWithHighestVersion(contractClassName, minContractVersion)
        val attachmentQueryCriteria = AttachmentQueryCriteria.AttachmentsQueryCriteria(contractClassNamesCondition = Builder.equal(listOf(contractClassName)),
                versionCondition = Builder.greaterThanOrEqual(minimumRequiredContractClassVersion))
        val attachmentSort = AttachmentSort(listOf(AttachmentSort.AttachmentSortColumn(AttachmentSort.AttachmentSortAttribute.VERSION, Sort.Direction.DESC)))

        return services.attachments.queryAttachments(attachmentQueryCriteria, attachmentSort).firstOrNull() ?: throw MissingContractAttachments(states)
    }

    private fun useWhitelistedByZoneAttachmentConstraint(contractClassName: ContractClassName, networkParameters: NetworkParameters) = contractClassName in networkParameters.whitelistedContractImplementations.keys

    @Throws(AttachmentResolutionException::class, TransactionResolutionException::class)
    fun toLedgerTransaction(services: ServiceHub) = toWireTransaction(services).toLedgerTransaction(services)

    internal fun toLedgerTransactionWithContext(services: ServicesForResolution, serializationContext: SerializationContext): LedgerTransaction {
        return toWireTransactionWithContext(services, serializationContext).toLedgerTransaction(services)
    }

    @Throws(AttachmentResolutionException::class, TransactionResolutionException::class, TransactionVerificationException::class)
    fun verify(services: ServiceHub) {
        toLedgerTransaction(services).verify()
    }

    private fun checkNotary(stateAndRef: StateAndRef<*>) {
        val notary = stateAndRef.state.notary
        require(notary == this.notary) {
            "Input state requires notary \"$notary\" which does not match the transaction notary \"${this.notary}\"."
        }
    }

    // This check is performed here as well as in BaseTransaction.
    private fun checkForInputsAndReferencesOverlap() {
        val intersection = inputs intersect references
        require(intersection.isEmpty()) {
            "A StateRef cannot be both an input and a reference input in the same transaction."
        }
    }

    private fun checkReferencesUseSameNotary() = referencesWithTransactionState.map { it.notary }.toSet().size == 1

    /**
     * If any inputs or outputs added to the [TransactionBuilder] contain [StatePointer]s, then this method can be
     * optionally called to resolve those [StatePointer]s to [StateAndRef]s. The [StateAndRef]s are then added as
     * reference states to the transaction. The effect is that the referenced data is carried along with the
     * transaction. This may or may not be appropriate in all circumstances, which is why calling this method is
     * optional.
     *
     * If this method is called outside the context of a flow, a [ServiceHub] instance must be passed to this method
     * for it to be able to resolve [StatePointer]s. Usually for a unit test, this will be an instance of mock services.
     *
     * @param serviceHub a [ServiceHub] instance needed for performing vault queries.
     *
     * @throws IllegalStateException if no [ServiceHub] is provided and no flow context is available.
     */
    private fun resolveStatePointers(transactionState: TransactionState<*>) {
        val contractState = transactionState.data
        // Find pointers in all inputs and outputs.
        val inputAndOutputPointers = StatePointerSearch(contractState).search()
        // Queue up the pointers to resolve.
        val statePointerQueue = ArrayDeque<StatePointer<*>>().apply { addAll(inputAndOutputPointers) }
        // Recursively resolve all pointers.
        while (statePointerQueue.isNotEmpty()) {
            val nextStatePointer = statePointerQueue.pop()
            if (serviceHub != null) {
                val resolvedStateAndRef = nextStatePointer.resolve(serviceHub)
                // Don't add dupe reference states because CoreTransaction doesn't allow it.
                if (resolvedStateAndRef.ref !in referenceStates()) {
                    addReferenceState(resolvedStateAndRef.referenced())
                }
            } else {
                log.warn("WARNING: You must pass in a ServiceHub reference to TransactionBuilder to resolve " +
                        "state pointers outside of flows. If you are writing a unit test then pass in a " +
                        "MockServices instance.")
                return
            }
        }
    }

    /**
     * Adds a reference input [StateRef] to the transaction.
     *
     * Note: Reference states are only supported on Corda networks running a minimum platform version of 4.
     * [toWireTransaction] will throw an [IllegalStateException] if called in such an environment.
     */
    open fun addReferenceState(referencedStateAndRef: ReferencedStateAndRef<*>) = apply {
        val stateAndRef = referencedStateAndRef.stateAndRef
        referencesWithTransactionState.add(stateAndRef.state)

        // It is likely the case that users of reference states do not have permission to change the notary assigned
        // to a reference state. Even if users _did_ have this permission the result would likely be a bunch of
        // notary change races. As such, if a reference state is added to a transaction which is assigned to a
        // different notary to the input and output states then all those inputs and outputs must be moved to the
        // notary which the reference state uses.
        //
        // If two or more reference states assigned to different notaries are added to a transaction then it follows
        // that this transaction likely _cannot_ be committed to the ledger as it unlikely that the party using the
        // reference state can change the assigned notary for one of the reference states.
        //
        // As such, if reference states assigned to multiple different notaries are added to a transaction builder
        // then the check below will fail.
        check(checkReferencesUseSameNotary()) {
            "Transactions with reference states using multiple different notaries are currently unsupported."
        }

        // State Pointers are recursively resolved. NOTE: That this might be expensive.
        // TODO: Add support for making recursive resolution optional if it becomes an issue.
        resolveStatePointers(stateAndRef.state)

        checkNotary(stateAndRef)
        references.add(stateAndRef.ref)
        checkForInputsAndReferencesOverlap()
    }

    /** Adds an input [StateRef] to the transaction. */
    open fun addInputState(stateAndRef: StateAndRef<*>) = apply {
        checkNotary(stateAndRef)
        inputs.add(stateAndRef.ref)
        inputsWithTransactionState.add(stateAndRef)
        resolveStatePointers(stateAndRef.state)
        return this
    }

    /** Adds an attachment with the specified hash to the TransactionBuilder. */
    fun addAttachment(attachmentId: SecureHash) = apply {
        attachments.add(attachmentId)
    }

    /** Adds an output state to the transaction. */
    fun addOutputState(state: TransactionState<*>) = apply {
        outputs.add(state)
        resolveStatePointers(state)
        return this
    }

    /** Adds an output state, with associated contract code (and constraints), and notary, to the transaction. */
    @JvmOverloads
    fun addOutputState(
            state: ContractState,
            contract: ContractClassName = requireNotNull(state.requiredContractClassName) {
                //TODO: add link to docsite page, when there is one.
                """
Unable to infer Contract class name because state class ${state::class.java.name} is not annotated with
@BelongsToContract, and does not have an enclosing class which implements Contract. Either annotate ${state::class.java.name}
with @BelongsToContract, or supply an explicit contract parameter to addOutputState().
""".trimIndent().replace('\n', ' ')
            },
            notary: Party, encumbrance: Int? = null,
            constraint: AttachmentConstraint = AutomaticPlaceholderConstraint
    ) = addOutputState(TransactionState(state, contract, notary, encumbrance, constraint))

    /** A default notary must be specified during builder construction to use this method */
    @JvmOverloads
    fun addOutputState(
            state: ContractState,
            contract: ContractClassName = requireNotNull(state.requiredContractClassName) {
                //TODO: add link to docsite page, when there is one.
                """
Unable to infer Contract class name because state class ${state::class.java.name} is not annotated with
@BelongsToContract, and does not have an enclosing class which implements Contract. Either annotate ${state::class.java.name}
with @BelongsToContract, or supply an explicit contract parameter to addOutputState().
""".trimIndent().replace('\n', ' ')
            },
            constraint: AttachmentConstraint = AutomaticPlaceholderConstraint
    ) = apply {
        checkNotNull(notary) {
            "Need to specify a notary for the state, or set a default one on TransactionBuilder initialisation"
        }
        addOutputState(state, contract, notary!!, constraint = constraint)
    }

    /** Adds a [Command] to the transaction. */
    fun addCommand(arg: Command<*>) = apply {
        commands.add(arg)
    }

    /**
     * Adds a [Command] to the transaction, specified by the encapsulated [CommandData] object and required list of
     * signing [PublicKey]s.
     */
    fun addCommand(data: CommandData, vararg keys: PublicKey) = addCommand(Command(data, listOf(*keys)))

    fun addCommand(data: CommandData, keys: List<PublicKey>) = addCommand(Command(data, keys))

    /**
     * Sets the [TimeWindow] for this transaction, replacing the existing [TimeWindow] if there is one. To be valid, the
     * transaction must then be signed by the notary service within this window of time. In this way, the notary acts as
     * the Timestamp Authority.
     */
    fun setTimeWindow(timeWindow: TimeWindow) = apply {
        check(notary != null) { "Only notarised transactions can have a time-window" }
        window = timeWindow
    }

    /**
     * The [TimeWindow] for the transaction can also be defined as [time] +/- [timeTolerance]. The tolerance should be
     * chosen such that your code can finish building the transaction and sending it to the Timestamp Authority within
     * that window of time, taking into account factors such as network latency. Transactions being built by a group of
     * collaborating parties may therefore require a higher time tolerance than a transaction being built by a single
     * node.
     */
    fun setTimeWindow(time: Instant, timeTolerance: Duration) = setTimeWindow(TimeWindow.withTolerance(time, timeTolerance))

    fun setPrivacySalt(privacySalt: PrivacySalt) = apply {
        this.privacySalt = privacySalt
    }

    /** Returns an immutable list of input [StateRef]s. */
    fun inputStates(): List<StateRef> = ArrayList(inputs)

    /** Returns an immutable list of reference input [StateRef]s. */
    fun referenceStates(): List<StateRef> = ArrayList(references)

    /** Returns an immutable list of attachment hashes. */
    fun attachments(): List<SecureHash> = ArrayList(attachments)

    /** Returns an immutable list of output [TransactionState]s. */
    fun outputStates(): List<TransactionState<*>> = ArrayList(outputs)

    /** Returns an immutable list of [Command]s. */
    fun commands(): List<Command<*>> = ArrayList(commands)

    /**
     * Sign the built transaction and return it. This is an internal function for use by the service hub, please use
     * [ServiceHub.signInitialTransaction] instead.
     */
    fun toSignedTransaction(keyManagementService: KeyManagementService,
                            publicKey: PublicKey,
                            signatureMetadata: SignatureMetadata,
                            services: ServicesForResolution): SignedTransaction {
        val wtx = toWireTransaction(services)
        val signableData = SignableData(wtx.id, signatureMetadata)
        val sig = keyManagementService.sign(signableData, publicKey)
        return SignedTransaction(wtx, listOf(sig))
    }
}

package net.corda.core.internal

import net.corda.core.DeleteForDJVM
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.*
import net.corda.core.contracts.TransactionVerificationException.TransactionContractConflictException
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.internal.rules.StateContractValidationEnforcementRule
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.contextLogger

@DeleteForDJVM
interface TransactionVerifierServiceInternal {
    /**
     * Verifies the [transaction] but adds some [extraAttachments] to the classpath.
     * Required for transactions built with Corda 3.x that might miss some dependencies due to a bug in that version.
     */
    fun verify(transaction: LedgerTransaction, extraAttachments: List<Attachment> ): CordaFuture<*>
}

/**
 * Defined here for visibility reasons.
 */
fun LedgerTransaction.prepareVerify(extraAttachments: List<Attachment>) = this.internalPrepareVerify(extraAttachments)

/**
 * Because we create a separate [LedgerTransaction] onto which we need to perform verification, it becomes important we don't verify the
 * wrong object instance. This class helps avoid that.
 */
class Verifier(val ltx: LedgerTransaction, val transactionClassLoader: ClassLoader, private val inputStatesContractClassNameToMaxVersion: Map<ContractClassName, Version>) {
    private val inputStates: List<TransactionState<*>> = ltx.inputs.map { it.state }
    private val allStates: List<TransactionState<*>> = inputStates + ltx.references.map { it.state } + ltx.outputs
    private val contractAttachmentsByContract: Map<ContractClassName, Set<ContractAttachment>> = getContractAttachmentsByContract()

    companion object {
        private val logger = contextLogger()
    }

    fun verify() {
        // checkNoNotaryChange and checkEncumbrancesValid are called here, and not in the c'tor, as they need access to the "outputs"
        // list, the contents of which need to be deserialized under the correct classloader.
        checkNoNotaryChange()
        checkEncumbrancesValid()
        validateContractVersions()
        validatePackageOwnership()
        validateStatesAgainstContract()
        val hashToSignatureConstrainedContracts = verifyConstraintsValidity()
        verifyConstraints(hashToSignatureConstrainedContracts)
        verifyContracts()
    }

    // TODO: revisit to include contract version information
    /**
     *  This method may return more than one attachment for a given contract class.
     *  Specifically, this is the case for transactions combining hash and signature constraints where the hash constrained contract jar
     *  will be unsigned, and the signature constrained counterpart will be signed.
     */
    private fun getContractAttachmentsByContract(): Map<ContractClassName, Set<ContractAttachment>> {
        val contractClasses = allStates.map { it.contract }.toSet()
        val result = mutableMapOf<ContractClassName, Set<ContractAttachment>>()

        for (attachment in ltx.attachments) {
            if (attachment !is ContractAttachment) continue
            for (contract in contractClasses) {
                if (contract !in attachment.allContracts) continue
                result[contract] = result.getOrDefault(contract, setOf(attachment)).plus(attachment)
            }
        }

        return result
    }

    /**
     * Make sure the notary has stayed the same. As we can't tell how inputs and outputs connect, if there
     * are any inputs or reference inputs, all outputs must have the same notary.
     *
     * TODO: Is that the correct set of restrictions? May need to come back to this, see if we can be more
     *       flexible on output notaries.
     */
    private fun checkNoNotaryChange() {
        if (ltx.notary != null && (ltx.inputs.isNotEmpty() || ltx.references.isNotEmpty())) {
            ltx.outputs.forEach {
                if (it.notary != ltx.notary) {
                    throw TransactionVerificationException.NotaryChangeInWrongTransactionType(ltx.id, ltx.notary, it.notary)
                }
            }
        }
    }

    private fun checkEncumbrancesValid() {
        // Validate that all encumbrances exist within the set of input states.
        ltx.inputs
                .filter { it.state.encumbrance != null }
                .forEach { (state, ref) -> checkInputEncumbranceStateExists(state, ref) }

        // Check that in the outputs,
        // a) an encumbered state does not refer to itself as the encumbrance
        // b) the number of outputs can contain the encumbrance
        // c) the bi-directionality (full cycle) property is satisfied
        // d) encumbered output states are assigned to the same notary.
        val statesAndEncumbrance = ltx.outputs
                .withIndex()
                .filter { it.value.encumbrance != null }
                .map { Pair(it.index, it.value.encumbrance!!) }
        if (!statesAndEncumbrance.isEmpty()) {
            checkBidirectionalOutputEncumbrances(statesAndEncumbrance)
            checkNotariesOutputEncumbrance(statesAndEncumbrance)
        }
    }

    private fun checkInputEncumbranceStateExists(state: TransactionState<ContractState>, ref: StateRef) {
        val encumbranceStateExists = ltx.inputs.any {
            it.ref.txhash == ref.txhash && it.ref.index == state.encumbrance
        }
        if (!encumbranceStateExists) {
            throw TransactionVerificationException.TransactionMissingEncumbranceException(
                    ltx.id,
                    state.encumbrance!!,
                    TransactionVerificationException.Direction.INPUT
            )
        }
    }

    // Using basic graph theory, a full cycle of encumbered (co-dependent) states should exist to achieve bi-directional
    // encumbrances. This property is important to ensure that no states involved in an encumbrance-relationship
    // can be spent on their own. Briefly, if any of the states is having more than one encumbrance references by
    // other states, a full cycle detection will fail. As a result, all of the encumbered states must be present
    // as "from" and "to" only once (or zero times if no encumbrance takes place). For instance,
    // a -> b
    // c -> b    and     a -> b
    // b -> a            b -> c
    // do not satisfy the bi-directionality (full cycle) property.
    //
    // In the first example "b" appears twice in encumbrance ("to") list and "c" exists in the encumbered ("from") list only.
    // Due the above, one could consume "a" and "b" in the same transaction and then, because "b" is already consumed, "c" cannot be spent.
    //
    // Similarly, the second example does not form a full cycle because "a" and "c" exist in one of the lists only.
    // As a result, one can consume "b" and "c" in the same transactions, which will make "a" impossible to be spent.
    //
    // On other hand the following are valid constructions:
    // a -> b            a -> c
    // b -> c    and     c -> b
    // c -> a            b -> a
    // and form a full cycle, meaning that the bi-directionality property is satisfied.
    private fun checkBidirectionalOutputEncumbrances(statesAndEncumbrance: List<Pair<Int, Int>>) {
        // [Set] of "from" (encumbered states).
        val encumberedSet = mutableSetOf<Int>()
        // [Set] of "to" (encumbrance states).
        val encumbranceSet = mutableSetOf<Int>()
        // Update both [Set]s.
        statesAndEncumbrance.forEach { (statePosition, encumbrance) ->
            // Check it does not refer to itself.
            if (statePosition == encumbrance || encumbrance >= ltx.outputs.size) {
                throw TransactionVerificationException.TransactionMissingEncumbranceException(
                        ltx.id,
                        encumbrance,
                        TransactionVerificationException.Direction.OUTPUT
                )
            } else {
                encumberedSet.add(statePosition) // Guaranteed to have unique elements.
                if (!encumbranceSet.add(encumbrance)) {
                    throw TransactionVerificationException.TransactionDuplicateEncumbranceException(ltx.id, encumbrance)
                }
            }
        }
        // At this stage we have ensured that "from" and "to" [Set]s are equal in size, but we should check their
        // elements do indeed match. If they don't match, we return their symmetric difference (disjunctive union).
        val symmetricDifference = (encumberedSet union encumbranceSet).subtract(encumberedSet intersect encumbranceSet)
        if (symmetricDifference.isNotEmpty()) {
            // At least one encumbered state is not in the [encumbranceSet] and vice versa.
            throw TransactionVerificationException.TransactionNonMatchingEncumbranceException(ltx.id, symmetricDifference)
        }
    }

    // Method to check if all encumbered states are assigned to the same notary Party.
    // This method should be invoked after [checkBidirectionalOutputEncumbrances], because it assumes that the
    // bi-directionality property is already satisfied.
    private fun checkNotariesOutputEncumbrance(statesAndEncumbrance: List<Pair<Int, Int>>) {
        // We only check for transactions in which notary is null (i.e., issuing transactions).
        // Note that if a notary is defined for a transaction, we already check if all outputs are assigned
        // to the same notary (transaction's notary) in [checkNoNotaryChange()].
        if (ltx.notary == null) {
            // indicesAlreadyChecked is used to bypass already checked indices and to avoid cycles.
            val indicesAlreadyChecked = HashSet<Int>()
            statesAndEncumbrance.forEach {
                checkNotary(it.first, indicesAlreadyChecked)
            }
        }
    }

    private tailrec fun checkNotary(index: Int, indicesAlreadyChecked: HashSet<Int>) {
        if (indicesAlreadyChecked.add(index)) {
            val encumbranceIndex = ltx.outputs[index].encumbrance!!
            if (ltx.outputs[index].notary != ltx.outputs[encumbranceIndex].notary) {
                throw TransactionVerificationException.TransactionNotaryMismatchEncumbranceException(
                        ltx.id,
                        index,
                        encumbranceIndex,
                        ltx.outputs[index].notary,
                        ltx.outputs[encumbranceIndex].notary
                )
            } else {
                checkNotary(encumbranceIndex, indicesAlreadyChecked)
            }
        }
    }

    /**
     * Verify that contract class versions of output states are not lower that versions of relevant input states.
     */
    private fun validateContractVersions() {
        contractAttachmentsByContract.forEach { contractClassName, attachments ->
            val outputVersion = attachments.signed?.version ?: attachments.unsigned?.version ?: CordappImpl.DEFAULT_CORDAPP_VERSION
            inputStatesContractClassNameToMaxVersion[contractClassName]?.let {
                if (it > outputVersion) {
                    throw TransactionVerificationException.TransactionVerificationVersionException(ltx.id, contractClassName, "$it", "$outputVersion")
                }
            }
        }
    }

    /**
     * Verify that for each contract the network wide package owner is respected.
     *
     * TODO - revisit once transaction contains network parameters. - UPDATE: It contains them, but because of the API stability and the fact that
     *  LedgerTransaction was data class i.e. exposed constructors that shouldn't had been exposed, we still need to keep them nullable :/
     */
    private fun validatePackageOwnership() {
        val contractsAndOwners = allStates.mapNotNull { transactionState ->
            val contractClassName = transactionState.contract
            ltx.networkParameters!!.getPackageOwnerOf(contractClassName)?.let { contractClassName to it }
        }.toMap()

        contractsAndOwners.forEach { contract, owner ->
            contractAttachmentsByContract[contract]?.filter { it.isSigned }?.forEach { attachment ->
                if (!owner.isFulfilledBy(attachment.signerKeys))
                    throw TransactionVerificationException.ContractAttachmentNotSignedByPackageOwnerException(ltx.id, attachment.id, contract)
            } ?: throw TransactionVerificationException.ContractAttachmentNotSignedByPackageOwnerException(ltx.id, ltx.id, contract)
        }
    }

    /**
     * For all input and output [TransactionState]s, validates that the wrapped [ContractState] matches up with the
     * wrapped [Contract], as declared by the [BelongsToContract] annotation on the [ContractState]'s class.
     *
     * If the target platform version of the current CorDapp is lower than 4.0, a warning will be written to the log
     * if any mismatch is detected. If it is 4.0 or later, then [TransactionContractConflictException] will be thrown.
     */
    private fun validateStatesAgainstContract() = allStates.forEach(::validateStateAgainstContract)

    private fun validateStateAgainstContract(state: TransactionState<ContractState>) {
        val shouldEnforce = StateContractValidationEnforcementRule.shouldEnforce(state.data)

        val requiredContractClassName = state.data.requiredContractClassName
                ?: if (shouldEnforce) throw TransactionVerificationException.TransactionRequiredContractUnspecifiedException(ltx.id, state) else return

        if (state.contract != requiredContractClassName)
            if (shouldEnforce) {
                throw TransactionContractConflictException(ltx.id, state, requiredContractClassName)
            } else {
                logger.warnOnce("""
                            State of class ${state.data::class.java.typeName} belongs to contract $requiredContractClassName, but
                            is bundled in TransactionState with ${state.contract}.

                            For details see: https://docs.corda.net/api-contract-constraints.html#contract-state-agreement
                            """.trimIndent().replace('\n', ' '))
            }
    }

    /**
     * Enforces the validity of the actual constraints.
     * * Constraints should be one of the valid supported ones.
     * * Constraints should propagate correctly if not marked otherwise.
     *
     * Returns set of contract classes that identify hash -> signature constraint switchover
     */
    private fun verifyConstraintsValidity(): MutableSet<ContractClassName> {
        // First check that the constraints are valid.
        for (state in allStates) {
            checkConstraintValidity(state)
        }

        // Group the inputs and outputs by contract, and for each contract verify the constraints propagation logic.
        // This is not required for reference states as there is nothing to propagate.
        val inputContractGroups = ltx.inputs.groupBy { it.state.contract }
        val outputContractGroups = ltx.outputs.groupBy { it.contract }

        // identify any contract classes where input-output pair are transitioning from hash to signature constraints.
        val hashToSignatureConstrainedContracts = mutableSetOf<ContractClassName>()

        for (contractClassName in (inputContractGroups.keys + outputContractGroups.keys)) {
            if (contractClassName.contractHasAutomaticConstraintPropagation(transactionClassLoader)) {
                // Verify that the constraints of output states have at least the same level of restriction as the constraints of the
                // corresponding input states.
                val inputConstraints = inputContractGroups[contractClassName]?.map { it.state.constraint }?.toSet()
                val outputConstraints = outputContractGroups[contractClassName]?.map { it.constraint }?.toSet()
                outputConstraints?.forEach { outputConstraint ->
                    inputConstraints?.forEach { inputConstraint ->
                        val constraintAttachment = resolveAttachment(contractClassName)
                        if (!(outputConstraint.canBeTransitionedFrom(inputConstraint, constraintAttachment))) {
                            throw TransactionVerificationException.ConstraintPropagationRejection(
                                    ltx.id,
                                    contractClassName,
                                    inputConstraint,
                                    outputConstraint
                            )
                        }
                        // Hash to signature constraints auto-migration
                        if (outputConstraint is SignatureAttachmentConstraint && inputConstraint is HashAttachmentConstraint)
                            hashToSignatureConstrainedContracts.add(contractClassName)
                    }
                }
            } else {
                contractClassName.warnContractWithoutConstraintPropagation()
            }
        }
        return hashToSignatureConstrainedContracts
    }

    private fun resolveAttachment(contractClassName: ContractClassName): AttachmentWithContext {
        val unsignedAttachment = contractAttachmentsByContract[contractClassName]!!.firstOrNull { !it.isSigned }
        val signedAttachment = contractAttachmentsByContract[contractClassName]!!.firstOrNull { it.isSigned }
        return when {
            (unsignedAttachment != null && signedAttachment != null) -> AttachmentWithContext(signedAttachment, contractClassName, ltx.networkParameters!!)
            (unsignedAttachment != null) -> AttachmentWithContext(unsignedAttachment, contractClassName, ltx.networkParameters!!)
            (signedAttachment != null) -> AttachmentWithContext(signedAttachment, contractClassName, ltx.networkParameters!!)
            else -> throw TransactionVerificationException.ContractConstraintRejection(ltx.id, contractClassName)
        }
    }

    /**
     * Verify that all contract constraints are passing before running any contract code.
     *
     * This check is running the [AttachmentConstraint.isSatisfiedBy] method for each corresponding [ContractAttachment].
     *
     * @throws TransactionVerificationException if the constraints fail to verify
     */
    private fun verifyConstraints(hashToSignatureConstrainedContracts: MutableSet<ContractClassName>) {
        for (state in allStates) {
            if (state.constraint is SignatureAttachmentConstraint) {
                checkMinimumPlatformVersion(ltx.networkParameters!!.minimumPlatformVersion, 4, "Signature constraints")
            }

            val constraintAttachment = if (state.contract in hashToSignatureConstrainedContracts) {
                // hash to to signature constraint migration logic:
                // pass the unsigned attachment when verifying the constraint of the input state, and the signed attachment when verifying
                // the constraint of the output state.
                val unsignedAttachment = contractAttachmentsByContract[state.contract].unsigned
                        ?: throw TransactionVerificationException.MissingAttachmentRejection(ltx.id, state.contract)
                val signedAttachment = contractAttachmentsByContract[state.contract].signed
                        ?: throw TransactionVerificationException.MissingAttachmentRejection(ltx.id, state.contract)
                when {
                    // use unsigned attachment if hash-constrained input state
                    state.data in ltx.inputStates -> AttachmentWithContext(unsignedAttachment, state.contract, ltx.networkParameters!!)
                    // use signed attachment if signature-constrained output state
                    state.data in ltx.outputStates -> AttachmentWithContext(signedAttachment, state.contract, ltx.networkParameters!!)
                    else -> throw IllegalStateException("${state.contract} must use either signed or unsigned attachment in hash to signature constraints migration")
                }
            } else {
                // standard processing logic
                val contractAttachment = contractAttachmentsByContract[state.contract]?.firstOrNull()
                        ?: throw TransactionVerificationException.MissingAttachmentRejection(ltx.id, state.contract)
                AttachmentWithContext(contractAttachment, state.contract, ltx.networkParameters!!)
            }

            if (!state.constraint.isSatisfiedBy(constraintAttachment)) {
                throw TransactionVerificationException.ContractConstraintRejection(ltx.id, state.contract)
            }
        }
    }

    /**
     * Check the transaction is contract-valid by running the verify() for each input and output state contract.
     * If any contract fails to verify, the whole transaction is considered to be invalid.
     */
    private fun verifyContracts() {
        val contractClasses = (inputStates + ltx.outputs).toSet()
                .map { it.contract to contractClassFor(it.contract, it.data.javaClass.classLoader) }

        val contractInstances = contractClasses.map { (contractClassName, contractClass) ->
            try {
                contractClass.newInstance()
            } catch (e: Exception) {
                throw TransactionVerificationException.ContractCreationError(ltx.id, contractClassName, e)
            }
        }

        contractInstances.forEach { contract ->
            try {
                contract.verify(ltx)
            } catch (e: Exception) {
                throw TransactionVerificationException.ContractRejection(ltx.id, contract, e)
            }
        }
    }

    private fun contractClassFor(className: ContractClassName, classLoader: ClassLoader): Class<out Contract> {
        return try {
            classLoader.loadClass(className).asSubclass(Contract::class.java)
        } catch (e: Exception) {
            throw TransactionVerificationException.ContractCreationError(ltx.id, className, e)
        }
    }

    private val Set<ContractAttachment>?.unsigned: ContractAttachment? get() = this?.firstOrNull { !it.isSigned }
    private val Set<ContractAttachment>?.signed: ContractAttachment? get() = this?.firstOrNull { it.isSigned }
}

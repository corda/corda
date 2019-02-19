package net.corda.core.internal

import net.corda.core.DeleteForDJVM
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.*
import net.corda.core.contracts.TransactionVerificationException.TransactionContractConflictException
import net.corda.core.internal.rules.StateContractValidationEnforcementRule
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.contextLogger

@DeleteForDJVM
interface TransactionVerifierServiceInternal {
    /**
     * Verifies the [transaction] but adds some [extraAttachments] to the classpath.
     * Required for transactions built with Corda 3.x that might miss some dependencies due to a bug in that version.
     */
    fun verify(transaction: LedgerTransaction, extraAttachments: List<Attachment>): CordaFuture<*>
}

/**
 * Defined here for visibility reasons.
 */
fun LedgerTransaction.prepareVerify(extraAttachments: List<Attachment>) = this.internalPrepareVerify(extraAttachments)

/**
 * Because we create a separate [LedgerTransaction] onto which we need to perform verification, it becomes important we don't verify the
 * wrong object instance. This class helps avoid that.
 *
 * @param inputVersions A map linking each contract class name to the advertised version of the JAR that defines it. Used for downgrade protection.
 */
class Verifier(val ltx: LedgerTransaction, private val transactionClassLoader: ClassLoader) {
    private val inputStates: List<TransactionState<*>> = ltx.inputs.map { it.state }
    private val allStates: List<TransactionState<*>> = inputStates + ltx.references.map { it.state } + ltx.outputs

    companion object {
        private val logger = contextLogger()
    }

    /**
     * This function is where the validity of transactions is determined.
     *
     * It is a critical piece of the security of the platform.
     *
     * @throws TransactionVerificationException
     */
    fun verify() {
        // checkNoNotaryChange and checkEncumbrancesValid are called here, and not in the c'tor, as they need access to the "outputs"
        // list, the contents of which need to be deserialized under the correct classloader.
        checkNoNotaryChange()
        checkEncumbrancesValid()

        // The following checks ensure the integrity of the current transaction and also of the future chain.
        // See: https://docs.corda.net/head/api-contract-constraints.html
        // A transaction contains both the data and the code that must be executed to validate the transition of the data.
        // Transactions can be created by malicious adversaries, who can try to use code that allows them to create transactions that appear valid but are not.

        // 1. Check that there is one and only one attachment for each relevant contract.
        val contractAttachmentsByContract = getUniqueContractAttachmentsByContract()

        // 2. Check that the attachments satisfy the constraints of the states. (The contract verification code is correct.)
        verifyConstraints(contractAttachmentsByContract)

        // 3. Check that the actual state constraints are correct. This is necessary because transactions can be built by potentially malicious nodes
        // who can create output states with a weaker constraint which can be exploited in a future transaction.
        verifyConstraintsValidity(contractAttachmentsByContract)

        // 4. Check that the [TransactionState] objects are correctly formed.
        validateStatesAgainstContract()

        // 5. Final step is to run the contract code. After the first 4 steps we are now sure that we are running the correct code.
        verifyContracts()
    }

    /**
     *  This method returns the attachment with the code for each contract.
     *  It makes sure there is one and only one.
     *  This is an important piece of the security of transactions.
     */
    private fun getUniqueContractAttachmentsByContract(): Map<ContractClassName, ContractAttachment> {
        val contractClasses = allStates.map { it.contract }.toSet()

        // Check that there are no duplicate attachments added.
        if (ltx.attachments.size != ltx.attachments.toSet().size) throw TransactionVerificationException.DuplicateAttachmentsRejection(ltx.id, ltx.attachments.groupBy { it }.filterValues { it.size > 1 }.keys.first())

        // For each attachment this finds all the relevant state contracts that it provides.
        // And then maps them to the attachment.
        val contractAttachmentsPerContract: List<Pair<ContractClassName, ContractAttachment>> = ltx.attachments
                .mapNotNull { it as? ContractAttachment } // only contract attachments are relevant.
                .flatMap { attachment ->
                    // Find which relevant contracts are present in the current attachment and return them as a list
                    contractClasses
                            .filter { it in attachment.allContracts }
                            .map { it to attachment }
                }

        // It is forbidden to add multiple attachments for the same contract.
        val contractWithMultipleAttachments = contractAttachmentsPerContract
                .groupBy { it.first }  // Group by contract.
                .filter { (_, attachments) -> attachments.size > 1 } // And only keep contracts that are in multiple attachments. It's guaranteed that attachments were unique by a previous check.
                .keys.firstOrNull() // keep the first one - if any - to throw a meaningful exception.
        if (contractWithMultipleAttachments != null) throw TransactionVerificationException.ConflictingAttachmentsRejection(ltx.id, contractWithMultipleAttachments)

        val result = contractAttachmentsPerContract.toMap()

        // Check that there is an attachment for each contract.
        if (result.keys != contractClasses) throw TransactionVerificationException.MissingAttachmentRejection(ltx.id, contractClasses.minus(result.keys).first())

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
     * For all input, output and reference [TransactionState]s, validates that the wrapped [ContractState] matches up with the
     * wrapped [Contract], as declared by the [BelongsToContract] annotation on the [ContractState]'s class.
     *
     * If the target platform version of the current CorDapp is lower than 4.0, a warning will be written to the log
     * if any mismatch is detected. If it is 4.0 or later, then [TransactionContractConflictException] will be thrown.
     *
     * Note: It should be enough to run this check only on the output states. Even more, it could be run only on distinct output contractClass/stateClass pairs.
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
     * Enforces the validity of the actual constraints of the output states.
     * - Constraints should be one of the valid supported ones.
     * - Constraints should propagate correctly if not marked otherwise (in that case it is the responsibility of the contract to ensure that the output states are created properly).
     */
    private fun verifyConstraintsValidity(contractAttachmentsByContract: Map<ContractClassName, ContractAttachment>) {

        // First check that the constraints are valid.
        for (state in allStates) {
            checkConstraintValidity(state)
        }

        // Group the inputs and outputs by contract, and for each contract verify the constraints propagation logic.
        // This is not required for reference states as there is nothing to propagate.
        val inputContractGroups = ltx.inputs.groupBy { it.state.contract }
        val outputContractGroups = ltx.outputs.groupBy { it.contract }

        for (contractClassName in (inputContractGroups.keys + outputContractGroups.keys)) {

            if (!contractClassName.contractHasAutomaticConstraintPropagation(transactionClassLoader)) {
                contractClassName.warnContractWithoutConstraintPropagation()
                continue
            }

            val contractAttachment = contractAttachmentsByContract[contractClassName]!!

            // Verify that the constraints of output states have at least the same level of restriction as the constraints of the
            // corresponding input states.
            val inputConstraints = (inputContractGroups[contractClassName] ?: emptyList()).map { it.state.constraint }.toSet()
            val outputConstraints = (outputContractGroups[contractClassName] ?: emptyList()).map { it.constraint }.toSet()

            outputConstraints.forEach { outputConstraint ->
                inputConstraints.forEach { inputConstraint ->
                    if (!(outputConstraint.canBeTransitionedFrom(inputConstraint, contractAttachment))) {
                        throw TransactionVerificationException.ConstraintPropagationRejection(
                                ltx.id,
                                contractClassName,
                                inputConstraint,
                                outputConstraint)
                    }
                }
            }
        }
    }

    /**
     * Verify that all contract constraints are passing before running any contract code.
     *
     * This check is running the [AttachmentConstraint.isSatisfiedBy] method for each corresponding [ContractAttachment].
     *
     * @throws TransactionVerificationException if the constraints fail to verify
     */
    private fun verifyConstraints(contractAttachmentsByContract: Map<ContractClassName, ContractAttachment>) {
        // For each contract/constraint pair check that the relevant attachment is valid.
        allStates.map { it.contract to it.constraint }.toSet().forEach { (contract, constraint) ->
            if (constraint is SignatureAttachmentConstraint)
                checkMinimumPlatformVersion(ltx.networkParameters?.minimumPlatformVersion ?: 1, 4, "Signature constraints")

            // We already checked that there is one and only one attachment.
            val contractAttachment = contractAttachmentsByContract[contract]!!

            val constraintAttachment = AttachmentWithContext(contractAttachment, contract, ltx.networkParameters!!.whitelistedContractImplementations)

            if (HashAttachmentConstraint.disableHashConstraints && constraint is HashAttachmentConstraint)
                logger.warnOnce("Skipping hash constraints verification.")
            else if (!constraint.isSatisfiedBy(constraintAttachment))
                throw TransactionVerificationException.ContractConstraintRejection(ltx.id, contract)
        }
    }

    /**
     * Check the transaction is contract-valid by running the verify() for each input and output state contract.
     * If any contract fails to verify, the whole transaction is considered to be invalid.
     *
     * Note: Reference states are not verified.
     */
    private fun verifyContracts() {

        // Loads the contract class from the transactionClassLoader.
        fun contractClassFor(className: ContractClassName) = try {
            transactionClassLoader.loadClass(className).asSubclass(Contract::class.java)
        } catch (e: Exception) {
            throw TransactionVerificationException.ContractCreationError(ltx.id, className, e)
        }

        val contractClasses: Map<ContractClassName, Class<out Contract>> = (inputStates + ltx.outputs)
                .map { it.contract }
                .toSet()
                .map { contract -> contract to contractClassFor(contract) }
                .toMap()

        val contractInstances: List<Contract> = contractClasses.map { (contractClassName, contractClass) ->
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
                logger.error("Error validating transaction ${ltx.id}.", e)
                throw TransactionVerificationException.ContractRejection(ltx.id, contract, e)
            }
        }
    }
}

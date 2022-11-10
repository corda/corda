package net.corda.core.internal

import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.HashAttachmentConstraint
import net.corda.core.contracts.SignatureAttachmentConstraint
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.contracts.TransactionVerificationException.ConflictingAttachmentsRejection
import net.corda.core.contracts.TransactionVerificationException.ConstraintPropagationRejection
import net.corda.core.contracts.TransactionVerificationException.ContractConstraintRejection
import net.corda.core.contracts.TransactionVerificationException.ContractCreationError
import net.corda.core.contracts.TransactionVerificationException.ContractRejection
import net.corda.core.contracts.TransactionVerificationException.Direction
import net.corda.core.contracts.TransactionVerificationException.DuplicateAttachmentsRejection
import net.corda.core.contracts.TransactionVerificationException.InvalidConstraintRejection
import net.corda.core.contracts.TransactionVerificationException.MissingAttachmentRejection
import net.corda.core.contracts.TransactionVerificationException.NotaryChangeInWrongTransactionType
import net.corda.core.contracts.TransactionVerificationException.TransactionContractConflictException
import net.corda.core.contracts.TransactionVerificationException.TransactionDuplicateEncumbranceException
import net.corda.core.contracts.TransactionVerificationException.TransactionMissingEncumbranceException
import net.corda.core.contracts.TransactionVerificationException.TransactionNonMatchingEncumbranceException
import net.corda.core.contracts.TransactionVerificationException.TransactionNotaryMismatchEncumbranceException
import net.corda.core.contracts.TransactionVerificationException.TransactionRequiredContractUnspecifiedException
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.rules.StateContractValidationEnforcementRule
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.loggerFor
import java.util.function.Function
import java.util.function.Supplier

@DeleteForDJVM
interface TransactionVerifierServiceInternal {
    fun reverifyWithFixups(transaction: LedgerTransaction, missingClass: String?): CordaFuture<*>
}

/**
 * Defined here for visibility reasons.
 */
fun LedgerTransaction.prepareVerify(attachments: List<Attachment>) = internalPrepareVerify(attachments)

interface Verifier {

    /**
     * Placeholder function for the verification logic.
     */
    fun verify()
}

// This class allows us unit-test transaction verification more easily.
abstract class AbstractVerifier(
    protected val ltx: LedgerTransaction,
    protected val transactionClassLoader: ClassLoader
) : Verifier {
    protected abstract val transaction: Supplier<LedgerTransaction>

    protected companion object {
        @JvmField
        val logger = loggerFor<Verifier>()
    }

    /**
     * Check that the transaction is internally consistent, and then check that it is
     * contract-valid by running verify() for each input and output state contract.
     * If any contract fails to verify, the whole transaction is considered to be invalid.
     *
     * Note: Reference states are not verified.
     */
    final override fun verify() {
        try {
            TransactionVerifier(transactionClassLoader).apply(transaction)
        } catch (e: TransactionVerificationException) {
            logger.error("Error validating transaction ${ltx.id}.", e.cause)
            throw e
        }
    }
}

/**
 * Because we create a separate [LedgerTransaction] onto which we need to perform verification, it becomes important we don't verify the
 * wrong object instance. This class helps avoid that.
 */
@KeepForDJVM
private class Validator(private val ltx: LedgerTransaction, private val transactionClassLoader: ClassLoader) {
    private val inputStates: List<TransactionState<*>> = ltx.inputs.map(StateAndRef<ContractState>::state)
    private val allStates: List<TransactionState<*>> = inputStates + ltx.references.map(StateAndRef<ContractState>::state) + ltx.outputs

    private companion object {
        private val logger = loggerFor<Validator>()
    }

    /**
     * This function is where the validity of transactions is determined.
     *
     * It is a critical piece of the security of the platform.
     *
     * @throws net.corda.core.contracts.TransactionVerificationException
     */
    fun validate() {
        // checkNoNotaryChange and checkEncumbrancesValid are called here, and not in the c'tor, as they need access to the "outputs"
        // list, the contents of which need to be deserialized under the correct classloader.
        checkNoNotaryChange()
        checkEncumbrancesValid()
        ltx.checkSupportedHashType()
        checkTransactionWithTimeWindowIsNotarised()
        checkNotaryWhitelisted(ltx)

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

        // 5. Final step will be to run the contract code.
    }

    private fun checkTransactionWithTimeWindowIsNotarised() {
        if (ltx.timeWindow != null) check(ltx.notary != null) { "Transactions with time-windows must be notarised" }
    }

    /**
     *  This method returns the attachment with the code for each contract.
     *  It makes sure there is one and only one.
     *  This is an important piece of the security of transactions.
     */
    @Suppress("ThrowsCount")
    private fun getUniqueContractAttachmentsByContract(): Map<ContractClassName, ContractAttachment> {
        val contractClasses = allStates.mapTo(LinkedHashSet(), TransactionState<*>::contract)

        // Check that there are no duplicate attachments added.
        if (ltx.attachments.size != ltx.attachments.toSet().size) throw DuplicateAttachmentsRejection(ltx.id, ltx.attachments.groupBy { it }.filterValues { it.size > 1 }.keys.first())

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
        if (contractWithMultipleAttachments != null) throw ConflictingAttachmentsRejection(ltx.id, contractWithMultipleAttachments)

        val result = contractAttachmentsPerContract.toMap()

        // Check that there is an attachment for each contract.
        if (result.keys != contractClasses) throw MissingAttachmentRejection(ltx.id, contractClasses.minus(result.keys).first())

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
                    throw NotaryChangeInWrongTransactionType(ltx.id, ltx.notary, it.notary)
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
        if (statesAndEncumbrance.isNotEmpty()) {
            checkBidirectionalOutputEncumbrances(statesAndEncumbrance)
            checkNotariesOutputEncumbrance(statesAndEncumbrance)
        }
    }

    private fun checkInputEncumbranceStateExists(state: TransactionState<ContractState>, ref: StateRef) {
        val encumbranceStateExists = ltx.inputs.any {
            it.ref.txhash == ref.txhash && it.ref.index == state.encumbrance
        }
        if (!encumbranceStateExists) {
            throw TransactionMissingEncumbranceException(
                    ltx.id,
                    state.encumbrance!!,
                    Direction.INPUT
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
    @Suppress("ThrowsCount")
    private fun checkBidirectionalOutputEncumbrances(statesAndEncumbrance: List<Pair<Int, Int>>) {
        // [Set] of "from" (encumbered states).
        val encumberedSet = mutableSetOf<Int>()
        // [Set] of "to" (encumbrance states).
        val encumbranceSet = mutableSetOf<Int>()
        // Update both [Set]s.
        statesAndEncumbrance.forEach { (statePosition, encumbrance) ->
            // Check it does not refer to itself.
            if (statePosition == encumbrance || encumbrance >= ltx.outputs.size) {
                throw TransactionMissingEncumbranceException(
                        ltx.id,
                        encumbrance,
                        Direction.OUTPUT
                )
            } else {
                encumberedSet.add(statePosition) // Guaranteed to have unique elements.
                if (!encumbranceSet.add(encumbrance)) {
                    throw TransactionDuplicateEncumbranceException(ltx.id, encumbrance)
                }
            }
        }
        // At this stage we have ensured that "from" and "to" [Set]s are equal in size, but we should check their
        // elements do indeed match. If they don't match, we return their symmetric difference (disjunctive union).
        val symmetricDifference = (encumberedSet union encumbranceSet).subtract(encumberedSet intersect encumbranceSet)
        if (symmetricDifference.isNotEmpty()) {
            // At least one encumbered state is not in the [encumbranceSet] and vice versa.
            throw TransactionNonMatchingEncumbranceException(ltx.id, symmetricDifference)
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
                throw TransactionNotaryMismatchEncumbranceException(
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
                ?: if (shouldEnforce) throw TransactionRequiredContractUnspecifiedException(ltx.id, state) else return

        if (state.contract != requiredContractClassName)
            if (shouldEnforce) {
                throw TransactionContractConflictException(ltx.id, state, requiredContractClassName)
            } else {
                logger.warnOnce("""
                            State of class ${state.data::class.java.typeName} belongs to contract $requiredContractClassName, but
                            is bundled in TransactionState with ${state.contract}.
                            """.trimIndent().replace('\n', ' '))
            }
    }

    /**
     * Enforces the validity of the actual constraints of the output states.
     * - Constraints should be one of the valid supported ones.
     * - Constraints should propagate correctly if not marked otherwise (in that case it is the responsibility of the contract to ensure that the output states are created properly).
     */
    @Suppress("NestedBlockDepth")
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
                contractClassName.warnContractWithoutConstraintPropagation(transactionClassLoader)
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
                        throw ConstraintPropagationRejection(
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
    @Suppress("NestedBlockDepth", "MagicNumber")
    private fun verifyConstraints(contractAttachmentsByContract: Map<ContractClassName, ContractAttachment>) {
        // For each contract/constraint pair check that the relevant attachment is valid.
        allStates.mapTo(LinkedHashSet()) { it.contract to it.constraint }.forEach { (contract, constraint) ->
            if (constraint is SignatureAttachmentConstraint) {
                /**
                 * Support for signature constraints has been added on
                 * min. platform version >= [PlatformVersionSwitches.MIGRATE_ATTACHMENT_TO_SIGNATURE_CONSTRAINTS].
                 * On minimum platform version >= [PlatformVersionSwitches.LIMIT_KEYS_IN_SIGNATURE_CONSTRAINTS], an explicit check has
                 * been introduced on the supported number of leaf keys in composite keys of signature constraints in
                 * order to harden consensus.
                 */
                checkMinimumPlatformVersion(
                        ltx.networkParameters?.minimumPlatformVersion ?: PlatformVersionSwitches.FIRST_VERSION,
                        PlatformVersionSwitches.MIGRATE_ATTACHMENT_TO_SIGNATURE_CONSTRAINTS,
                        "Signature constraints"
                )
                val constraintKey = constraint.key
                if ((ltx.networkParameters?.minimumPlatformVersion ?: 1) >= PlatformVersionSwitches.LIMIT_KEYS_IN_SIGNATURE_CONSTRAINTS) {
                    if (constraintKey is CompositeKey && constraintKey.leafKeys.size > MAX_NUMBER_OF_KEYS_IN_SIGNATURE_CONSTRAINT) {
                        throw InvalidConstraintRejection(ltx.id, contract,
                                "Signature constraint contains composite key with ${constraintKey.leafKeys.size} leaf keys, " +
                                        "which is more than the maximum allowed number of keys " +
                                        "($MAX_NUMBER_OF_KEYS_IN_SIGNATURE_CONSTRAINT).")
                    }
                }
            }

            // We already checked that there is one and only one attachment.
            val contractAttachment = contractAttachmentsByContract[contract]!!

            val constraintAttachment = AttachmentWithContext(contractAttachment, contract, ltx.networkParameters!!.whitelistedContractImplementations)

            if (HashAttachmentConstraint.disableHashConstraints && constraint is HashAttachmentConstraint)
                logger.warnOnce("Skipping hash constraints verification.")
            else if (!constraint.isSatisfiedBy(constraintAttachment))
                throw ContractConstraintRejection(ltx.id, contract)
        }
    }
}

/**
 * Verify the given [LedgerTransaction]. This includes validating
 * its contents, as well as executing all of its smart contracts.
 */
@Suppress("TooGenericExceptionCaught")
@KeepForDJVM
class TransactionVerifier(private val transactionClassLoader: ClassLoader) : Function<Supplier<LedgerTransaction>, Unit> {
    // This constructor is used inside the DJVM's sandbox.
    @Suppress("unused")
    constructor() : this(ClassLoader.getSystemClassLoader())

    // Loads the contract class from the transactionClassLoader.
    private fun createContractClass(id: SecureHash, contractClassName: ContractClassName): Class<out Contract> {
        return try {
            Class.forName(contractClassName, false, transactionClassLoader).asSubclass(Contract::class.java)
        } catch (e: Exception) {
            throw ContractCreationError(id, contractClassName, e)
        }
    }

    private fun generateContracts(ltx: LedgerTransaction): List<Contract> {
        return (ltx.inputs.map(StateAndRef<ContractState>::state) + ltx.outputs)
            .mapTo(LinkedHashSet(), TransactionState<*>::contract)
            .map { contractClassName ->
                createContractClass(ltx.id, contractClassName)
            }.map { contractClass ->
                try {
                    /**
                     * This function must execute within the DJVM's sandbox, which does not
                     * permit user code to invoke [java.lang.reflect.Constructor.newInstance].
                     * (This would be fixable now, provided the constructor is public.)
                     *
                     * [Class.newInstance] is deprecated as of Java 9.
                     */
                    @Suppress("deprecation")
                    contractClass.newInstance()
                } catch (e: Exception) {
                    throw ContractCreationError(ltx.id, contractClass.name, e)
                }
            }
    }

    private fun validateTransaction(ltx: LedgerTransaction) {
        Validator(ltx, transactionClassLoader).validate()
    }

    override fun apply(transactionFactory: Supplier<LedgerTransaction>) {
        var firstLtx: LedgerTransaction? = null

        transactionFactory.get().let { ltx ->
            firstLtx = ltx

            /**
             * Check that this transaction is correctly formed.
             * We only need to run these checks once.
             */
            validateTransaction(ltx)

            /**
             * Generate the list of unique contracts
             * within this transaction.
             */
            generateContracts(ltx)
        }.forEach { contract ->
            val ltx = firstLtx ?: transactionFactory.get()
            firstLtx = null
            try {
                // Final step is to run the contract code. Having validated the
                // transaction, we are now sure that we are running the correct code.
                contract.verify(ltx)
            } catch (e: Exception) {
                throw ContractRejection(ltx.id, contract, e)
            }
        }
    }
}

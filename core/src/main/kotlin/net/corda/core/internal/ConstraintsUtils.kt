package net.corda.core.internal

import net.corda.core.contracts.*
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.crypto.keys
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.utilities.loggerFor

/**
 * Contract version and flow versions are integers.
 */
typealias Version = Int

private val log = loggerFor<AttachmentConstraint>()

val Attachment.contractVersion: Version get() = if (this is ContractAttachment) version else CordappImpl.DEFAULT_CORDAPP_VERSION

/**
 * Obtain the typename of the required [ContractClass] associated with the target [ContractState], using the
 * [BelongsToContract] annotation by default, but falling through to checking the state's enclosing class if there is
 * one and it inherits from [Contract].
 */
val ContractState.requiredContractClassName: String? get() {
    val annotation = javaClass.getAnnotation(BelongsToContract::class.java)
    if (annotation != null) {
        return annotation.value.java.typeName
    }
    val enclosingClass = javaClass.enclosingClass ?: return null
    return if (Contract::class.java.isAssignableFrom(enclosingClass)) enclosingClass.typeName else null
}

/**
 * This method will be used in conjunction with [NoConstraintPropagation]. It is run during transaction verification when the contract is not
 * annotated with [NoConstraintPropagation]. When constraints propagation is enabled, constraints set on output states need to follow certain
 * rules with regards to constraints of input states.
 *
 * Rules:
 *
 *  * It is allowed for output states to inherit the exact same constraint as the input states.
 *
 *  * The [AlwaysAcceptAttachmentConstraint] is not allowed to transition to a different constraint, as that could be used to hide malicious
 *    behaviour.
 *
 *  * Anything (except the [AlwaysAcceptAttachmentConstraint]) can be transitioned to a [HashAttachmentConstraint].
 *
 *  * You can transition from the [WhitelistedByZoneAttachmentConstraint] to the [SignatureAttachmentConstraint] only if all signers of the
 *    JAR are required to sign in the future.
 *
 *  * You can transition from a [HashAttachmentConstraint] to a [SignatureAttachmentConstraint] when the following conditions are met:
 *
 *    1. Jar contents (per entry, by hashcode) of both original (unsigned) and signed contract jars are identical
 *       Note: this step is enforced in the [AttachmentsClassLoader] no overlap rule checking.
 *
 *    2. Java package namespace of signed contract jar is registered in the CZ network map with same public keys (as used to sign contract jar)
 */
// TODO - SignatureConstraint third party signers.
fun AttachmentConstraint.canBeTransitionedFrom(input: AttachmentConstraint, attachment: AttachmentWithContext): Boolean {
    val output = this
    return when {
        // These branches should not happen, as this has been already checked.
        input is AutomaticPlaceholderConstraint || output is AutomaticPlaceholderConstraint -> throw IllegalArgumentException("Illegal constraint: AutomaticPlaceholderConstraint.")
        input is AutomaticHashConstraint || output is AutomaticHashConstraint -> throw IllegalArgumentException("Illegal constraint: AutomaticHashConstraint.")

        // Transition to the same constraint.
        input == output -> true

        // You can't transition from the AlwaysAcceptAttachmentConstraint to anything else, as it could hide something illegal.
        input is AlwaysAcceptAttachmentConstraint && output !is AlwaysAcceptAttachmentConstraint -> false

        // Nothing can be migrated from the HashConstraint except a HashConstraint with the same Hash. (This check is redundant, but added for clarity)
        input is HashAttachmentConstraint && output is HashAttachmentConstraint -> input == output

        // Anything (except the AlwaysAcceptAttachmentConstraint) can be transformed to a HashAttachmentConstraint.
        input !is HashAttachmentConstraint && output is HashAttachmentConstraint -> true

        // The SignatureAttachmentConstraint allows migration from a Signature constraint with the same key.
        // TODO - we don't support currently third party signers. When we do, the output key will have to be stronger then the input key.
        input is SignatureAttachmentConstraint && output is SignatureAttachmentConstraint -> input.key == output.key

        // You can transition from the WhitelistConstraint to the SignatureConstraint only if all signers of the JAR are required to sign in the future.
        input is WhitelistedByZoneAttachmentConstraint && output is SignatureAttachmentConstraint ->
            attachment.signerKeys.isNotEmpty() && output.key.keys.containsAll(attachment.signerKeys)

        // Transition from Hash to Signature constraint requires
        // signer(s) of signature-constrained output state is same as signer(s) of registered package namespace
        input is HashAttachmentConstraint && output is SignatureAttachmentConstraint -> {
            val packageOwnerPK = attachment.networkParameters.getPackageOwnerOf(attachment.contractAttachment.allContracts)
            if (packageOwnerPK == null) {
                log.warn("Missing registered java package owner for ${attachment.contractAttachment.contract} in network parameters: " +
                        "${attachment.networkParameters} (input constraint = $input, output constraint = $output)")
                return false
            }
            else if (!packageOwnerPK.isFulfilledBy(output.key) ) {
                log.warn("Java package owner keys do not match signature constrained output state keys")
                return false
            }
            return true
        }
        else -> false
    }
}

private val validConstraints = setOf(
        AlwaysAcceptAttachmentConstraint::class,
        HashAttachmentConstraint::class,
        WhitelistedByZoneAttachmentConstraint::class,
        SignatureAttachmentConstraint::class
)

/**
 * Fails if the constraint is not of a known type.
 * Only the Corda core is allowed to implement the [AttachmentConstraint] interface.
 */
internal fun checkConstraintValidity(state: TransactionState<*>) {
    require(state.constraint::class in validConstraints) { "Found state ${state.contract} with an illegal constraint: ${state.constraint}" }
    if (state.constraint is AlwaysAcceptAttachmentConstraint) {
        log.warnOnce("Found state ${state.contract} that is constrained by the insecure: AlwaysAcceptAttachmentConstraint.")
    }
}

/**
 * Check for the [NoConstraintPropagation] annotation on the contractClassName. If it's present it means that the automatic secure core behaviour
 * is not applied, and it's up to the contract developer to enforce a secure propagation logic.
 */
internal fun ContractClassName.contractHasAutomaticConstraintPropagation(classLoader: ClassLoader? = null): Boolean {
    return (classLoader ?: NoConstraintPropagation::class.java.classLoader)
            .run { Class.forName(this@contractHasAutomaticConstraintPropagation, false, this) }
            .getAnnotation(NoConstraintPropagation::class.java) == null
}

fun ContractClassName.warnContractWithoutConstraintPropagation(classLoader: ClassLoader? = null) {
    if (!this.contractHasAutomaticConstraintPropagation(classLoader)) {
        log.warnOnce("Found contract $this with automatic constraint propagation disabled.")
    }
}

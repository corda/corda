package net.corda.core.contracts

import net.corda.core.DoNotImplement
import net.corda.core.KeepForDJVM
import net.corda.core.contracts.AlwaysAcceptAttachmentConstraint.isSatisfiedBy
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.crypto.keys
import net.corda.core.internal.AttachmentWithContext
import net.corda.core.internal.isUploaderTrusted
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.warnOnce
import org.slf4j.LoggerFactory
import java.lang.annotation.Inherited
import java.security.PublicKey

/**
 * This annotation should only be added to [Contract] classes.
 * If the annotation is present, then we assume that [Contract.verify] will ensure that the output states have an acceptable constraint.
 * If the annotation is missing, then the default - secure - constraint propagation logic is enforced by the platform.
 */
@Target(AnnotationTarget.CLASS)
@Inherited
annotation class NoConstraintPropagation

/**
 * Constrain which contract-code-containing attachment can be used with a [Contract].
 * */
@CordaSerializable
@DoNotImplement
interface AttachmentConstraint {
    /** Returns whether the given contract attachment can be used with the [ContractState] associated with this constraint object. */
    fun isSatisfiedBy(attachment: Attachment): Boolean

    /**
     * This method will be used in conjunction with [NoConstraintPropagation]. It is run during transaction verification when the contract is not annotated with [NoConstraintPropagation].
     * When constraints propagation is enabled, constraints set on output states need to follow certain rules with regards to constraints of input states.
     *
     * Rules:
     *  * It is allowed for output states to inherit the exact same constraint as the input states.
     *  * The [AlwaysAcceptAttachmentConstraint] is not allowed to transition to a different constraint, as that could be used to hide malicious behaviour.
     *  * Nothing can be migrated from the [HashAttachmentConstraint] except a [HashAttachmentConstraint] with the same hash.
     *  * Anything (except the [AlwaysAcceptAttachmentConstraint]) can be transitioned to a [HashAttachmentConstraint].
     *  * You can transition from the [WhitelistedByZoneAttachmentConstraint] to the [SignatureAttachmentConstraint] only if all signers of the JAR are required to sign in the future.
     *
     * TODO - SignatureConstraint third party signers.
     */
    fun canBeTransitionedFrom(input: AttachmentConstraint, attachment: ContractAttachment): Boolean {
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
            // TODO - this might change if we decide to allow migration to the SignatureConstraint.
            input is HashAttachmentConstraint && output is HashAttachmentConstraint -> input == output
            input is HashAttachmentConstraint && output !is HashAttachmentConstraint -> false

            // Anything (except the AlwaysAcceptAttachmentConstraint) can be transformed to a HashAttachmentConstraint.
            input !is HashAttachmentConstraint && output is HashAttachmentConstraint -> true

            // The SignatureAttachmentConstraint allows migration from a Signature constraint with the same key.
            // TODO - we don't support currently third party signers. When we do, the output key will have to be stronger then the input key.
            input is SignatureAttachmentConstraint && output is SignatureAttachmentConstraint -> input.key == output.key

            // You can transition from the WhitelistConstraint to the SignatureConstraint only if all signers of the JAR are required to sign in the future.
            input is WhitelistedByZoneAttachmentConstraint && output is SignatureAttachmentConstraint ->
                attachment.signers.isNotEmpty() && output.key.keys.containsAll(attachment.signers)

            else -> false
        }
    }
}

/** An [AttachmentConstraint] where [isSatisfiedBy] always returns true. */
@KeepForDJVM
object AlwaysAcceptAttachmentConstraint : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment) = true
}

/**
 * An [AttachmentConstraint] that verifies by hash.
 * The state protected by this constraint can only be used in a transaction created with that version of the jar.
 * And a receiving node will only accept it if a cordapp with that hash has (is) been deployed on the node.
 */
@KeepForDJVM
data class HashAttachmentConstraint(val attachmentId: SecureHash) : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment): Boolean {
        return if (attachment is AttachmentWithContext) {
            attachment.id == attachmentId && isUploaderTrusted(attachment.contractAttachment.uploader)
        } else false
    }
}

/**
 * An [AttachmentConstraint] that verifies that the hash of the attachment is in the network parameters whitelist.
 * See: [net.corda.core.node.NetworkParameters.whitelistedContractImplementations]
 * It allows for centralized control over the cordapps that can be used.
 */
@KeepForDJVM
object WhitelistedByZoneAttachmentConstraint : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment): Boolean {
        return if (attachment is AttachmentWithContext) {
            val whitelist = attachment.whitelistedContractImplementations
                    ?: throw IllegalStateException("Unable to verify WhitelistedByZoneAttachmentConstraint - whitelist not specified")
            attachment.id in (whitelist[attachment.stateContract] ?: emptyList())
        } else false
    }
}

@KeepForDJVM
@Deprecated("The name is no longer valid as multiple constraints were added.", replaceWith = ReplaceWith("AutomaticPlaceholderConstraint"), level = DeprecationLevel.WARNING)
object AutomaticHashConstraint : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment): Boolean {
        throw UnsupportedOperationException("Contracts cannot be satisfied by an AutomaticHashConstraint placeholder.")
    }
}

/**
 * This [AttachmentConstraint] is a convenience class that acts as a placeholder and will be automatically resolved by the platform when set on an output state.
 * It is the default constraint of all output states.
 *
 * The resolution occurs in [TransactionBuilder.toWireTransaction] and is based on the input states and the attachments.
 * If the [Contract] was not annotated with [NoConstraintPropagation], then the platform will ensure the correct constraint propagation.
 */
@KeepForDJVM
object AutomaticPlaceholderConstraint : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment): Boolean {
        throw UnsupportedOperationException("Contracts cannot be satisfied by an AutomaticPlaceholderConstraint placeholder.")
    }
}

private val logger = LoggerFactory.getLogger(AttachmentConstraint::class.java)
private val validConstraints = setOf(
        AlwaysAcceptAttachmentConstraint::class,
        HashAttachmentConstraint::class,
        WhitelistedByZoneAttachmentConstraint::class,
        SignatureAttachmentConstraint::class)

/**
 * Fails if the constraint is not of a known type.
 * Only the Corda core is allowed to implement the [AttachmentConstraint] interface.
 */
internal fun checkConstraintValidity(state: TransactionState<*>) {
    require(state.constraint::class in validConstraints) { "Found state ${state.contract} with an illegal constraint: ${state.constraint}" }
    if (state.constraint is AlwaysAcceptAttachmentConstraint) {
        logger.warnOnce("Found state ${state.contract} that is constrained by the insecure: AlwaysAcceptAttachmentConstraint.")
    }
}

/**
 * Check for the [NoConstraintPropagation] annotation on the contractClassName.
 * If it's present it means that the automatic secure core behaviour is not applied, and it's up to the contract developer to enforce a secure propagation logic.
 */
internal fun ContractClassName.contractHasAutomaticConstraintPropagation(classLoader: ClassLoader? = null) =
        (classLoader ?: NoConstraintPropagation::class.java.classLoader)
                .loadClass(this).getAnnotation(NoConstraintPropagation::class.java) == null

fun ContractClassName.warnContractWithoutConstraintPropagation(classLoader: ClassLoader? = null) {
    if (!this.contractHasAutomaticConstraintPropagation(classLoader)) {
        logger.warnOnce("Found contract $this with automatic constraint propagation disabled.")
    }
}

/**
 * An [AttachmentConstraint] that verifies that the attachment has signers that fulfil the provided [PublicKey].
 * See: [Signature Constraints](https://docs.corda.net/design/data-model-upgrades/signature-constraints.html)
 *
 * @param key A [PublicKey] that must be fulfilled by the owning keys of the attachment's signing parties.
 */
@KeepForDJVM
data class SignatureAttachmentConstraint(
        val key: PublicKey
) : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment): Boolean =
        key.isFulfilledBy(attachment.signers.map { it })
}
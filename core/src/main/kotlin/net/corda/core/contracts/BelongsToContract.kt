package net.corda.core.contracts

import kotlin.reflect.KClass

/**
 * This annotation is required by any [ContractState] which needs to ensure that it is only ever processed as part of a
 * [TransactionState] referencing the specified [Contract]. It may be omitted in the case that the [ContractState] class
 * is defined as an inner class of its owning [Contract] class, in which case the "X belongs to Y" relationship is taken
 * to be implicitly declared.
 *
 * During verification of transactions, prior to their being written into the ledger, all input and output states are
 * checked to ensure that their [ContractState]s match with their [Contract]s as specified either by this annotation, or
 * by their inner/outer class relationship.
 *
 * The transaction will fail with a [TransactionContractConflictException] if any mismatch is detected.
 *
 * @param value The class of the [Contract] to which states of the annotated [ContractState] belong.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class BelongsToContract(val value: KClass<out Contract>)

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
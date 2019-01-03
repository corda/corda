package net.corda.core.contracts

import kotlin.reflect.KClass
import net.corda.core.contracts.TransactionVerificationException.TransactionContractConflictException

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
 * The transaction will write a warning to the log (for corDapps with a target version less than 4) or
 * fail with a [TransactionContractConflictException] if any mismatch is detected.
 *
 * @param value The class of the [Contract] to which states of the annotated [ContractState] belong.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class BelongsToContract(val value: KClass<out Contract>)

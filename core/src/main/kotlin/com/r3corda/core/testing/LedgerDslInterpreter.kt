package com.r3corda.core.testing

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.SecureHash
import java.io.InputStream

interface OutputStateLookup {
    fun <State: ContractState> retrieveOutputStateAndRef(clazz: Class<State>, label: String): StateAndRef<State>
}

interface LedgerDslInterpreter<Return, out TransactionInterpreter: TransactionDslInterpreter<Return>> :
        OutputStateLookup {
    fun transaction(
            transactionLabel: String?,
            dsl: TransactionDsl<Return, TransactionInterpreter>.() -> Return
    ): WireTransaction
    fun nonVerifiedTransaction(
            transactionLabel: String?,
            dsl: TransactionDsl<Return, TransactionInterpreter>.() -> Unit
    ): WireTransaction
    fun tweak(dsl: LedgerDsl<Return, TransactionInterpreter, LedgerDslInterpreter<Return, TransactionInterpreter>>.() -> Unit)
    fun attachment(attachment: InputStream): SecureHash
    fun verifies()
}

/**
 * This is the class the top-level primitives deal with. It delegates all other primitives to the contained interpreter.
 * This way we have a decoupling of the DSL "AST" and the interpretation(s) of it. Note how the delegation forces
 * covariance of the TransactionInterpreter parameter
 */
class LedgerDsl<
        Return,
    out TransactionInterpreter: TransactionDslInterpreter<Return>,
    out LedgerInterpreter: LedgerDslInterpreter<Return, TransactionInterpreter>
    > (val interpreter: LedgerInterpreter
) : LedgerDslInterpreter<Return, TransactionDslInterpreter<Return>> by interpreter {

    fun transaction(dsl: TransactionDsl<Return, TransactionDslInterpreter<Return>>.() -> Return) =
            transaction(null, dsl)
    fun nonVerifiedTransaction(dsl: TransactionDsl<Return, TransactionDslInterpreter<Return>>.() -> Unit) =
            nonVerifiedTransaction(null, dsl)

    inline fun <reified State: ContractState> String.outputStateAndRef(): StateAndRef<State> =
            retrieveOutputStateAndRef(State::class.java, this)
    inline fun <reified State: ContractState> String.output(): TransactionState<State> =
            outputStateAndRef<State>().state
    fun String.outputRef(): StateRef = outputStateAndRef<ContractState>().ref
}

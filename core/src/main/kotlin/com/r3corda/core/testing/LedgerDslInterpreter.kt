package com.r3corda.core.testing

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.SecureHash

interface OutputStateLookup {
    fun <State: ContractState> retrieveOutputStateAndRef(clazz: Class<State>, label: String): StateAndRef<State>
}



interface LedgerDslInterpreter<out TransactionInterpreter: TransactionDslInterpreter> :
        OutputStateLookup {
    fun transaction(transactionLabel: String?, dsl: TransactionDsl<TransactionInterpreter>.() -> Unit): WireTransaction
    fun nonVerifiedTransaction(transactionLabel: String?, dsl: TransactionDsl<TransactionInterpreter>.() -> Unit): WireTransaction
    fun tweak(dsl: LedgerDsl<TransactionInterpreter, LedgerDslInterpreter<TransactionInterpreter>>.() -> Unit)
    fun attachment(attachment: Attachment): SecureHash
    fun verifies()
}

/**
 * This is the class the top-level primitives deal with. It delegates all other primitives to the contained interpreter.
 * This way we have a decoupling of the DSL "AST" and the interpretation(s) of it. Note how the delegation forces
 * covariance of the TransactionInterpreter parameter
 */
class LedgerDsl<
    out TransactionInterpreter: TransactionDslInterpreter,
    out LedgerInterpreter: LedgerDslInterpreter<TransactionInterpreter>
    > (val interpreter: LedgerInterpreter)
    : LedgerDslInterpreter<TransactionDslInterpreter> by interpreter {

    fun transaction(dsl: TransactionDsl<TransactionDslInterpreter>.() -> Unit) = transaction(null, dsl)
    fun nonVerifiedTransaction(dsl: TransactionDsl<TransactionDslInterpreter>.() -> Unit) =
            nonVerifiedTransaction(null, dsl)

    inline fun <reified State: ContractState> String.outputStateAndRef(): StateAndRef<State> =
            retrieveOutputStateAndRef(State::class.java, this)
    inline fun <reified State: ContractState> String.output(): TransactionState<State> =
            outputStateAndRef<State>().state
    fun String.outputRef(): StateRef = outputStateAndRef<ContractState>().ref
}

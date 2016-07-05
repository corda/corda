package com.r3corda.core.testing

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.SecureHash
import java.io.InputStream

interface OutputStateLookup {
    fun <S : ContractState> retrieveOutputStateAndRef(clazz: Class<S>, label: String): StateAndRef<S>
}

interface LedgerDSLInterpreter<R, out T : TransactionDSLInterpreter<R>> : OutputStateLookup {
    fun transaction(transactionLabel: String?, dsl: TransactionDSL<R, T>.() -> R): WireTransaction
    fun unverifiedTransaction(transactionLabel: String?, dsl: TransactionDSL<R, T>.() -> Unit): WireTransaction
    fun tweak(dsl: LedgerDSL<R, T, LedgerDSLInterpreter<R, T>>.() -> Unit)
    fun attachment(attachment: InputStream): SecureHash
    fun verifies()
}

/**
 * This is the class the top-level primitives deal with. It delegates all other primitives to the contained interpreter.
 * This way we have a decoupling of the DSL "AST" and the interpretation(s) of it. Note how the delegation forces
 * covariance of the TransactionInterpreter parameter
 */
class LedgerDSL<R, out T : TransactionDSLInterpreter<R>, out L : LedgerDSLInterpreter<R, T>> (val interpreter: L) :
        LedgerDSLInterpreter<R, TransactionDSLInterpreter<R>> by interpreter {

    fun transaction(dsl: TransactionDSL<R, TransactionDSLInterpreter<R>>.() -> R) =
            transaction(null, dsl)
    fun unverifiedTransaction(dsl: TransactionDSL<R, TransactionDSLInterpreter<R>>.() -> Unit) =
            unverifiedTransaction(null, dsl)

    inline fun <reified S : ContractState> String.outputStateAndRef(): StateAndRef<S> =
            retrieveOutputStateAndRef(S::class.java, this)
    inline fun <reified S : ContractState> String.output(): TransactionState<S> =
            outputStateAndRef<S>().state
    fun String.outputRef(): StateRef = outputStateAndRef<ContractState>().ref
}

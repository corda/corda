package com.r3corda.core.testing

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.SecureHash
import java.io.InputStream

/**
 * This interface defines output state lookup by label. It is split from the interpreter interfaces so that outputs may
 * be looked up both in ledger{..} and transaction{..} blocks.
 */
interface OutputStateLookup {
    /**
     * Retrieves an output previously defined by [TransactionDSLInterpreter._output] with a label passed in.
     * @param clazz: The class object holding the type of the output state expected.
     * @param label: The label of the to-be-retrieved output state
     * @return: The output [StateAndRef]
     */
    fun <S : ContractState> retrieveOutputStateAndRef(clazz: Class<S>, label: String): StateAndRef<S>
}

/**
 * This interface defines the bare bone functionality that a Ledger DSL interpreter should implement.
 *
 * TODO (Kotlin 1.1): Use type synonyms to make the type params less unwieldy
 */
interface LedgerDSLInterpreter<R, out T : TransactionDSLInterpreter<R>> : OutputStateLookup {
    /**
     * Creates and adds a transaction to the ledger.
     * @param transactionLabel: Optional label of the transaction, to be used in diagnostic messages.
     * @param transactionBuilder: The base transactionBuilder that will be used to build the transaction.
     * @param dsl: The dsl that should be interpreted for building the transaction.
     * @return: The final [WireTransaction] of the built transaction.
     */
    fun _transaction(transactionLabel: String?, transactionBuilder: TransactionBuilder,
                     dsl: TransactionDSL<R, T>.() -> R): WireTransaction

    /**
     * Creates and adds a transaction to the ledger that will not be verified by [verifies].
     * @param transactionLabel: Optional label of the transaction, to be used in diagnostic messages.
     * @param transactionBuilder: The base transactionBuilder that will be used to build the transaction.
     * @param dsl: The dsl that should be interpreted for building the transaction.
     * @return: The final [WireTransaction] of the built transaction.
     */
    fun _unverifiedTransaction(transactionLabel: String?, transactionBuilder: TransactionBuilder,
                               dsl: TransactionDSL<R, T>.() -> Unit): WireTransaction

    /**
     * Creates a local scoped copy of the ledger.
     * @param dsl: The ledger DSL to be interpreted using the copy.
     */
    fun tweak(dsl: LedgerDSL<R, T, LedgerDSLInterpreter<R, T>>.() -> Unit)

    /**
     * Adds an attachment to the ledger.
     * @param attachment: The [InputStream] defining the contents of the attachment.
     * @return: The [SecureHash] that identifies the attachment, to be used in transactions.
     */
    fun attachment(attachment: InputStream): SecureHash

    /**
     * Verifies the ledger using [TransactionGroup.verify], throws if the verification fails.
     */
    fun verifies()
}

/**
 * This is the class that defines the syntactic sugar of the ledger Test DSL and delegates to the contained interpreter,
 * and what is actually used in `ledger { (...) }`. Add convenience functions here, or if you want to extend the DSL
 * functionality then first add your primitive to [LedgerDSLInterpreter] and then add the convenience defaults/extension
 * methods here.
 */
class LedgerDSL<R, out T : TransactionDSLInterpreter<R>, out L : LedgerDSLInterpreter<R, T>> (val interpreter: L) :
        LedgerDSLInterpreter<R, TransactionDSLInterpreter<R>> by interpreter {

    /**
     * @see LedgerDSLInterpreter._transaction
     */
    @JvmOverloads
    fun transaction(label: String? = null, transactionBuilder: TransactionBuilder = TransactionBuilder(),
                    dsl: TransactionDSL<R, TransactionDSLInterpreter<R>>.() -> R) =
            _transaction(label, transactionBuilder, dsl)
    /**
     * @see LedgerDSLInterpreter._unverifiedTransaction
     */
    @JvmOverloads
    fun unverifiedTransaction(label: String? = null, transactionBuilder: TransactionBuilder = TransactionBuilder(),
                    dsl: TransactionDSL<R, TransactionDSLInterpreter<R>>.() -> Unit) =
            _unverifiedTransaction(label, transactionBuilder, dsl)

    /**
     * @see OutputStateLookup.retrieveOutputStateAndRef
     */
    inline fun <reified S : ContractState> String.outputStateAndRef(): StateAndRef<S> =
            retrieveOutputStateAndRef(S::class.java, this)

    /**
     * Retrieves the output [TransactionState] based on the label.
     * @see OutputStateLookup.retrieveOutputStateAndRef
     */
    inline fun <reified S : ContractState> String.output(): TransactionState<S> =
            outputStateAndRef<S>().state

    /**
     * Retrieves the output [StateRef] based on the label.
     * @see OutputStateLookup.retrieveOutputStateAndRef
     */
    fun String.outputRef(): StateRef = outputStateAndRef<ContractState>().ref
}

package com.r3corda.core.testing

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.seconds
import java.security.PublicKey
import java.time.Duration
import java.time.Instant

/**
 * This interface defines the bare bone functionality that a Transaction DSL interpreter should implement.
 * @param <R>: The return type of [verifies]/[failsWith] and the like. It is generic so that we have control over whether
 * we want to enforce users to call these methods (@see [EnforceVerifyOrFail]) or not.
 */
interface TransactionDSLInterpreter<R> : OutputStateLookup {
    /**
     * A reference to the enclosing ledger{..}'s interpreter.
     */
    val ledgerInterpreter: LedgerDSLInterpreter<R, TransactionDSLInterpreter<R>>

    /**
     * Adds an input reference to the transaction. Note that [verifies] will resolve this reference.
     * @param stateRef: The input [StateRef].
     */
    fun input(stateRef: StateRef)

    /**
     * Adds an output to the transaction.
     * @param label: An optional label that may be later used to retrieve the output probably in other transactions.
     * @param notary: The associated notary.
     * @param contractState: The state itself.
     */
    fun _output(label: String?, notary: Party, contractState: ContractState)

    /**
     * Adds an [Attachment] reference to the transaction.
     * @param attachmentId: The hash of the attachment, possibly returned by [LedgerDSLInterpreter.attachment]
     */
    fun attachment(attachmentId: SecureHash)

    /**
     * Adds a command to the transaction.
     * @param signers: The signer public keys.
     * @param commandData: The contents of the command.
     */
    fun _command(signers: List<PublicKey>, commandData: CommandData)

    /**
     * Verifies the transaction.
     * @return: Possibly a token confirming that [verifies] has been called.
     */
    fun verifies(): R

    /**
     * Verifies the transaction, expecting an exception to be thrown.
     * @param expectedMessage: An optional string to be searched for in the raised exception.
     */
    fun failsWith(expectedMessage: String?): R

    /**
     * Creates a local scoped copy of the transaction.
     * @param dsl: The transaction DSL to be interpreted using the copy.
     */
    fun tweak(dsl: TransactionDSL<R, TransactionDSLInterpreter<R>>.() -> R): R
}

class TransactionDSL<R, out T : TransactionDSLInterpreter<R>> (val interpreter: T) :
        TransactionDSLInterpreter<R> by interpreter {

    /**
     * Looks up the output label and adds the found state as an input.
     * @param stateLabel: The label of the output state specified when calling [LedgerDSLInterpreter._output] and friends.
     */
    fun input(stateLabel: String) = input(retrieveOutputStateAndRef(ContractState::class.java, stateLabel).ref)

    /**
     * Creates an [LedgerDSLInterpreter._unverifiedTransaction] with a single output state and adds it's reference as an
     * input to the current transaction.
     * @param state: The state to be added.
     */
    fun input(state: ContractState) {
        val transaction = ledgerInterpreter._unverifiedTransaction(null, TransactionBuilder()) {
            output { state }
        }
        input(transaction.outRef<ContractState>(0).ref)
    }
    fun input(stateClosure: () -> ContractState) = input(stateClosure())

    /**
     * @see TransactionDSLInterpreter._output
     */
    @JvmOverloads
    fun output(label: String? = null, notary: Party = DUMMY_NOTARY, contractStateClosure: () -> ContractState) =
            _output(label, notary,  contractStateClosure())
    /**
     * @see TransactionDSLInterpreter._output
     */
    @JvmOverloads
    fun output(label: String? = null, contractState: ContractState) =
            _output(label, DUMMY_NOTARY, contractState)

    /**
     * @see TransactionDSLInterpreter._command
     */
    fun command(vararg signers: PublicKey, commandDataClosure: () -> CommandData) =
            _command(listOf(*signers), commandDataClosure())
    /**
     * @see TransactionDSLInterpreter._command
     */
    fun command(signer: PublicKey, commandData: CommandData) = _command(listOf(signer), commandData)

    /**
     * Adds a timestamp command to the transaction.
     * @param time: The [Instant] of the [TimestampCommand].
     * @param tolerance: The tolerance of the [TimestampCommand].
     * @param notary: The notary to sign the command.
     */
    @JvmOverloads
    fun timestamp(time: Instant, tolerance: Duration = 30.seconds, notary: PublicKey = DUMMY_NOTARY.owningKey) =
            timestamp(TimestampCommand(time, tolerance), notary)
    /**
     * Adds a timestamp command to the transaction.
     * @param data: The [TimestampCommand].
     * @param notary: The notary to sign the command.
     */
    @JvmOverloads
    fun timestamp(data: TimestampCommand, notary: PublicKey = DUMMY_NOTARY.owningKey) = command(notary, data)

    /**
     * Asserts that the transaction will fail verification
     */
    fun fails() = failsWith(null)

    /**
     * @see TransactionDSLInterpreter.failsWith
     */
    infix fun `fails with`(msg: String) = failsWith(msg)
}

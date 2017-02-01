package net.corda.testing

import net.corda.core.contracts.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.seconds
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.DUMMY_NOTARY
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * This interface defines the bare bone functionality that a Transaction DSL interpreter should implement.
 * @param <R> The return type of [verifies]/[failsWith] and the like. It is generic so that we have control over whether
 * we want to enforce users to call these methods (@see [EnforceVerifyOrFail]) or not.
 */
interface TransactionDSLInterpreter : Verifies, OutputStateLookup {
    /**
     * A reference to the enclosing ledger{..}'s interpreter.
     */
    val ledgerInterpreter: LedgerDSLInterpreter<TransactionDSLInterpreter>

    /**
     * Adds an input reference to the transaction. Note that [verifies] will resolve this reference.
     * @param stateRef The input [StateRef].
     */
    fun input(stateRef: StateRef)

    /**
     * Adds an output to the transaction.
     * @param label An optional label that may be later used to retrieve the output probably in other transactions.
     * @param notary The associated notary.
     * @param encumbrance The position of the encumbrance state.
     * @param contractState The state itself.
     */
    fun _output(label: String?, notary: Party.Full, encumbrance: Int?, contractState: ContractState)

    /**
     * Adds an [Attachment] reference to the transaction.
     * @param attachmentId The hash of the attachment, possibly returned by [LedgerDSLInterpreter.attachment].
     */
    fun attachment(attachmentId: SecureHash)

    /**
     * Adds a command to the transaction.
     * @param signers The signer public keys.
     * @param commandData The contents of the command.
     */
    fun _command(signers: List<CompositeKey>, commandData: CommandData)

    /**
     * Adds a timestamp to the transaction.
     * @param data The [TimestampCommand].
     */
    fun timestamp(data: Timestamp)

    /**
     * Creates a local scoped copy of the transaction.
     * @param dsl The transaction DSL to be interpreted using the copy.
     */
    fun tweak(dsl: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail): EnforceVerifyOrFail
}

class TransactionDSL<out T : TransactionDSLInterpreter>(val interpreter: T) : TransactionDSLInterpreter by interpreter {
    /**
     * Looks up the output label and adds the found state as an input.
     * @param stateLabel The label of the output state specified when calling [TransactionDSLInterpreter._output] and friends.
     */
    fun input(stateLabel: String) = input(retrieveOutputStateAndRef(ContractState::class.java, stateLabel).ref)

    /**
     * Creates an [LedgerDSLInterpreter._unverifiedTransaction] with a single output state and adds it's reference as an
     * input to the current transaction.
     * @param state The state to be added.
     */
    fun input(state: ContractState) {
        val transaction = ledgerInterpreter._unverifiedTransaction(null, TransactionBuilder(notary = DUMMY_NOTARY)) {
            output { state }
            // Add a dummy randomised output so that the transaction id differs when issuing the same state multiple times
            val nonceState = DummyContract.SingleOwnerState(Random().nextInt(), DUMMY_NOTARY.owningKey)
            output { nonceState }
        }
        input(transaction.outRef<ContractState>(0).ref)
    }
    fun input(stateClosure: () -> ContractState) = input(stateClosure())

    /**
     * @see TransactionDSLInterpreter._output
     */
    @JvmOverloads
    fun output(label: String? = null, notary: Party.Full = DUMMY_NOTARY, encumbrance: Int? = null, contractStateClosure: () -> ContractState) =
            _output(label, notary, encumbrance, contractStateClosure())
    /**
     * @see TransactionDSLInterpreter._output
     */
    fun output(label: String, contractState: ContractState) =
            _output(label, DUMMY_NOTARY, null, contractState)

    fun output(contractState: ContractState) =
            _output(null, DUMMY_NOTARY, null, contractState)

    /**
     * @see TransactionDSLInterpreter._command
     */
    fun command(vararg signers: CompositeKey, commandDataClosure: () -> CommandData) =
            _command(listOf(*signers), commandDataClosure())
    /**
     * @see TransactionDSLInterpreter._command
     */
    fun command(signer: CompositeKey, commandData: CommandData) = _command(listOf(signer), commandData)

    /**
     * Adds a timestamp command to the transaction.
     * @param time The [Instant] of the [TimestampCommand].
     * @param tolerance The tolerance of the [TimestampCommand].
     */
    @JvmOverloads
    fun timestamp(time: Instant, tolerance: Duration = 30.seconds) =
            timestamp(Timestamp(time, tolerance))
}

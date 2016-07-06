package com.r3corda.core.testing

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.seconds
import java.security.PublicKey
import java.time.Instant

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Defines a simple DSL for building pseudo-transactions (not the same as the wire protocol) for testing purposes.
//
// Define a transaction like this:
//
// ledger {
//     transaction {
//         input { someExpression }
//         output { someExpression }
//         command { someExpression }
//
//         tweak {
//              ... same thing but works with a copy of the parent, can add inputs/outputs/commands just within this scope.
//         }
//
//         contract.verifies() -> verify() should pass
//         contract `fails with` "some substring of the error message"
//     }
// }
//

/**
 * The [TransactionDSLInterpreter] defines the interface DSL interpreters should satisfy. No
 * overloading/default valuing should be done here, only the basic functions that are required to implement everything.
 * Same goes for functions requiring reflection e.g. [OutputStateLookup.retrieveOutputStateAndRef]
 * Put convenience functions in [TransactionDSL] instead. There are some cases where the overloads would clash with the
 * Interpreter interface, in these cases define a "backing" function in the interface instead (e.g. [_command]).
 *
 * This way the responsibility of providing a nice frontend DSL and the implementation(s) are separated.
 */
interface TransactionDSLInterpreter<R> : OutputStateLookup {
    val ledgerInterpreter: LedgerDSLInterpreter<R, TransactionDSLInterpreter<R>>
    fun input(stateRef: StateRef)
    fun _output(label: String?, notary: Party, contractState: ContractState)
    fun attachment(attachmentId: SecureHash)
    fun _command(signers: List<PublicKey>, commandData: CommandData)
    fun verifies(): R
    fun failsWith(expectedMessage: String?): R
    fun tweak(
            dsl: TransactionDSL<R, TransactionDSLInterpreter<R>>.() -> R
    ): R
}

class TransactionDSL<R, out T : TransactionDSLInterpreter<R>> (val interpreter: T) :
        TransactionDSLInterpreter<R> by interpreter {

    fun input(stateLabel: String) = input(retrieveOutputStateAndRef(ContractState::class.java, stateLabel).ref)
    /**
     * Adds the passed in state as a non-verified transaction output to the ledger and adds that as an input.
     */
    fun input(state: ContractState) {
        val transaction = ledgerInterpreter._unverifiedTransaction(null, TransactionBuilder()) {
            output { state }
        }
        input(transaction.outRef<ContractState>(0).ref)
    }
    fun input(stateClosure: () -> ContractState) = input(stateClosure())

    @JvmOverloads
    fun output(label: String? = null, notary: Party = DUMMY_NOTARY, contractStateClosure: () -> ContractState) =
            _output(label, notary,  contractStateClosure())
    @JvmOverloads
    fun output(label: String? = null, contractState: ContractState) =
            _output(label, DUMMY_NOTARY, contractState)

    fun command(vararg signers: PublicKey, commandDataClosure: () -> CommandData) =
            _command(listOf(*signers), commandDataClosure())
    fun command(signer: PublicKey, commandData: CommandData) = _command(listOf(signer), commandData)

    @JvmOverloads
    fun timestamp(time: Instant, notary: PublicKey = DUMMY_NOTARY.owningKey) =
            timestamp(TimestampCommand(time, 30.seconds), notary)
    @JvmOverloads
    fun timestamp(data: TimestampCommand, notary: PublicKey = DUMMY_NOTARY.owningKey) = command(notary, data)

    fun fails() = failsWith(null)
    infix fun `fails with`(msg: String) = failsWith(msg)
}

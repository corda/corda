package com.r3corda.core.testing

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.services.IdentityService
import com.r3corda.core.seconds
import java.security.PublicKey
import java.time.Instant


/**
 * [State] is bound at the top level. This allows the definition of e.g. [String.output], however it also means that we
 * cannot mix different types of states in the same transaction.
 * TODO: Move the [State] binding to the primitives' level to allow different State types, use reflection to check types
 * dynamically, come up with a substitute for primitives relying on early bind
 */
interface TransactionDslInterpreter : OutputStateLookup {
    fun input(stateRef: StateRef)
    fun output(label: String?, notary: Party, contractState: ContractState)
    fun attachment(attachmentId: SecureHash)
    fun _command(signers: List<PublicKey>, commandData: CommandData)
    fun verifies()
    fun failsWith(expectedMessage: String?)
    fun tweak(dsl: TransactionDsl<TransactionDslInterpreter>.() -> Unit)
}


class TransactionDsl<
    out TransactionInterpreter: TransactionDslInterpreter
    > (val interpreter: TransactionInterpreter)
    : TransactionDslInterpreter by interpreter {

    fun input(stateLabel: String) = input(retrieveOutputStateAndRef(ContractState::class.java, stateLabel).ref)

    // Convenience functions
    fun output(label: String? = null, notary: Party = DUMMY_NOTARY, contractStateClosure: () -> ContractState) =
            output(label, notary,  contractStateClosure())
    @JvmOverloads
    fun output(label: String? = null, contractState: ContractState) = output(label, DUMMY_NOTARY, contractState)

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

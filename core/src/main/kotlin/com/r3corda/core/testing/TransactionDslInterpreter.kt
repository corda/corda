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
interface TransactionDslInterpreter<State: ContractState> {
    fun input(stateLabel: String)
    fun input(stateRef: StateRef)
    fun output(label: String?, notary: Party, contractState: State)
    fun attachment(attachmentId: SecureHash)
    fun _command(signers: List<PublicKey>, commandData: CommandData)
    fun _verifies(identityService: IdentityService)
    fun failsWith(expectedMessage: String?, identityService: IdentityService)
    fun tweak(dsl: TransactionDsl<State, TransactionDslInterpreter<State>>.() -> Unit)
    fun retrieveOutputStateAndRef(label: String): StateAndRef<State>?

    val String.outputStateAndRef: StateAndRef<State>
        get() = retrieveOutputStateAndRef(this) ?: throw IllegalArgumentException("State with label '$this' was not found")
    val String.output: TransactionState<State>
        get() = outputStateAndRef.state
    val String.outputRef: StateRef
        get() = outputStateAndRef.ref
}


class TransactionDsl<
    State: ContractState,
    out TransactionInterpreter: TransactionDslInterpreter<State>
    > (val interpreter: TransactionInterpreter)
    : TransactionDslInterpreter<State> by interpreter {

    // Convenience functions
    fun output(label: String? = null, notary: Party = DUMMY_NOTARY, contractStateClosure: () -> State) =
            output(label, notary,  contractStateClosure())
    @JvmOverloads
    fun output(label: String? = null, contractState: State) = output(label, DUMMY_NOTARY, contractState)

    fun command(vararg signers: PublicKey, commandDataClosure: () -> CommandData) =
            _command(listOf(*signers), commandDataClosure())
    fun command(signer: PublicKey, commandData: CommandData) = _command(listOf(signer), commandData)

    fun verifies(identityService: IdentityService = MOCK_IDENTITY_SERVICE) = _verifies(identityService)

    @JvmOverloads
    fun timestamp(time: Instant, notary: PublicKey = DUMMY_NOTARY.owningKey) =
            timestamp(TimestampCommand(time, 30.seconds), notary)
    @JvmOverloads
    fun timestamp(data: TimestampCommand, notary: PublicKey = DUMMY_NOTARY.owningKey) = command(notary, data)

    fun fails(identityService: IdentityService = MOCK_IDENTITY_SERVICE) = failsWith(null, identityService)
    infix fun `fails with`(msg: String) = failsWith(msg, MOCK_IDENTITY_SERVICE)
}

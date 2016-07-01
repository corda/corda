package com.r3corda.core.testing

import com.r3corda.core.contracts.Attachment
import com.r3corda.core.contracts.ContractState
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.services.IdentityService
import com.r3corda.core.node.services.StorageService
import com.r3corda.core.node.services.testing.MockStorageService

interface LedgerDslInterpreter<State: ContractState, out TransactionInterpreter: TransactionDslInterpreter<State>> {
    fun transaction(dsl: TransactionDsl<State, TransactionInterpreter>.() -> Unit): Unit
    fun nonVerifiedTransaction(dsl: TransactionDsl<State, TransactionInterpreter>.() -> Unit): Unit
    fun tweak(dsl: LedgerDsl<State, TransactionInterpreter, LedgerDslInterpreter<State, TransactionInterpreter>>.() -> Unit)
    fun attachment(attachment: Attachment): SecureHash
    fun _verifies(identityService: IdentityService, storageService: StorageService)
}

/**
 * This is the class the top-level primitives deal with. It delegates all other primitives to the contained interpreter.
 * This way we have a decoupling of the DSL "AST" and the interpretation(s) of it. Note how the delegation forces
 * covariance of the TransactionInterpreter parameter
 */
class LedgerDsl<
    State: ContractState,
    out TransactionInterpreter: TransactionDslInterpreter<State>,
    out LedgerInterpreter: LedgerDslInterpreter<State, TransactionInterpreter>
    > (val interpreter: LedgerInterpreter)
    : LedgerDslInterpreter<State, TransactionDslInterpreter<State>> by interpreter {

    @JvmOverloads
    fun verifies(
            identityService: IdentityService = MOCK_IDENTITY_SERVICE,
            storageService: StorageService = MockStorageService()
    ) = _verifies(identityService, storageService)
}

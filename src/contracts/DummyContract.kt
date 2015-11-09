package contracts

import core.Contract
import core.ContractState
import core.SecureHash
import core.TransactionForVerification

// The dummy contract doesn't do anything useful. It exists for testing purposes.

val DUMMY_PROGRAM_ID = SecureHash.sha256("dummy")

object DummyContract : Contract {
    class State : ContractState {
        override val programRef: SecureHash = DUMMY_PROGRAM_ID
    }

    override fun verify(tx: TransactionForVerification) {
        // Always accepts.
    }

    override val legalContractReference: String = "/dev/null"
}
package contracts

import core.Contract
import core.ContractState
import core.TransactionForVerification
import core.crypto.SecureHash

// The dummy contract doesn't do anything useful. It exists for testing purposes.

val DUMMY_PROGRAM_ID = SecureHash.sha256("dummy")

class DummyContract : Contract {
    class State : ContractState {
        override val programRef: SecureHash = DUMMY_PROGRAM_ID
    }

    override fun verify(tx: TransactionForVerification) {
        // Always accepts.
    }

    // The "empty contract"
    override val legalContractReference: SecureHash = SecureHash.sha256("")
}
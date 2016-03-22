/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package contracts.isolated

import core.Contract
import core.ContractState
import core.TransactionForVerification
import core.crypto.SecureHash

// The dummy contract doesn't do anything useful. It exists for testing purposes.

val ANOTHER_DUMMY_PROGRAM_ID = SecureHash.sha256("dummy")

class AnotherDummyContract : Contract {
    class State : ContractState {
        override val programRef: SecureHash = ANOTHER_DUMMY_PROGRAM_ID
    }

    override fun verify(tx: TransactionForVerification) {
        // Always accepts.
    }

    // The "empty contract"
    override val legalContractReference: SecureHash = SecureHash.sha256("https://anotherdummy.org")
}
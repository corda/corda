/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package contracts.isolated

import core.*
import core.Contract
import core.ContractState
import core.TransactionForVerification
import core.crypto.SecureHash

// The dummy contract doesn't do anything useful. It exists for testing purposes.

val ANOTHER_DUMMY_PROGRAM_ID = AnotherDummyContract()

class AnotherDummyContract : Contract {
    data class State(val foo: Int) : ContractState {
        override val contract = ANOTHER_DUMMY_PROGRAM_ID
    }

    override fun verify(tx: TransactionForVerification) {
        requireThat {
            "justice will be served" by false
        }
        // Always accepts.
    }

    // The "empty contract"
    override val legalContractReference = SecureHash.sha256("https://anotherdummy.org")
}
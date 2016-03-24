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

class AnotherDummyContract : Contract, core.node.DummyContractBackdoor {
    class State(val magicNumber: Int = 0) : ContractState {
        override val contract = ANOTHER_DUMMY_PROGRAM_ID
    }

    interface Commands : CommandData {
        class Create : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: TransactionForVerification) {
        // Always accepts.
    }

    // The "empty contract"
    override val legalContractReference: SecureHash = SecureHash.sha256("https://anotherdummy.org")

    override fun generateInitial(owner: PartyReference, magicNumber: Int) : TransactionBuilder {
        val state = State(magicNumber)
        return TransactionBuilder().withItems( state, Command(Commands.Create(), owner.party.owningKey) )
    }

    override fun inspectState(state: core.ContractState) : Int = (state as State).magicNumber

}
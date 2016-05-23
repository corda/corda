/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package com.r3corda.contracts.isolated

import com.r3corda.core.*
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash

// The dummy contract doesn't do anything useful. It exists for testing purposes.

val ANOTHER_DUMMY_PROGRAM_ID = AnotherDummyContract()

class AnotherDummyContract : Contract, com.r3corda.core.node.DummyContractBackdoor {
    class State(val magicNumber: Int = 0, override val notary: Party) : ContractState {
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

    override fun generateInitial(owner: PartyAndReference, magicNumber: Int, notary: Party): TransactionBuilder {
        val state = State(magicNumber, notary)
        return TransactionBuilder().withItems(state, Command(Commands.Create(), owner.party.owningKey))
    }

    override fun inspectState(state: ContractState): Int = (state as State).magicNumber

}
/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package com.r3corda.contracts.isolated

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import java.security.PublicKey

// The dummy contract doesn't do anything useful. It exists for testing purposes.

val ANOTHER_DUMMY_PROGRAM_ID = AnotherDummyContract()

class AnotherDummyContract : Contract, com.r3corda.core.node.DummyContractBackdoor {
    data class State(val magicNumber: Int = 0) : ContractState {
        override val contract = ANOTHER_DUMMY_PROGRAM_ID
        override val participants: List<PublicKey>
            get() = emptyList()
    }

    interface Commands : CommandData {
        class Create : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: TransactionForContract) {
        // Always accepts.
    }

    // The "empty contract"
    override val legalContractReference: SecureHash = SecureHash.sha256("https://anotherdummy.org")

    override fun generateInitial(owner: PartyAndReference, magicNumber: Int, notary: Party): TransactionBuilder {
        val state = State(magicNumber)
        return TransactionType.General.Builder(notary = notary).withItems(state, Command(Commands.Create(), owner.party.owningKey))
    }

    override fun inspectState(state: ContractState): Int = (state as State).magicNumber

}
package net.corda.core.node

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.PartyAndReference
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.crypto.Party

interface DummyContractBackdoor {
    fun generateInitial(owner: PartyAndReference, magicNumber: Int, notary: Party.Full): TransactionBuilder

    fun inspectState(state: ContractState): Int
}

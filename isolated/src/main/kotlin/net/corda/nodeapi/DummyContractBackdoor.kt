package net.corda.nodeapi

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.PartyAndReference
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder

interface DummyContractBackdoor {
    fun generateInitial(owner: PartyAndReference, magicNumber: Int, notary: Party): TransactionBuilder

    fun inspectState(state: ContractState): Int
}

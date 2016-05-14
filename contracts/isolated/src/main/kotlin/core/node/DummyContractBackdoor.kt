package core.node

import core.contracts.ContractState
import core.crypto.Party
import core.contracts.PartyAndReference
import core.contracts.TransactionBuilder

interface DummyContractBackdoor {
    fun generateInitial(owner: PartyAndReference, magicNumber: Int, notary: Party): TransactionBuilder

    fun inspectState(state: ContractState): Int
}
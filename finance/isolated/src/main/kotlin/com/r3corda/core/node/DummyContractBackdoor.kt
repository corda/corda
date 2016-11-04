package com.r3corda.core.node

import com.r3corda.core.contracts.ContractState
import com.r3corda.core.contracts.PartyAndReference
import com.r3corda.core.transactions.TransactionBuilder
import com.r3corda.core.crypto.Party

interface DummyContractBackdoor {
    fun generateInitial(owner: PartyAndReference, magicNumber: Int, notary: Party): TransactionBuilder

    fun inspectState(state: ContractState): Int
}
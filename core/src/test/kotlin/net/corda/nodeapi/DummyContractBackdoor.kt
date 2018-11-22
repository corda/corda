package net.corda.nodeapi

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.PartyAndReference
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder

/**
 * This interface deliberately mirrors the one in the finance:isolated module.
 * We will actually link [AnotherDummyContract] against this interface rather
 * than the one inside isolated.jar, which means we won't need to use reflection
 * to execute the contract's generateInitial() method.
 */
interface DummyContractBackdoor {
    fun generateInitial(owner: PartyAndReference, magicNumber: Int, notary: Party): TransactionBuilder
    fun inspectState(state: ContractState): Int
}

package net.corda.notaryhealthcheck.contract

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

/**
 * Minimal contract to use for checking that notarisation works
 */
class NullContract : Contract {
    override fun verify(tx: LedgerTransaction) {}
    data class NullCommand(val data: Byte = 0) : CommandData // Param must be public for AMQP serialization.
    data class State(override val participants: List<AbstractParty>) : ContractState
}

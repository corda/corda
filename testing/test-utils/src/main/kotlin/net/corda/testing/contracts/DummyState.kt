package net.corda.testing.contracts

import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty

/**
 * Dummy state for use in testing. Not part of any contract, not even the [DummyContract].
 */
data class DummyState @JvmOverloads constructor (
        /** Some information that the state represents for test purposes. **/
        val magicNumber: Int = 0,
        override val participants: List<AbstractParty> = listOf()) : ContractState {
}

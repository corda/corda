package net.corda.core.contracts

import java.security.PublicKey

/**
 * Dummy state for use in testing. Not part of any contract, not even the [DummyContract].
 */
data class DummyState(val magicNumber: Int = 0) : ContractState {
    override val contract = DUMMY_PROGRAM_ID
    override val participants: List<PublicKey>
        get() = emptyList()
}

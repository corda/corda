package net.corda.core.contracts

import net.corda.core.crypto.CompositeKey
import net.corda.core.serialization.CordaSerializable

/**
 * Dummy state for use in testing. Not part of any contract, not even the [DummyContract].
 */
@CordaSerializable
data class DummyState(val magicNumber: Int = 0) : ContractState {
    override val contract = DUMMY_PROGRAM_ID
    override val participants: List<CompositeKey>
        get() = emptyList()
}

package net.corda.core.contracts.testing

/**
 * Dummy state for use in testing. Not part of any contract, not even the [DummyContract].
 */
data class DummyState(val magicNumber: Int = 0) : net.corda.core.contracts.ContractState {
    override val contract = DUMMY_PROGRAM_ID
    override val participants: List<net.corda.core.identity.AbstractParty>
        get() = emptyList()
}

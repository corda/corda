package net.corda.core.contracts

import net.corda.core.identity.AbstractParty

/**
 * All reference data states should implement this interface. The referenceInputs list in the transaction types require
 * elements to implement this interface.
 *
 * The owner should be the only participant for this state.
 *
 * TODO The owner should probably just be a party. So in the absence of DDGs parties with out of date data can just
 * request an update from the owner.
 */
interface ReferenceState : LinearState {
    val owner: AbstractParty
    override val participants: List<AbstractParty> get() = listOf(owner)
}
package net.corda.sample.businessnetwork.membership.flow

import net.corda.core.identity.AbstractParty

/**
 * Represents a concept of a parties member list.
 * Nodes or other parties can be grouped into membership lists to represent business network relationship among them
 */
interface MembershipList {
    /**
     * @return true if a particular party belongs to a list, false otherwise.
     */
    operator fun contains(party: AbstractParty): Boolean = content().contains(party)

    /**
     * Obtains a full content of a membership list.
     */
    fun content(): Set<AbstractParty>
}
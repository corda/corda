/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
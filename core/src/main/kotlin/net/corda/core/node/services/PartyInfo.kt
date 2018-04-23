/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.node.services

import net.corda.core.identity.Party
import net.corda.core.utilities.NetworkHostAndPort

/**
 * Holds information about a [Party], which may refer to either a specific node or a service.
 */
sealed class PartyInfo {
    abstract val party: Party

    data class SingleNode(override val party: Party, val addresses: List<NetworkHostAndPort>) : PartyInfo()
    data class DistributedNode(override val party: Party) : PartyInfo()
}

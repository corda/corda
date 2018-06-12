/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.contracts

import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.internal.VisibleForTesting
import net.corda.core.serialization.CordaSerializable
import java.util.*

/**
 * This class provides a truly unique identifier of a trade, state, or other business object, bound to any existing
 * external ID. Equality and comparison are based on the unique ID only; if two states somehow have the same UUID but
 * different external IDs, it would indicate a problem with handling of IDs.
 *
 * @param externalId Any existing weak identifier such as trade reference ID.
 * This should be set here the first time a [UniqueIdentifier] is created as part of state issuance,
 * or ledger on-boarding activity. This ensure that the human readable identity is paired with the strong ID.
 * @param id Should never be set by user code and left as default initialised.
 * So that the first time a state is issued this should be given a new UUID.
 * Subsequent copies and evolutions of a state should just copy the [externalId] and [id] fields unmodified.
 */
@CordaSerializable
@KeepForDJVM
data class UniqueIdentifier(val externalId: String?, val id: UUID) : Comparable<UniqueIdentifier> {
    @DeleteForDJVM constructor(externalId: String?) : this(externalId, UUID.randomUUID())
    @DeleteForDJVM constructor() : this(null)

    override fun toString(): String = if (externalId != null) "${externalId}_$id" else id.toString()

    companion object {
        /** Helper function for unit tests where the UUID needs to be manually initialised for consistency. */
        @VisibleForTesting
        fun fromString(name: String): UniqueIdentifier = UniqueIdentifier(null, UUID.fromString(name))
    }

    override fun compareTo(other: UniqueIdentifier): Int = id.compareTo(other.id)

    override fun equals(other: Any?): Boolean {
        return if (other is UniqueIdentifier)
            id == other.id
        else
            false
    }

    override fun hashCode(): Int = id.hashCode()
}
/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node.services

import co.paralleluniverse.fibers.Suspendable
import core.Party
import core.WireTransaction
import core.crypto.DigitalSignature
import core.crypto.generateKeyPair
import core.serialization.SerializedBytes

/**
 * Simple interface (for testing) to an abstract timestamping service. Note that this is not "timestamping" in the
 * blockchain sense of a total ordering of transactions, but rather, a signature from a well known/trusted timestamping
 * service over a transaction that indicates the timestamp in it is accurate. Such a signature may not always be
 * necessary: if there are multiple parties involved in a transaction then they can cross-check the timestamp
 * themselves.
 */
interface TimestamperService {
    @Suspendable
    fun timestamp(wtxBytes: SerializedBytes<WireTransaction>): DigitalSignature.LegallyIdentifiable

    /** The name+pubkey that this timestamper will sign with. */
    val identity: Party
}

// Smart contracts may wish to specify explicitly which timestamping authorities are trusted to assert the time.
// We define a dummy authority here to allow to us to develop prototype contracts in the absence of a real authority.
// The timestamper itself is implemented in the unit test part of the code (in TestUtils.kt).
object DummyTimestampingAuthority {
    val key = generateKeyPair()
    val identity = Party("Mock Company 0", key.public)
}

sealed class TimestampingError : Exception() {
    class RequiresExactlyOneCommand : TimestampingError()
    /**
     * Thrown if an attempt is made to timestamp a transaction using a trusted timestamper, but the time on the
     * transaction is too far in the past or future relative to the local clock and thus the timestamper would reject
     * it.
     */
    class NotOnTimeException : TimestampingError()

    /** Thrown if the command in the transaction doesn't list this timestamping authorities public key as a signer */
    class NotForMe : TimestampingError()
}

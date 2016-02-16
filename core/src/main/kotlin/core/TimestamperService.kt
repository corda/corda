package core

import co.paralleluniverse.fibers.Suspendable
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
    val identity = Party("The dummy timestamper", key.public)
}

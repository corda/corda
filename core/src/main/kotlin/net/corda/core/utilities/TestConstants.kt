@file:JvmName("TestConstants")

package net.corda.core.utilities

import net.corda.core.crypto.*
import java.math.BigInteger
import java.security.KeyPair
import java.time.Instant

// A dummy time at which we will be pretending test transactions are created.
val TEST_TX_TIME: Instant get() = Instant.parse("2015-04-17T12:00:00.00Z")

val DUMMY_PUBKEY_1: CompositeKey get() = DummyPublicKey("x1").composite
val DUMMY_PUBKEY_2: CompositeKey get() = DummyPublicKey("x2").composite

val DUMMY_KEY_1: KeyPair by lazy { generateKeyPair() }
val DUMMY_KEY_2: KeyPair by lazy { generateKeyPair() }

val DUMMY_NOTARY_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(20)) }
val DUMMY_NOTARY: Party.Full get() = Party.Full("Notary Service", DUMMY_NOTARY_KEY.public)

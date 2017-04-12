package net.corda.core.crypto

import java.security.KeyFactory

/**
 * Custom [KeyFactory] for EdDSA with null security [Provider].
 * This is required as a [SignatureScheme] requires a [java.security.KeyFactory] property, but i2p has
 * its own KeyFactory for EdDSA, thus this actually a Proxy Pattern over i2p's KeyFactory.
 */
object EdDSAKeyFactory : KeyFactory(net.i2p.crypto.eddsa.KeyFactory(), null, "EDDSA_ED25519_SHA512")

package net.corda.core.crypto

import java.security.KeyFactory

/**
 * Custom [KeyFactory] for EdDSA with null security [Provider].
 * It is required for compatibility purposes on generating keys similarly to other providers like BouncyCastle.
 */
class EdDSAKeyFactory: KeyFactory {
    constructor() : super(net.i2p.crypto.eddsa.KeyFactory(), null, "EDDSA_ED25519_SHA512")
}

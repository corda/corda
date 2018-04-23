/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.crypto

import java.security.*
import java.security.spec.InvalidKeySpecException
import java.security.spec.KeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Factory for generating composite keys from ASN.1 format key specifications. This is used by [CordaSecurityProvider].
 */
class CompositeKeyFactory : KeyFactorySpi() {

    @Throws(InvalidKeySpecException::class)
    override fun engineGeneratePrivate(keySpec: KeySpec): PrivateKey {
        // Private composite key not supported.
        throw InvalidKeySpecException("key spec not recognised: " + keySpec.javaClass)
    }

    @Throws(InvalidKeySpecException::class)
    override fun engineGeneratePublic(keySpec: KeySpec): PublicKey? {
        return when (keySpec) {
            is X509EncodedKeySpec -> CompositeKey.getInstance(keySpec.encoded)
            else -> throw InvalidKeySpecException("key spec not recognised: " + keySpec.javaClass)
        }
    }

    @Throws(InvalidKeySpecException::class)
    override fun <T : KeySpec> engineGetKeySpec(key: Key, keySpec: Class<T>): T {
        // Only support X509EncodedKeySpec.
        throw InvalidKeySpecException("Not implemented yet $key $keySpec")
    }

    @Throws(InvalidKeyException::class)
    override fun engineTranslateKey(key: Key): Key {
        throw InvalidKeyException("No other composite key providers known")
    }
}

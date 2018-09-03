package net.corda.core.crypto

import net.corda.core.DeleteForDJVM
import java.security.*
import java.security.spec.InvalidKeySpecException
import java.security.spec.KeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Factory for generating composite keys from ASN.1 format key specifications. This is used by [CordaSecurityProvider].
 */
@DeleteForDJVM
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

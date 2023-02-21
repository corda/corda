package net.corda.core.crypto.internal

import net.corda.core.utilities.ByteSequence
import java.security.PublicKey

@Suppress("unused")
object PublicKeyCache {
    @Suppress("UNUSED_PARAMETER")
    fun bytesForCachedPublicKey(key: PublicKey): ByteSequence? {
        return null
    }

    @Suppress("UNUSED_PARAMETER")
    fun publicKeyForCachedBytes(bytes: ByteSequence): PublicKey? {
        return null
    }

    fun cachePublicKey(key: PublicKey): PublicKey {
        return key
    }
}
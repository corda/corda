package net.corda.serialization.djvm.deserializers

import net.corda.core.crypto.Crypto
import java.security.PublicKey
import java.util.function.Function

class PublicKeyDecoder : Function<ByteArray, PublicKey> {
    override fun apply(encoded: ByteArray): PublicKey {
        return Crypto.decodePublicKey(encoded)
    }
}

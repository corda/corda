package net.corda.core.crypto

import java.security.MessageDigest
import java.util.function.Supplier

@Suppress("unused")
private class DigestSupplier(private val algorithm: String) : Supplier<CustomMessageDigest> {
    init {
        require(algorithm.toUpperCase() == algorithm) { "Algorithm name $algorithm must be in the upper case" }
    }
    override fun get(): CustomMessageDigest = MessageDigestWrapper(MessageDigest.getInstance(algorithm))
    val digestLength: Int by lazy { get().digestLength }

    private class MessageDigestWrapper(val messageDigest: MessageDigest) : CustomMessageDigest {
        override val digestLength = messageDigest.digestLength
        override fun digest(bytes: ByteArray): ByteArray = messageDigest.digest(bytes)
    }
}

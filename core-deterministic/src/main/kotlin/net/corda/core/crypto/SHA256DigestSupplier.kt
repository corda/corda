package net.corda.core.crypto

import java.security.MessageDigest
import java.util.function.Supplier

@Suppress("unused")
private class SHA256DigestSupplier(private val algorithm: String) : Supplier<MessageDigest> {
    override fun get(): MessageDigest = MessageDigest.getInstance(algorithm)
    var digestLength: Int = get().digestLength
}

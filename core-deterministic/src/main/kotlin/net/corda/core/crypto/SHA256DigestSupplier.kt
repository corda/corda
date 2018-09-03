package net.corda.core.crypto

import java.security.MessageDigest
import java.util.function.Supplier

@Suppress("unused")
private class SHA256DigestSupplier : Supplier<MessageDigest> {
    override fun get(): MessageDigest = MessageDigest.getInstance("SHA-256")
}

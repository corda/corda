package net.corda.core.crypto

/**
 * Generic Exception for crypto-related issues.
 * @param reason a [String] message to be appended to the error log.
 * @return an Exception with an error-log message.
 */
class CryptoException(val reason: String) : Exception() {
    override fun toString() = "Crypto exception. Reason: $reason"
}

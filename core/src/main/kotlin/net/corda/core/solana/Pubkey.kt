@file:Suppress("MagicNumber")
package net.corda.core.solana

import net.corda.core.crypto.Base58
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.toBase58

/**
 * Represents an address on Solana. This is equivalent to the Rust struct of the
 * [same name](https://docs.rs/solana-pubkey/2.4.0/solana_pubkey/struct.Pubkey.html).
 */
class Pubkey(bytes: ByteArray) : OpaqueBytes(bytes) {
    companion object {
        /**
         * Parse the given base58 string as a 32-byte Solana address.
         *
         * @throws net.corda.core.crypto.AddressFormatException If the given string is not valid base58.
         */
        @JvmStatic
        fun fromBase58(input: String): Pubkey = Pubkey(Base58.decode(input))
    }

    init {
        require(bytes.size == 32) { "Solana pubkey must be 32 bytes" }
    }

    override fun toString(): String = bytes.toBase58()
}
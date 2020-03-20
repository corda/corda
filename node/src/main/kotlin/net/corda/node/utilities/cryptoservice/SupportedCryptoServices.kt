package net.corda.node.utilities.cryptoservice

enum class SupportedCryptoServices(val userFriendlyName: String) {
    /** Identifier for [BCCryptoService]. */
    BC_SIMPLE("file-based keystore")
}

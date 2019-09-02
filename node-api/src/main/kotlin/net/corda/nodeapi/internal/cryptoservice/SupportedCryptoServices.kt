package net.corda.nodeapi.internal.cryptoservice

enum class SupportedCryptoServices(val userFriendlyName: String) {
    /** Identifier for [BCCryptoService]. */
    BC_SIMPLE("file-based keystore")
}

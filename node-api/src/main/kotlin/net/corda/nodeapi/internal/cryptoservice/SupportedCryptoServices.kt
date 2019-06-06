package net.corda.nodeapi.internal.cryptoservice

enum class SupportedCryptoServices {
    /** Identifier for [BCCryptoService]. */
    BC_SIMPLE
    // UTIMACO, // Utimaco HSM.
    // GEMALTO_LUNA, // Gemalto Luna HSM.
    // AZURE_KV // Azure key Vault.
}

package net.corda.node.services.keys.cryptoservice

enum class SupportedCryptoServices {
    /** Identifier for [BCCryptoService]. */
    BC_SIMPLE
    // UTIMACO, // Utimaco HSM.
    // GEMALTO_LUNA, // Gemalto Luna HSM.
    // AZURE_KV // Azure key Vault.
}

package net.corda.node.services.keys.cryptoservice

enum class SupportedCryptoServices {
    /** Identifier for [BCCryptoService]. */
    BC_SIMPLE,
    UTIMACO, // Utimaco HSM.
    AZURE_KEY_VAULT // Azure key Vault.
}

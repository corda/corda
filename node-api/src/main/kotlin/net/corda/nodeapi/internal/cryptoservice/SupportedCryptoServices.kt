package net.corda.nodeapi.internal.cryptoservice

enum class SupportedCryptoServices {
    /** Identifier for [BCCryptoService]. */
    BC_SIMPLE,
    UTIMACO, // Utimaco HSM.
    AZURE_KEY_VAULT, // Azure key Vault.
    GEMALTO_LUNA, // Gemalto Luna HSM.
    FUTUREX // FutureX HSM
}

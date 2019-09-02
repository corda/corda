package net.corda.nodeapi.internal.cryptoservice

enum class SupportedCryptoServices(val userFriendlyName: String) {
    /** Identifier for [BCCryptoService]. */
    BC_SIMPLE("file-based keystore"),
    UTIMACO("Utimaco SecurityServer Se Gen2 HSM"),
    AZURE_KEY_VAULT("Azure KeyVault"),
    GEMALTO_LUNA("Gemalto Luna HSM"),
    FUTUREX("FutureX Excrypt SSP9000 HSM"),
    PRIMUS_X("Securosys PrimusX HSM")
}

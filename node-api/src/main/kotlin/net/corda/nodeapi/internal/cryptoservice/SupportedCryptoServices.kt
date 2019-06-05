package net.corda.nodeapi.internal.cryptoservice

import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.config.FileBasedCertificateStoreSupplier
import net.corda.nodeapi.internal.cryptoservice.bouncycastle.BCCryptoService

enum class SupportedCryptoServices {
    /** Identifier for [BCCryptoService]. */
    BC_SIMPLE;
    // UTIMACO, // Utimaco HSM.
    // GEMALTO_LUNA, // Gemalto Luna HSM.
    // AZURE_KV // Azure key Vault.

    companion object {
        fun makeCryptoService(legalName: CordaX500Name, signingCertificateStore: FileBasedCertificateStoreSupplier): CryptoService {
            return BCCryptoService(legalName.x500Principal, signingCertificateStore)
        }
    }
}

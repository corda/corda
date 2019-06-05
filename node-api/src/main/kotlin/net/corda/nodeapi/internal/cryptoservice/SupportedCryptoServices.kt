package net.corda.nodeapi.internal.cryptoservice

import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.config.FileBasedCertificateStoreSupplier
import net.corda.nodeapi.internal.cryptoservice.bouncycastle.BCCryptoService
import java.nio.file.Path

enum class SupportedCryptoServices {
    /** Identifier for [BCCryptoService]. */
    BC_SIMPLE;
    // UTIMACO, // Utimaco HSM.
    // GEMALTO_LUNA, // Gemalto Luna HSM.
    // AZURE_KV // Azure key Vault.

    companion object {
        fun makeCryptoService(legalName: CordaX500Name, signingCertificateStore: FileBasedCertificateStoreSupplier? = null, cryptoServiceName: SupportedCryptoServices? = null, cryptoServiceConf: Path? = null): CryptoService {
            // The signing certificate store can be null for other services as only BCC requires is at the moment.
            if (signingCertificateStore == null) {
                throw IllegalArgumentException("Currently only BouncyCastle is used as a crypto service. A valid signing certificate store is required.")
            }
            return BCCryptoService(legalName.x500Principal, signingCertificateStore)
        }
    }
}

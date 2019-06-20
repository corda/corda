package net.corda.nodeapi.internal.cryptoservice

import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.config.FileBasedCertificateStoreSupplier
import net.corda.nodeapi.internal.cryptoservice.bouncycastle.BCCryptoService
import net.corda.nodeapi.internal.cryptoservice.bouncycastle.SignOnlyBCCryptoService
import java.nio.file.Path

class CryptoServiceFactory {
    companion object {

        fun makeSignOnlyCryptoService(cryptoServiceName: SupportedCryptoServices, signingCertificateStore: FileBasedCertificateStoreSupplier? = null, cryptoServiceConf: Path? = null): SignOnlyCryptoService {
            // The signing certificate store can be null for other services as only BCC requires is at the moment.
            if (cryptoServiceName != SupportedCryptoServices.BC_SIMPLE || signingCertificateStore == null) {
                throw IllegalArgumentException("Currently only BouncyCastle is used as a crypto service. A valid signing certificate store is required.")
            }
            return SignOnlyBCCryptoService(signingCertificateStore)
        }

        fun makeCryptoService(cryptoServiceName: SupportedCryptoServices, legalName: CordaX500Name, signingCertificateStore: FileBasedCertificateStoreSupplier? = null, cryptoServiceConf: Path? = null): CryptoService {
            // The signing certificate store can be null for other services as only BCC requires is at the moment.
            if (cryptoServiceName != SupportedCryptoServices.BC_SIMPLE || signingCertificateStore == null) {
                throw IllegalArgumentException("Currently only BouncyCastle is used as a crypto service. A valid signing certificate store is required.")
            }
            return BCCryptoService(legalName.x500Principal, signingCertificateStore)
        }
    }
}
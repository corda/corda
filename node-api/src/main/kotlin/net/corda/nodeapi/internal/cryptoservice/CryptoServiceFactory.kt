package net.corda.nodeapi.internal.cryptoservice

import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.config.FileBasedCertificateStoreSupplier
import net.corda.nodeapi.internal.cryptoservice.azure.AzureKeyVaultCryptoService
import net.corda.nodeapi.internal.cryptoservice.bouncycastle.BCCryptoService
import net.corda.nodeapi.internal.cryptoservice.futurex.FutureXCryptoService
import net.corda.nodeapi.internal.cryptoservice.gemalto.GemaltoLunaCryptoService
import net.corda.nodeapi.internal.cryptoservice.utimaco.UtimacoCryptoService
import java.nio.file.Path

class CryptoServiceFactory {
    companion object {
        fun makeCryptoService(cryptoServiceName: SupportedCryptoServices, legalName: CordaX500Name, signingCertificateStore: FileBasedCertificateStoreSupplier? = null, cryptoServiceConf: Path? = null): CryptoService {
            return when (cryptoServiceName) {
                SupportedCryptoServices.BC_SIMPLE -> {
                    if (signingCertificateStore == null) {
                        throw IllegalArgumentException("A valid signing certificate store is required to create a BouncyCastle crypto service.")
                    }
                    BCCryptoService(legalName.x500Principal, signingCertificateStore)
                }
                SupportedCryptoServices.UTIMACO -> UtimacoCryptoService.fromConfigurationFile(cryptoServiceConf)
                SupportedCryptoServices.GEMALTO_LUNA -> GemaltoLunaCryptoService.fromConfigurationFile(legalName.x500Principal, cryptoServiceConf)
                SupportedCryptoServices.AZURE_KEY_VAULT -> {
                    val configPath = requireNotNull(cryptoServiceConf) { "When cryptoServiceName is set to AZURE_KEY_VAULT, cryptoServiceConf must specify the path to the configuration file."}
                    AzureKeyVaultCryptoService.fromConfigurationFile(configPath)
                }
                SupportedCryptoServices.FUTUREX -> FutureXCryptoService.fromConfigurationFile(legalName.x500Principal, cryptoServiceConf)
            }
        }
    }
}
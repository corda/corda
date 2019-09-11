package net.corda.nodeapi.internal.cryptoservice

import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.config.CryptoServiceConfig
import net.corda.nodeapi.internal.config.FileBasedCertificateStoreSupplier
import net.corda.nodeapi.internal.cryptoservice.azure.AzureKeyVaultCryptoService
import net.corda.nodeapi.internal.cryptoservice.bouncycastle.BCCryptoService
import net.corda.nodeapi.internal.cryptoservice.futurex.FutureXCryptoService
import net.corda.nodeapi.internal.cryptoservice.gemalto.GemaltoLunaCryptoService
import net.corda.nodeapi.internal.cryptoservice.securosys.PrimusXCryptoService
import net.corda.nodeapi.internal.cryptoservice.utimaco.UtimacoCryptoService
import java.nio.file.Path
import java.time.Duration

class CryptoServiceFactory {
    companion object {
        fun makeCryptoService(
                cryptoServiceName: SupportedCryptoServices,
                legalName: CordaX500Name,
                signingCertificateStore: FileBasedCertificateStoreSupplier? = null,
                cryptoServiceConf: Path? = null,
                wrappingKeyStorePath: Path? = null
        ): CryptoService {
            return when (cryptoServiceName) {
                SupportedCryptoServices.BC_SIMPLE -> {
                    if (signingCertificateStore == null) {
                        throw IllegalArgumentException("A valid signing certificate store is required to create a BouncyCastle crypto service.")
                    }
                    BCCryptoService(legalName.x500Principal, signingCertificateStore, wrappingKeyStorePath)
                }
                SupportedCryptoServices.UTIMACO -> UtimacoCryptoService.fromConfigurationFile(cryptoServiceConf)
                SupportedCryptoServices.GEMALTO_LUNA -> GemaltoLunaCryptoService.fromConfigurationFile(legalName.x500Principal, cryptoServiceConf)
                SupportedCryptoServices.AZURE_KEY_VAULT -> {
                    val configPath = requireNotNull(cryptoServiceConf) { "When cryptoServiceName is set to AZURE_KEY_VAULT, cryptoServiceConf must specify the path to the configuration file."}
                    AzureKeyVaultCryptoService.fromConfigurationFile(configPath)
                }
                SupportedCryptoServices.FUTUREX -> FutureXCryptoService.fromConfigurationFile(legalName.x500Principal, cryptoServiceConf)
                SupportedCryptoServices.PRIMUS_X -> PrimusXCryptoService.fromConfigurationFile(legalName.x500Principal, cryptoServiceConf)
            }
        }
        fun makeCryptoService(csConf: CryptoServiceConfig?, legalName: CordaX500Name, keyStoreSupplier: FileBasedCertificateStoreSupplier? = null): CryptoService {
            return makeCryptoService( csConf?.name ?: SupportedCryptoServices.BC_SIMPLE,
                    legalName,
                    keyStoreSupplier,
                    csConf?.conf)
        }
        fun makeManagedCryptoService(
                cryptoServiceName: SupportedCryptoServices,
                legalName: CordaX500Name,
                signingCertificateStore: FileBasedCertificateStoreSupplier? = null,
                cryptoServiceConf: Path? = null,
                timeout: Duration? = null,
                wrappingKeyStorePath: Path? = null
        ): ManagedCryptoService {
            return ManagedCryptoService(makeCryptoService(cryptoServiceName, legalName, signingCertificateStore, cryptoServiceConf, wrappingKeyStorePath), timeout)
        }
    }
}
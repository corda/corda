package net.corda.bridge.services.config

import net.corda.bridge.services.api.CryptoServiceConfig
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.config.CertificateStoreSupplier
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import net.corda.nodeapi.internal.cryptoservice.azure.AzureKeyVaultCryptoService
import net.corda.nodeapi.internal.cryptoservice.bouncycastle.BCCryptoService
import net.corda.nodeapi.internal.cryptoservice.utimaco.UtimacoCryptoService

object CryptoServiceFactory {
    fun get(config: CryptoServiceConfig?, fallbackKeystore: CertificateStoreSupplier? = null): CryptoService {
        // TODO: Move cryptoservice out of Node
        return when (config?.name) {
            SupportedCryptoServices.UTIMACO -> UtimacoCryptoService.fromConfigurationFile(config.conf)
            SupportedCryptoServices.AZURE_KEY_VAULT -> {
                val configPath = requireNotNull(config.conf) { "When cryptoServiceName is set to AZURE_KEY_VAULT, cryptoServiceConf must specify the path to the configuration file." }
                AzureKeyVaultCryptoService.fromConfigurationFile(configPath)
            }
            // The x500 name is only used for generating self signed cert, don't need this in bridge.
            else -> BCCryptoService(CordaX500Name("dummy identity", "London", "GB").x500Principal, requireNotNull(fallbackKeystore) { "Fallback keystore must not be null when Crypto service config is not provided." }) // Pick default BCCryptoService when null.
        }
    }
}
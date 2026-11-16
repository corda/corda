package net.corda.networkbuilder.backends

import com.azure.core.credential.TokenCredential
import com.azure.core.management.profile.AzureProfile
import com.azure.resourcemanager.AzureResourceManager
import com.azure.core.management.AzureEnvironment
import com.azure.core.management.exception.ManagementException
import com.azure.identity.AzureCliCredentialBuilder
import net.corda.networkbuilder.Constants
import net.corda.networkbuilder.containers.instance.azure.AzureInstantiator
import net.corda.networkbuilder.containers.push.azure.AzureContainerPusher
import net.corda.networkbuilder.containers.push.azure.RegistryLocator
import net.corda.networkbuilder.context.Context
import net.corda.networkbuilder.volumes.azure.AzureSmbVolume
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture

data class AzureBackend(override val containerPusher: AzureContainerPusher,
                        override val instantiator: AzureInstantiator,
                        override val volume: AzureSmbVolume) : Backend {

    companion object {

        val LOG = LoggerFactory.getLogger(AzureBackend::class.java)

        private val credential: TokenCredential = AzureCliCredentialBuilder().build()
        private val profile = AzureProfile(AzureEnvironment.AZURE)
        private val azure: AzureResourceManager = AzureResourceManager.configure()
                .authenticate(credential, profile)
                .withDefaultSubscription()

        fun fromContext(context: Context): AzureBackend {
            val resourceGroupName = context.networkName.replace(Constants.ALPHA_NUMERIC_DOT_AND_UNDERSCORE_ONLY_REGEX, "")
            val resourceGroup = try {
                LOG.info("Attempting to find existing resource group with name: $resourceGroupName")
                val existingGroup = azure.resourceGroups().getByName(resourceGroupName)
                LOG.info("Found existing resource group with name $resourceGroupName, reusing")
                existingGroup
            } catch (ex: ManagementException) {
                if (ex.response.statusCode == 404) {
                    LOG.info("No existing resource group with name $resourceGroupName found. Creating new one.")
                    val now = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    azure.resourceGroups().define(resourceGroupName)
                            .withRegion(context.extraParams[Constants.REGION_ARG_NAME])
                            .withTag("CreatedBy", "Corda Network Builder")
                            .withTag("CreatedOn", now)
                            .withTag("NetworkName", context.networkName)
                            .create()
                } else {
                    throw ex
                }
            }

            val registryLocatorFuture = CompletableFuture.supplyAsync {
                RegistryLocator(azure, resourceGroup)
            }
            val containerPusherFuture = registryLocatorFuture.thenApplyAsync {
                AzureContainerPusher(it.registry)
            }
            val azureNetworkStore = CompletableFuture.supplyAsync { AzureSmbVolume(azure, resourceGroup) }
            val azureInstantiatorFuture = azureNetworkStore.thenCombine(registryLocatorFuture) { azureVolume, registryLocator ->
                AzureInstantiator(azure, registryLocator.registry, azureVolume, resourceGroup)
            }
            return AzureBackend(containerPusherFuture.get(), azureInstantiatorFuture.get(), azureNetworkStore.get())
        }
    }
}
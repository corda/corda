package net.corda.bootstrapper.backends

import com.microsoft.azure.CloudException
import com.microsoft.azure.credentials.AzureCliCredentials
import com.microsoft.azure.management.Azure
import com.microsoft.rest.LogLevel
import net.corda.bootstrapper.Constants
import net.corda.bootstrapper.containers.instance.azure.AzureInstantiator
import net.corda.bootstrapper.containers.push.azure.AzureContainerPusher
import net.corda.bootstrapper.containers.push.azure.RegistryLocator
import net.corda.bootstrapper.context.Context
import net.corda.bootstrapper.volumes.azure.AzureSmbVolume
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

data class AzureBackend(override val containerPusher: AzureContainerPusher,
                        override val instantiator: AzureInstantiator,
                        override val volume: AzureSmbVolume) : Backend {

    companion object {

        val LOG = LoggerFactory.getLogger(AzureBackend::class.java)

        private val azure: Azure = kotlin.run {
            Azure.configure()
                    .withLogLevel(LogLevel.NONE)
                    .authenticate(AzureCliCredentials.create())
                    .withDefaultSubscription()
        }

        fun fromContext(context: Context): AzureBackend {
            val resourceGroupName = context.networkName.replace(Constants.ALPHA_NUMERIC_DOT_AND_UNDERSCORE_ONLY_REGEX, "")
            val resourceGroup = try {
                LOG.info("Attempting to find existing resourceGroup with name: $resourceGroupName")
                val foundResourceGroup = azure.resourceGroups().getByName(resourceGroupName)

                if (foundResourceGroup == null) {
                    LOG.info("No existing resourceGroup found creating new resourceGroup with name: $resourceGroupName")
                    azure.resourceGroups().define(resourceGroupName).withRegion(context.extraParams[Constants.REGION_ARG_NAME]).create()
                } else {
                    LOG.info("Found existing resourceGroup, reusing")
                    foundResourceGroup
                }
            } catch (e: CloudException) {
                throw RuntimeException(e)
            }

            val registryLocatorFuture = CompletableFuture.supplyAsync {
                RegistryLocator(azure, resourceGroup)
            }
            val containerPusherFuture = registryLocatorFuture.thenApplyAsync {
                AzureContainerPusher(azure, it.registry)
            }
            val azureNetworkStore = CompletableFuture.supplyAsync { AzureSmbVolume(azure, resourceGroup) }
            val azureInstantiatorFuture = azureNetworkStore.thenCombine(registryLocatorFuture,
                    { azureVolume, registryLocator ->
                        AzureInstantiator(azure, registryLocator.registry, azureVolume, resourceGroup)
                    }
            )
            return AzureBackend(containerPusherFuture.get(), azureInstantiatorFuture.get(), azureNetworkStore.get())
        }
    }
}
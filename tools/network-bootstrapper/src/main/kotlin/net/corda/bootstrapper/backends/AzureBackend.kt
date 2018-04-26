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
import java.util.concurrent.CompletableFuture

data class AzureBackend(override val containerPusher: AzureContainerPusher,
                        override val instantiator: AzureInstantiator,
                        override val volume: AzureSmbVolume) : Backend {

    companion object {
        private val azure: Azure = kotlin.run {
            Azure.configure()
                    .withLogLevel(LogLevel.BASIC)
                    .authenticate(AzureCliCredentials.create())
                    .withDefaultSubscription()
        }

        fun fromContext(context: Context): AzureBackend {
            val resourceGroup = try {
                azure.resourceGroups().getByName(context.safeNetworkName)
                        ?: azure.resourceGroups().define(context.safeNetworkName).withRegion(context.extraParams[Constants.REGION_ARG_NAME]).create()
            } catch (e: CloudException) {
                azure.resourceGroups().define(context.safeNetworkName).withRegion(context.extraParams[Constants.REGION_ARG_NAME]).create()
            }

            val registryLocatorFuture = CompletableFuture.supplyAsync {
                RegistryLocator(azure, context)
            }
            val containerPusherFuture = registryLocatorFuture.thenApplyAsync {
                AzureContainerPusher(azure, it.registry)
            }
            val azureNetworkStore = CompletableFuture.supplyAsync { AzureSmbVolume(azure, context) }
            val azureInstantiatorFuture = azureNetworkStore.thenCombine(registryLocatorFuture,
                    { azureVolume, registryLocator ->
                        AzureInstantiator(azure, registryLocator.registry, azureVolume, context)
                    }
            )
            return AzureBackend(containerPusherFuture.get(), azureInstantiatorFuture.get(), azureNetworkStore.get())
        }
    }
}
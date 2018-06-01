package net.corda.bootstrapper.containers.instance.azure

import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.containerinstance.ContainerGroup
import com.microsoft.azure.management.containerinstance.ContainerGroupRestartPolicy
import com.microsoft.azure.management.containerregistry.Registry
import com.microsoft.azure.management.resources.ResourceGroup
import com.microsoft.rest.ServiceCallback
import net.corda.bootstrapper.Constants.Companion.restFriendlyName
import net.corda.bootstrapper.containers.instance.Instantiator
import net.corda.bootstrapper.containers.instance.Instantiator.Companion.ADDITIONAL_NODE_INFOS_PATH
import net.corda.bootstrapper.containers.push.azure.RegistryLocator.Companion.parseCredentials
import net.corda.bootstrapper.volumes.azure.AzureSmbVolume
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

class AzureInstantiator(private val azure: Azure,
                        private val registry: Registry,
                        private val azureSmbVolume: AzureSmbVolume,
                        private val resourceGroup: ResourceGroup
) : Instantiator {
    override fun instantiateContainer(imageId: String,
                                      portsToOpen: List<Int>,
                                      instanceName: String,
                                      env: Map<String, String>?): CompletableFuture<Pair<String, Map<Int, Int>>> {

        findAndKillExistingContainerGroup(resourceGroup, buildIdent(instanceName))

        LOG.info("Starting instantiation of container: $instanceName using $imageId")
        val registryAddress = registry.loginServerUrl()
        val (username, password) = registry.parseCredentials();
        val mountName = "node-setup"
        val future = CompletableFuture<Pair<String, Map<Int, Int>>>().also {
            azure.containerGroups().define(buildIdent(instanceName))
                    .withRegion(resourceGroup.region())
                    .withExistingResourceGroup(resourceGroup)
                    .withLinux()
                    .withPrivateImageRegistry(registryAddress, username, password)
                    .defineVolume(mountName)
                    .withExistingReadWriteAzureFileShare(azureSmbVolume.shareName)
                    .withStorageAccountName(azureSmbVolume.storageAccountName)
                    .withStorageAccountKey(azureSmbVolume.storageAccountKey)
                    .attach()
                    .defineContainerInstance(instanceName)
                    .withImage(imageId)
                    .withExternalTcpPorts(*portsToOpen.toIntArray())
                    .withVolumeMountSetting(mountName, ADDITIONAL_NODE_INFOS_PATH)
                    .withEnvironmentVariables(env ?: emptyMap())
                    .attach().withRestartPolicy(ContainerGroupRestartPolicy.ON_FAILURE)
                    .withDnsPrefix(buildIdent(instanceName))
                    .createAsync(object : ServiceCallback<ContainerGroup> {
                        override fun failure(t: Throwable?) {
                            it.completeExceptionally(t)
                        }

                        override fun success(result: ContainerGroup) {
                            val fqdn = result.fqdn()
                            LOG.info("Completed instantiation: $instanceName is running at $fqdn with port(s) $portsToOpen exposed")
                            it.complete(result.fqdn() to portsToOpen.map { it to it }.toMap())
                        }
                    })
        }
        return future
    }

    private fun buildIdent(instanceName: String) = "$instanceName-${resourceGroup.restFriendlyName()}"

    override fun getExpectedFQDN(instanceName: String): String {
        return "${buildIdent(instanceName)}.${resourceGroup.region().name()}.azurecontainer.io"
    }

    fun findAndKillExistingContainerGroup(resourceGroup: ResourceGroup, containerName: String): ContainerGroup? {
        val existingContainer = azure.containerGroups().getByResourceGroup(resourceGroup.name(), containerName)
        if (existingContainer != null) {
            LOG.info("Found an existing instance of: $containerName destroying ContainerGroup")
            azure.containerGroups().deleteByResourceGroup(resourceGroup.name(), containerName)
        }
        return existingContainer;
    }

    companion object {
        val LOG = LoggerFactory.getLogger(AzureInstantiator::class.java)
    }

}
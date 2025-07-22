package net.corda.networkbuilder.containers.instance.azure

import com.azure.core.management.exception.ManagementException
import com.azure.resourcemanager.AzureResourceManager
import com.azure.resourcemanager.containerinstance.models.ContainerGroup
import com.azure.resourcemanager.containerinstance.models.ContainerGroupRestartPolicy
import com.azure.resourcemanager.containerregistry.models.Registry
import com.azure.resourcemanager.resources.models.ResourceGroup
import net.corda.networkbuilder.Constants.Companion.restFriendlyName
import net.corda.networkbuilder.containers.instance.Instantiator
import net.corda.networkbuilder.containers.instance.Instantiator.Companion.ADDITIONAL_NODE_INFOS_PATH
import net.corda.networkbuilder.containers.push.azure.RegistryLocator.Companion.parseCredentials
import net.corda.networkbuilder.volumes.azure.AzureSmbVolume
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

class AzureInstantiator(private val azure: AzureResourceManager,
                        private val registry: Registry,
                        private val azureSmbVolume: AzureSmbVolume,
                        private val resourceGroup: ResourceGroup
) : Instantiator {
    private val executor = Executors.newSingleThreadExecutor()

    override fun instantiateContainer(imageId: String,
                                      portsToOpen: List<Int>,
                                      instanceName: String,
                                      env: Map<String, String>?): CompletableFuture<Pair<String, Map<Int, Int>>> {

        findAndKillExistingContainerGroup(resourceGroup, buildIdent(instanceName))

        LOG.info("Starting instantiation of container: $instanceName using $imageId")
        val registryAddress = registry.loginServerUrl()
        val (username, password) = registry.parseCredentials()
        val mountName = "node-setup"
        return CompletableFuture.supplyAsync({
            val containerGroup = azure.containerGroups().define(buildIdent(instanceName))
                    .withRegion(resourceGroup.regionName())
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
                    .attach()
                    .withRestartPolicy(ContainerGroupRestartPolicy.ON_FAILURE)
                    .withDnsPrefix(buildIdent(instanceName))
                    .create()
            val fqdn = containerGroup.fqdn()
            LOG.info("Completed instantiation: $instanceName is running at $fqdn with port(s) $portsToOpen exposed")
            fqdn to portsToOpen.associateWith { it }
        }, executor)
    }

    private fun buildIdent(instanceName: String) = "$instanceName-${resourceGroup.restFriendlyName()}"

    override fun getExpectedFQDN(instanceName: String): String {
        return "${buildIdent(instanceName)}.${resourceGroup.region().name()}.azurecontainer.io"
    }

    fun findAndKillExistingContainerGroup(resourceGroup: ResourceGroup, containerName: String): ContainerGroup? {
        return try {
            val existingContainer = azure.containerGroups().getByResourceGroup(resourceGroup.name(), containerName)
            if (existingContainer != null) {
                LOG.info("Found an existing instance of: $containerName, destroying ContainerGroup")
                azure.containerGroups().deleteByResourceGroup(resourceGroup.name(), containerName)
            }
            existingContainer
        } catch (e: ManagementException) {
            if (e.response.statusCode == 404) {
                LOG.info("No existing container group found for: $containerName")
                null
            } else {
                throw e
            }
        }
    }

    companion object {
        val LOG = LoggerFactory.getLogger(AzureInstantiator::class.java)
    }
}
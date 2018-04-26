package net.corda.bootstrapper.containers.instance.azure

import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.containerinstance.ContainerGroup
import com.microsoft.azure.management.containerinstance.ContainerGroupRestartPolicy
import com.microsoft.azure.management.containerregistry.Registry
import com.microsoft.rest.ServiceCallback
import net.corda.bootstrapper.Constants.Companion.REGION_ARG_NAME
import net.corda.bootstrapper.containers.instance.Instantiator
import net.corda.bootstrapper.containers.instance.Instantiator.Companion.ADDITIONAL_NODE_INFOS_PATH
import net.corda.bootstrapper.containers.push.azure.parseCredentials
import net.corda.bootstrapper.context.Context
import net.corda.bootstrapper.volumes.azure.AzureSmbVolume
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

class AzureInstantiator(private val azure: Azure,
                        private val registry: Registry,
                        private val azureSmbVolume: AzureSmbVolume,
                        private val context: Context
) : Instantiator {
    override fun instantiateContainer(imageId: String,
                                      portsToOpen: List<Int>,
                                      instanceName: String,
                                      env: Map<String, String>?): CompletableFuture<Pair<String, Map<Int, Int>>> {

        findAndKillExistingContainerGroup(context.safeNetworkName, buildIdent(instanceName))

        LOG.info("Starting instantiation of container: $instanceName using $imageId")
        val registryAddress = registry.loginServerUrl()
        val (username, password) = registry.parseCredentials();
        val mountName = "node-setup"
        val future = CompletableFuture<Pair<String, Map<Int, Int>>>().also {
            azure.containerGroups().define(buildIdent(instanceName))
                    .withRegion(context.extraParams[REGION_ARG_NAME])
                    .withExistingResourceGroup(context.safeNetworkName)
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

    private fun buildIdent(instanceName: String) = "$instanceName-${context.networkName}"

    override fun getExpectedFQDN(instanceName: String): String {
        return "${buildIdent(instanceName)}.${context.extraParams[REGION_ARG_NAME]}.azurecontainer.io"
    }

    fun findAndKillExistingContainerGroup(resourceGroup: String, containerName: String): ContainerGroup? {
        val existingContainer = azure.containerGroups().getByResourceGroup(resourceGroup, containerName)
        if (existingContainer != null) {
            LOG.info("Found an existing instance of: $containerName destroying ContainerGroup")
            azure.containerGroups().deleteByResourceGroup(resourceGroup, containerName)
        }
        return existingContainer;
    }

    companion object {
        val LOG = LoggerFactory.getLogger(AzureInstantiator::class.java)
    }

}
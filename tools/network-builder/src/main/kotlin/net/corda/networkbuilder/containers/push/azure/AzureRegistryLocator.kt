package net.corda.networkbuilder.containers.push.azure

import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.containerregistry.AccessKeyType
import com.microsoft.azure.management.containerregistry.Registry
import com.microsoft.azure.management.resources.ResourceGroup
import net.corda.networkbuilder.Constants.Companion.restFriendlyName
import net.corda.networkbuilder.containers.instance.azure.AzureInstantiator
import org.slf4j.LoggerFactory

class RegistryLocator(private val azure: Azure,
                      private val resourceGroup: ResourceGroup) {

    val registry: Registry = locateRegistry()

    private fun locateRegistry(): Registry {
        LOG.info("Attempting to find existing registry with name: ${resourceGroup.restFriendlyName()}")
        val found = azure.containerRegistries().getByResourceGroup(resourceGroup.name(), resourceGroup.restFriendlyName())

        return if (found == null) {
            LOG.info("Did not find existing container registry - creating new registry with name ${resourceGroup.restFriendlyName()}")
            azure.containerRegistries()
                    .define(resourceGroup.restFriendlyName())
                    .withRegion(resourceGroup.region().name())
                    .withExistingResourceGroup(resourceGroup)
                    .withBasicSku()
                    .withRegistryNameAsAdminUser()
                    .create()
        } else {
            LOG.info("found existing registry with name: ${resourceGroup.restFriendlyName()} reusing")
            found
        }
    }

    companion object {
        fun Registry.parseCredentials(): Pair<String, String> {
            val credentials = this.credentials
            return credentials.username() to
                    (credentials.accessKeys()[AccessKeyType.PRIMARY]
                            ?: throw IllegalStateException("no registry password found"))
        }

        val LOG = LoggerFactory.getLogger(AzureInstantiator::class.java)
    }
}
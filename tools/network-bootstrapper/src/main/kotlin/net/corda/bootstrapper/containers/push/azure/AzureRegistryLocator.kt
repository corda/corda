package net.corda.bootstrapper.containers.push.azure

import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.containerregistry.AccessKeyType
import com.microsoft.azure.management.containerregistry.Registry
import com.microsoft.azure.management.resources.ResourceGroup
import net.corda.bootstrapper.Constants.Companion.restFriendlyName

class RegistryLocator(private val azure: Azure,
                      private val resourceGroup: ResourceGroup) {


    val registry: Registry = locateRegistry()


    private fun locateRegistry(): Registry {
        val found = azure.containerRegistries().getByResourceGroup(resourceGroup.name(), resourceGroup.restFriendlyName())
        return found ?: azure.containerRegistries()
                .define(resourceGroup.restFriendlyName())
                .withRegion(resourceGroup.region().name())
                .withExistingResourceGroup(resourceGroup)
                .withBasicSku()
                .withRegistryNameAsAdminUser()
                .create()
    }


}

fun Registry.parseCredentials(): Pair<String, String> {
    val credentials = this.credentials
    return credentials.username() to
            (credentials.accessKeys()[AccessKeyType.PRIMARY]
                    ?: throw IllegalStateException("no registry password found"))
}



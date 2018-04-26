package net.corda.bootstrapper.containers.push.azure

import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.containerregistry.AccessKeyType
import com.microsoft.azure.management.containerregistry.Registry
import net.corda.bootstrapper.Constants
import net.corda.bootstrapper.context.Context

class RegistryLocator(private val azure: Azure,
                      private val context: Context) {


    val registry: Registry = locateRegistry()


    private fun locateRegistry(): Registry {

        val found = azure.containerRegistries().getByResourceGroup(context.safeNetworkName, context.safeNetworkName)


        return found ?: azure.containerRegistries()
                .define(context.safeNetworkName)
                .withRegion(context.extraParams[Constants.REGION_ARG_NAME])
                .withExistingResourceGroup(context.safeNetworkName)
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



package net.corda.bootstrapper.volumes.azure

import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.resources.ResourceGroup
import com.microsoft.azure.management.storage.StorageAccount
import com.microsoft.azure.storage.CloudStorageAccount
import net.corda.bootstrapper.Constants.Companion.restFriendlyName
import net.corda.bootstrapper.notaries.CopiedNotary
import net.corda.bootstrapper.volumes.Volume
import net.corda.bootstrapper.volumes.Volume.Companion.keyPair
import net.corda.bootstrapper.volumes.Volume.Companion.networkMapCert
import net.corda.core.internal.signWithCert
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_FILE_NAME
import org.slf4j.LoggerFactory


class AzureSmbVolume(private val azure: Azure, private val resourceGroup: ResourceGroup) : Volume {

    private val storageAccount = getStorageAccount()

    private val accKeys = storageAccount.keys[0]


    private val cloudFileShare = CloudStorageAccount.parse(
            "DefaultEndpointsProtocol=https;" +
                    "AccountName=${storageAccount.name()};" +
                    "AccountKey=${accKeys.value()};" +
                    "EndpointSuffix=core.windows.net"
    )
            .createCloudFileClient()
            .getShareReference("nodeinfos")

    val networkParamsFolder = cloudFileShare.rootDirectoryReference.getDirectoryReference("network-params")
    val shareName: String = cloudFileShare.name
    val storageAccountName: String
        get() = resourceGroup.restFriendlyName()
    val storageAccountKey: String
        get() = accKeys.value()


    init {
        while (true) {
            try {
                cloudFileShare.createIfNotExists()
                networkParamsFolder.createIfNotExists()
                break
            } catch (e: Throwable) {
                LOG.debug("storage account not ready, waiting")
                Thread.sleep(5000)
            }
        }
    }

    private fun getStorageAccount(): StorageAccount {
        return azure.storageAccounts().getByResourceGroup(resourceGroup.name(), resourceGroup.restFriendlyName())
                ?: azure.storageAccounts().define(resourceGroup.restFriendlyName())
                        .withRegion(resourceGroup.region())
                        .withExistingResourceGroup(resourceGroup)
                        .withAccessFromAllNetworks()
                        .create()
    }

    override fun notariesForNetworkParams(notaries: List<CopiedNotary>) {
        val networkParamsFile = networkParamsFolder.getFileReference(NETWORK_PARAMS_FILE_NAME)
        networkParamsFile.deleteIfExists()
        LOG.info("Storing network-params in AzureFile location: " + networkParamsFile.uri)
        val networkParameters = convertNodeIntoToNetworkParams(notaries.map { it.configFile to it.nodeInfoFile })
        networkParamsFile.uploadFromByteArray(networkParameters.signWithCert(keyPair.private, networkMapCert).serialize().bytes)
    }


    companion object {
        val LOG = LoggerFactory.getLogger(AzureSmbVolume::class.java)
    }

}





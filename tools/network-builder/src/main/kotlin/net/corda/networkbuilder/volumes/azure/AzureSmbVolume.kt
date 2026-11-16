package net.corda.networkbuilder.volumes.azure

import com.azure.core.management.exception.ManagementException
import com.azure.resourcemanager.AzureResourceManager
import com.azure.resourcemanager.resources.models.ResourceGroup
import com.azure.resourcemanager.storage.models.StorageAccount
import com.azure.storage.common.StorageSharedKeyCredential
import com.azure.storage.file.share.ShareDirectoryClient
import com.azure.storage.file.share.ShareServiceClientBuilder
import net.corda.core.internal.signWithCert
import net.corda.core.serialization.serialize
import net.corda.networkbuilder.Constants.Companion.restFriendlyName
import net.corda.networkbuilder.notaries.CopiedNotary
import net.corda.networkbuilder.volumes.Volume
import net.corda.networkbuilder.volumes.Volume.Companion.keyPair
import net.corda.networkbuilder.volumes.Volume.Companion.networkMapCert
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_FILE_NAME
import org.slf4j.LoggerFactory
import java.time.Instant

class AzureSmbVolume(private val azure: AzureResourceManager, private val resourceGroup: ResourceGroup) : Volume {

    private val storageAccount = getStorageAccount()

    private val accKeys = storageAccount.keys[0]
    private val credential = StorageSharedKeyCredential(storageAccount.name(), storageAccountKey)
    private val shareClient = ShareServiceClientBuilder()
            .endpoint("https://${storageAccount.name()}.file.core.windows.net")
            .credential(credential)
            .buildClient()
            .getShareClient("nodeinfos")
            .apply { createIfNotExists() }

    var networkParamsFolder: ShareDirectoryClient
    val shareName: String = shareClient.shareName
    val storageAccountName: String
        get() = resourceGroup.restFriendlyName() + "storageacc"
    val storageAccountKey: String
        get() = accKeys.value()

    init {
        while (true) {
            try {
                shareClient.createIfNotExists()
                val folder = shareClient.rootDirectoryClient
                        .getSubdirectoryClient("network-params")
                folder.createIfNotExists()
                if (folder.exists()) {
                    networkParamsFolder = folder
                    break
                } else {
                    LOG.debug("'network-params' folder does not yet exist, retrying...")
                }
            } catch (e: Exception) {
                LOG.debug("Storage account or file share not ready, waiting: ${e.message}")
                Thread.sleep(5000)
            }
        }
    }

    private fun getStorageAccount(): StorageAccount {
        val existing = try {
            azure.storageAccounts().getByResourceGroup(
                    resourceGroup.name(),
                    resourceGroup.restFriendlyName()
            )
        } catch (ex: ManagementException) {
            if (ex.response.statusCode == 404) {
                null
            } else {
                throw ex
            }
        }

        return existing ?: run {
            azure.storageAccounts()
                    .define(storageAccountName)
                    .withRegion(resourceGroup.region())
                    .withExistingResourceGroup(resourceGroup)
                    .withAccessFromAllNetworks()
                    .withTags(mapOf(
                            "createdBy" to "Corda Network Builder",
                            "createdAt" to Instant.now().toString()
                    ))
                    .create()
        }
    }

    override fun notariesForNetworkParams(notaries: List<CopiedNotary>) {
        val networkParamsFile = networkParamsFolder.getFileClient(NETWORK_PARAMS_FILE_NAME)
        networkParamsFile.deleteIfExists()
        LOG.info("Storing network-params in AzureFile location: ${networkParamsFile.fileUrl}")
        val networkParameters = convertNodeIntoToNetworkParams(notaries.map { it.configFile to it.nodeInfoFile })
        networkParamsFile.uploadFromByteArray(networkParameters.signWithCert(keyPair.private, networkMapCert).serialize().bytes)
    }

    companion object {
        val LOG = LoggerFactory.getLogger(AzureSmbVolume::class.java)
    }
}
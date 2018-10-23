package net.corda.testing.internal

import net.corda.core.contracts.ContractClassName
import net.corda.core.cordapp.Cordapp
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.DEPLOYED_CORDAPP_UPLOADER
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.AttachmentStorage
import net.corda.node.cordapp.CordappLoader
import net.corda.node.internal.cordapp.CordappProviderImpl
import net.corda.testing.services.MockAttachmentStorage
import java.nio.file.Paths
import java.security.PublicKey

class MockCordappProvider(
        cordappLoader: CordappLoader,
        attachmentStorage: AttachmentStorage,
        cordappConfigProvider: MockCordappConfigProvider = MockCordappConfigProvider()
) : CordappProviderImpl(cordappLoader, cordappConfigProvider, attachmentStorage) {

    private val cordappRegistry = mutableListOf<Pair<Cordapp, AttachmentId>>()

    fun addMockCordapp(contractClassName: ContractClassName, attachments: MockAttachmentStorage, contractHash: AttachmentId? = null, signers: List<PublicKey> = emptyList()): AttachmentId {
        val cordapp = CordappImpl(
                contractClassNames = listOf(contractClassName),
                initiatedFlows = emptyList(),
                rpcFlows = emptyList(),
                serviceFlows = emptyList(),
                schedulableFlows = emptyList(),
                services = emptyList(),
                serializationWhitelists = emptyList(),
                serializationCustomSerializers = emptyList(),
                customSchemas = emptySet(),
                jarPath = Paths.get("").toUri().toURL(),
                info = CordappImpl.Info.UNKNOWN,
                allFlows = emptyList(),
                jarHash = SecureHash.allOnesHash)
        if (cordappRegistry.none { it.first.contractClassNames.contains(contractClassName) && it.second == contractHash }) {
            cordappRegistry.add(Pair(cordapp, findOrImportAttachment(listOf(contractClassName), contractClassName.toByteArray(), attachments, contractHash, signers)))
        }
        return cordappRegistry.findLast { contractClassName in it.first.contractClassNames }?.second!!
    }

    override fun getContractAttachmentID(contractClassName: ContractClassName): AttachmentId? = cordappRegistry.find { it.first.contractClassNames.contains(contractClassName) }?.second
            ?: super.getContractAttachmentID(contractClassName)

    private fun findOrImportAttachment(contractClassNames: List<ContractClassName>, data: ByteArray, attachments: MockAttachmentStorage, contractHash: AttachmentId?, signers: List<PublicKey>): AttachmentId {
        val existingAttachment = attachments.files.filter { (attachmentId, content) ->
            contractHash == attachmentId
        }
        return if (!existingAttachment.isEmpty()) {
            existingAttachment.keys.first()
        } else {
            attachments.importContractAttachment(contractClassNames, DEPLOYED_CORDAPP_UPLOADER, data.inputStream(), contractHash, signers)
        }
    }
}

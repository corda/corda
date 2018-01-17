package net.corda.testing.services

import net.corda.core.contracts.ContractClassName
import net.corda.core.cordapp.Cordapp
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.AttachmentStorage
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.internal.cordapp.CordappProviderImpl
import net.corda.testing.node.MockCordappConfigProvider
import java.nio.file.Paths
import java.util.*

class MockCordappProvider(
        cordappLoader: CordappLoader,
        attachmentStorage: AttachmentStorage,
        val cordappConfigProvder: MockCordappConfigProvider = MockCordappConfigProvider()
) : CordappProviderImpl(cordappLoader, cordappConfigProvder, attachmentStorage) {
    val cordappRegistry = mutableListOf<Pair<Cordapp, AttachmentId>>()

    fun addMockCordapp(contractClassName: ContractClassName, attachments: MockAttachmentStorage) {
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
                jarPath = Paths.get(".").toUri().toURL())
        if (cordappRegistry.none { it.first.contractClassNames.contains(contractClassName) }) {
            cordappRegistry.add(Pair(cordapp, findOrImportAttachment(contractClassName.toByteArray(), attachments)))
        }
    }

    override fun getContractAttachmentID(contractClassName: ContractClassName): AttachmentId? = cordappRegistry.find { it.first.contractClassNames.contains(contractClassName) }?.second ?: super.getContractAttachmentID(contractClassName)

    private fun findOrImportAttachment(data: ByteArray, attachments: MockAttachmentStorage): AttachmentId {
        val existingAttachment = attachments.files.filter {
            Arrays.equals(it.value, data)
        }
        return if (!existingAttachment.isEmpty()) {
            existingAttachment.keys.first()
        } else {
            attachments.importAttachment(data.inputStream())
        }
    }
}

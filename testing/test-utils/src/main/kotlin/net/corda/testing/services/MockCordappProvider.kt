package net.corda.testing.services

import net.corda.core.contracts.ContractClassName
import net.corda.core.cordapp.Cordapp
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.AttachmentStorage
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.internal.cordapp.CordappProviderImpl
import net.corda.nodeapi.internal.network.NetworkParameters
import net.corda.testing.common.internal.testNetworkParameters
import java.nio.file.Paths
import java.util.*

class MockCordappProvider(cordappLoader: CordappLoader, attachmentStorage: AttachmentStorage, networkParameters: NetworkParameters = testNetworkParameters(emptyList())) : CordappProviderImpl(cordappLoader, attachmentStorage, networkParameters) {
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
            cordappRegistry.add(Pair(cordapp, findOrImportAttachment(listOf(contractClassName) , contractClassName.toByteArray(), attachments)))
        }
    }

    private fun findOrImportAttachment(contractClassNames: List<ContractClassName>, data: ByteArray, attachments: MockAttachmentStorage): AttachmentId {
        val existingAttachment = attachments.files.filter {
            Arrays.equals(it.value.second, data)
        }
        return if (!existingAttachment.isEmpty()) {
            existingAttachment.keys.first()
        } else {
            attachments.importOrGetContractAttachment(contractClassNames, data.inputStream())
        }
    }
}

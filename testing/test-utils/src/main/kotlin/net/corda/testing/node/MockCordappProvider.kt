package net.corda.testing.node

import net.corda.core.contracts.ContractClassName
import net.corda.core.cordapp.Cordapp
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.AttachmentId
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.internal.cordapp.CordappProviderImpl
import java.nio.file.Paths
import java.util.*

class MockCordappProvider(cordappLoader: CordappLoader) : CordappProviderImpl(cordappLoader) {
    val cordappRegistry = mutableListOf<Pair<Cordapp, AttachmentId>>()

    fun addMockCordapp(contractClassName: ContractClassName, services: ServiceHub) {
        val cordapp = CordappImpl(listOf(contractClassName), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptySet(), Paths.get(".").toUri().toURL())
        if (cordappRegistry.none { it.first.contractClassNames.contains(contractClassName) }) {
            cordappRegistry.add(Pair(cordapp, findOrImportAttachment(contractClassName.toByteArray(), services)))
        }
    }

    override fun getContractAttachmentID(contractClassName: ContractClassName): AttachmentId? = cordappRegistry.find { it.first.contractClassNames.contains(contractClassName) }?.second ?: super.getContractAttachmentID(contractClassName)

    private fun findOrImportAttachment(data: ByteArray, services: ServiceHub): AttachmentId {
        return if (services.attachments is MockAttachmentStorage) {
            val existingAttachment = (services.attachments as MockAttachmentStorage).files.filter {
                Arrays.equals(it.value, data)
            }
            if (!existingAttachment.isEmpty()) {
                existingAttachment.keys.first()
            } else {
                services.attachments.importAttachment(data.inputStream())
            }
        } else {
            throw Exception("MockCordappService only requires MockAttachmentStorage")
        }
    }
}

package net.corda.testing.internal

import net.corda.core.contracts.ContractClassName
import net.corda.core.cordapp.Cordapp
import net.corda.core.internal.DEPLOYED_CORDAPP_UPLOADER
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.AttachmentStorage
import net.corda.nodeapi.internal.cordapp.CordappLoader
import net.corda.node.internal.cordapp.CordappProviderImpl
import net.corda.testing.services.MockAttachmentStorage
import java.security.PublicKey
import java.util.jar.Attributes

class MockCordappProvider(
        cordappLoader: CordappLoader,
        attachmentStorage: AttachmentStorage,
        cordappConfigProvider: MockCordappConfigProvider = MockCordappConfigProvider()
) : CordappProviderImpl(cordappLoader, cordappConfigProvider, attachmentStorage) {

    private val cordappRegistry = mutableListOf<Pair<Cordapp, AttachmentId>>()

    fun addMockCordapp(
            contractClassName: ContractClassName,
            attachments: MockAttachmentStorage,
            contractHash: AttachmentId? = null,
            signers: List<PublicKey> = emptyList(),
            jarManifestAttributes: Map<String,String> = emptyMap()
    ): AttachmentId {
        val cordapp = CordappImpl.TEST_INSTANCE.copy(contractClassNames = listOf(contractClassName))
        val jarManifestAttributesWithObligatoryElement = jarManifestAttributes.toMutableMap()
        jarManifestAttributesWithObligatoryElement.putIfAbsent(Attributes.Name.MANIFEST_VERSION.toString(), "1.0")
        if (cordappRegistry.none { it.first.contractClassNames.contains(contractClassName) && it.second == contractHash }) {
            cordappRegistry.add(Pair(
                    cordapp,
                    findOrImportAttachment(
                            listOf(contractClassName),
                            fakeAttachmentCached(contractClassName, jarManifestAttributesWithObligatoryElement),
                            attachments,
                            contractHash,
                            signers
                    )
            ))
        }
        return cordappRegistry.findLast { contractClassName in it.first.contractClassNames }?.second!!
    }

    override fun getContractAttachmentID(contractClassName: ContractClassName): AttachmentId? {
        return cordappRegistry.find { it.first.contractClassNames.contains(contractClassName) }?.second
                ?: super.getContractAttachmentID(contractClassName)
    }

    private fun findOrImportAttachment(
            contractClassNames: List<ContractClassName>,
            data: ByteArray,
            attachments: MockAttachmentStorage,
            contractHash: AttachmentId?,
            signers: List<PublicKey>
    ): AttachmentId {
        val existingAttachment = attachments.files.filter { (attachmentId, _) ->
            contractHash == attachmentId
        }
        return if (!existingAttachment.isEmpty()) {
            existingAttachment.keys.first()
        } else {
            attachments.importContractAttachment(contractClassNames, DEPLOYED_CORDAPP_UPLOADER, data.inputStream(), contractHash, signers)
        }
    }

    private val attachmentsCache = mutableMapOf<String, ByteArray>()
    private fun fakeAttachmentCached(contractClass: String, manifestAttributes: Map<String,String> = emptyMap()): ByteArray {
        return attachmentsCache.computeIfAbsent(contractClass + manifestAttributes.toSortedMap()) {
            fakeAttachment(contractClass.replace('.', '/') + ".class", "fake class file for $contractClass", manifestAttributes)
        }
    }
}

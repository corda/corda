package net.corda.testing.services

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractClassName
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.AbstractAttachment
import net.corda.core.internal.UNKNOWN_UPLOADER
import net.corda.core.internal.readFully
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.vault.AttachmentQueryCriteria
import net.corda.core.node.services.vault.AttachmentSort
import net.corda.core.node.services.vault.Builder
import net.corda.core.node.services.vault.ColumnPredicate
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.nodeapi.internal.withContractsInJar
import java.io.InputStream
import java.security.PublicKey
import java.util.*
import java.util.jar.Attributes
import java.util.jar.JarInputStream

/**
 * A mock implementation of [AttachmentStorage] for use within tests
 */
class MockAttachmentStorage : AttachmentStorage, SingletonSerializeAsToken() {

    private data class ContractAttachmentMetadata(val name: ContractClassName, val version: Int, val isSigned: Boolean)

    private val _files = HashMap<SecureHash, Pair<Attachment, ByteArray>>()
    private val _contractClasses = HashMap<ContractAttachmentMetadata, SecureHash>()
    /** A map of the currently stored files by their [SecureHash] */
    val files: Map<SecureHash, Pair<Attachment, ByteArray>> get() = _files

    @Suppress("OverridingDeprecatedMember")
    override fun importAttachment(jar: InputStream): AttachmentId = importAttachment(jar, UNKNOWN_UPLOADER, null)

    override fun importAttachment(jar: InputStream, uploader: String, filename: String?): AttachmentId {
        return withContractsInJar(jar) { contractClassNames, inputStream ->
            importAttachmentInternal(inputStream, uploader, contractClassNames)
        }
    }

    override fun openAttachment(id: SecureHash): Attachment? = files[id]?.first

    override fun queryAttachments(criteria: AttachmentQueryCriteria, sorting: AttachmentSort?): List<SecureHash> {
        criteria as AttachmentQueryCriteria.AttachmentsQueryCriteria
        val contractClassNames =
                if (criteria.contractClassNamesCondition is ColumnPredicate.EqualityComparison)
                    (criteria.contractClassNamesCondition as ColumnPredicate.EqualityComparison<List<ContractClassName>>).rightLiteral
                else emptyList()
        val contractMetadataList =
            if (criteria.isSignedCondition != null) {
                val isSigned = criteria.isSignedCondition == Builder.equal(true)
                 contractClassNames.map {contractClassName ->
                    ContractAttachmentMetadata(contractClassName, 1, isSigned)
                }
            }
            else {
                contractClassNames.flatMap { contractClassName ->
                    listOf(ContractAttachmentMetadata(contractClassName, 1, false),
                     ContractAttachmentMetadata(contractClassName, 1, true))
                }
            }

        return _contractClasses.filterKeys { contractMetadataList.contains(it) }.values.toList()
    }

    override fun hasAttachment(attachmentId: AttachmentId) = files.containsKey(attachmentId)

    @Suppress("OverridingDeprecatedMember")
    override fun importOrGetAttachment(jar: InputStream): AttachmentId {
        return try {
            importAttachment(jar, UNKNOWN_UPLOADER, null)
        } catch (e: java.nio.file.FileAlreadyExistsException) {
            AttachmentId.parse(e.message!!)
        }
    }

    @JvmOverloads
    fun importContractAttachment(contractClassNames: List<ContractClassName>, uploader: String, jar: InputStream, attachmentId: AttachmentId? = null,  signers: List<PublicKey> = emptyList()): AttachmentId = importAttachmentInternal(jar, uploader, contractClassNames, attachmentId, signers)

    fun importContractAttachment(attachmentId: AttachmentId, contractAttachment: ContractAttachment) {
        _files[attachmentId] = Pair(contractAttachment, ByteArray(1))
    }

    fun getAttachmentIdAndBytes(jar: InputStream): Pair<AttachmentId, ByteArray> = jar.readFully().let { bytes -> Pair(bytes.sha256(), bytes) }

    private class MockAttachment(dataLoader: () -> ByteArray, override val id: SecureHash, override val signerKeys: List<PublicKey>) : AbstractAttachment(dataLoader)

    private fun importAttachmentInternal(jar: InputStream, uploader: String, contractClassNames: List<ContractClassName>? = null, attachmentId: AttachmentId? = null, signers: List<PublicKey> = emptyList()): AttachmentId {
        // JIS makes read()/readBytes() return bytes of the current file, but we want to hash the entire container here.
        require(jar !is JarInputStream)

        val bytes = jar.readFully()

        val sha256 = attachmentId ?: bytes.sha256()
        if (sha256 !in files.keys) {
            val baseAttachment = MockAttachment({ bytes }, sha256, signers)
            val version = try { Integer.parseInt(baseAttachment.openAsJAR().manifest?.mainAttributes?.getValue(Attributes.Name.IMPLEMENTATION_VERSION) ?: "1") } catch (e: Exception) { 1 }
            val attachment =
                    if (contractClassNames == null || contractClassNames.isEmpty()) baseAttachment
                    else {
                        contractClassNames.map {contractClassName ->
                            val contractClassMetadata = ContractAttachmentMetadata(contractClassName, version, signers.isNotEmpty())
                            _contractClasses[contractClassMetadata] = sha256
                        }
                        ContractAttachment(baseAttachment, contractClassNames.first(), contractClassNames.toSet(), uploader, signers, version)
                    }
            _files[sha256] = Pair(attachment, bytes)
        }
        return sha256
    }
}
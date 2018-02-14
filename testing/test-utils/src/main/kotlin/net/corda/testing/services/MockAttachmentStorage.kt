package net.corda.testing.services

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractClassName
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.AbstractAttachment
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.vault.AttachmentQueryCriteria
import net.corda.core.node.services.vault.AttachmentSort
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.nodeapi.internal.withContractsInJar
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*
import java.util.jar.JarInputStream

class MockAttachmentStorage : AttachmentStorage, SingletonSerializeAsToken() {
    companion object {
        fun getBytes(jar: InputStream) = run {
            val s = ByteArrayOutputStream()
            jar.copyTo(s)
            s.close()
            s.toByteArray()
        }
    }

    val files = HashMap<SecureHash, Pair<Attachment, ByteArray>>()

    override fun importAttachment(jar: InputStream): AttachmentId = withContractsInJar(jar) { contracts, inputStream -> importAttachmentInternal(inputStream, contracts) }

    fun importContractAttachment(contractClassNames: List<ContractClassName>, jar: InputStream): AttachmentId = importAttachmentInternal(jar, contractClassNames)

    override fun importAttachment(jar: InputStream, uploader: String, filename: String): AttachmentId {
        return importAttachment(jar)
    }

    override fun openAttachment(id: SecureHash): Attachment? = files[id]?.first

    override fun queryAttachments(criteria: AttachmentQueryCriteria, sorting: AttachmentSort?): List<AttachmentId> {
        throw NotImplementedError("Querying for attachments not implemented")
    }

    override fun hasAttachment(attachmentId: AttachmentId) = files.containsKey(attachmentId)

    override fun importOrGetAttachment(jar: InputStream): AttachmentId {
        try {
            return importAttachment(jar)
        } catch (faee: java.nio.file.FileAlreadyExistsException) {
            return AttachmentId.parse(faee.message!!)
        }
    }

    fun getAttachmentIdAndBytes(jar: InputStream): Pair<AttachmentId, ByteArray> = getBytes(jar).let { bytes -> Pair(bytes.sha256(), bytes) }

    private fun importAttachmentInternal(jar: InputStream, contractClassNames: List<ContractClassName>? = null): AttachmentId {
        // JIS makes read()/readBytes() return bytes of the current file, but we want to hash the entire container here.
        require(jar !is JarInputStream)

        val bytes = getBytes(jar)

        val sha256 = bytes.sha256()
        if (sha256 !in files.keys) {
            val baseAttachment = object : AbstractAttachment({ bytes }) {
                override val id = sha256
            }
            val attachment = if (contractClassNames == null) baseAttachment else ContractAttachment(baseAttachment, contractClassNames.first(), contractClassNames.toSet())
            files[sha256] = Pair(attachment, bytes)
        }
        return sha256
    }

}

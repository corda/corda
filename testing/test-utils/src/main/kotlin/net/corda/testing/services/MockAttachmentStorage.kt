package net.corda.testing.services

import com.google.common.hash.Hashing
import com.google.common.hash.HashingInputStream
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
import net.corda.node.services.persistence.NodeAttachmentService
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
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

    override fun importAttachment(jar: InputStream): AttachmentId {
        // JIS makes read()/readBytes() return bytes of the current file, but we want to hash the entire container here.
        require(jar !is JarInputStream)

        val bytes = getBytes(jar)

        val sha256 = bytes.sha256()
        if (!files.containsKey(sha256)) {
            files[sha256] = Pair(object : AbstractAttachment({ bytes }) {
                override val id = sha256
            }, bytes)
        }
        return sha256
    }

    override fun importAttachment(jar: InputStream, uploader: String, filename: String): AttachmentId {
        return importAttachment(jar)
    }

    val files = HashMap<SecureHash, Pair<Attachment, ByteArray>>()

    override fun openAttachment(id: SecureHash): Attachment? {
        val f = files[id] ?: return null
        return f.first
    }

    override fun queryAttachments(criteria: AttachmentQueryCriteria, sorting: AttachmentSort?): List<AttachmentId> {
        throw NotImplementedError("Querying for attachments not implemented")
    }

    override fun hasAttachment(attachmentId: AttachmentId) = files.containsKey(attachmentId)

    fun getAttachmentIdAndBytes(jar: InputStream): Pair<AttachmentId, ByteArray> {
        val bytes = getBytes(jar)
        return Pair(bytes.sha256(), bytes)
    }

    override fun importOrGetAttachment(jar: InputStream): AttachmentId {
        try {
            return importAttachment(jar)
        } catch (faee: java.nio.file.FileAlreadyExistsException) {
            return AttachmentId.parse(faee.message!!)
        }
    }


    override fun importContractAttachment(contractClassNames: List<ContractClassName>, jar: InputStream): AttachmentId {
        // JIS makes read()/readBytes() return bytes of the current file, but we want to hash the entire container here.
        require(jar !is JarInputStream)
        val hs = HashingInputStream(Hashing.sha256(), jar)
        val bytes = hs.readBytes()
        val id = SecureHash.SHA256(hs.hash().asBytes())

        if (!files.containsKey(id)) {
            files[id] = Pair(ContractAttachment(object : AbstractAttachment({ bytes }) {
                override val id = id
            }, contractClassNames.first(), contractClassNames.toSet()), bytes)
        }
        return id

    }

}

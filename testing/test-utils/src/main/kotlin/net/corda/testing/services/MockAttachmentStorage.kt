/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.nodeapi.internal.withContractsInJar
import java.io.InputStream
import java.util.*
import java.util.jar.JarInputStream

/**
 * A mock implementation of [AttachmentStorage] for use within tests
 */
class MockAttachmentStorage : AttachmentStorage, SingletonSerializeAsToken() {

    private val _files = HashMap<SecureHash, Pair<Attachment, ByteArray>>()
    /** A map of the currently stored files by their [SecureHash] */
    val files: Map<SecureHash, Pair<Attachment, ByteArray>> get() = _files

    override fun importAttachment(jar: InputStream): AttachmentId = importAttachment(jar, UNKNOWN_UPLOADER, null)

    override fun importAttachment(jar: InputStream, uploader: String, filename: String?): AttachmentId {
        return withContractsInJar(jar) { contractClassNames, inputStream ->
            importAttachmentInternal(inputStream, uploader, filename, contractClassNames)
        }
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

    fun importContractAttachment(contractClassNames: List<ContractClassName>, uploader: String, jar: InputStream): AttachmentId = importAttachmentInternal(jar, uploader, null, contractClassNames)

    fun getAttachmentIdAndBytes(jar: InputStream): Pair<AttachmentId, ByteArray> = jar.readFully().let { bytes -> Pair(bytes.sha256(), bytes) }

    private class MockAttachment(dataLoader: () -> ByteArray, override val id: SecureHash) : AbstractAttachment(dataLoader)

    private fun importAttachmentInternal(jar: InputStream, uploader: String, filename: String?, contractClassNames: List<ContractClassName>? = null): AttachmentId {
        // JIS makes read()/readBytes() return bytes of the current file, but we want to hash the entire container here.
        require(jar !is JarInputStream)

        val bytes = jar.readFully()

        val sha256 = bytes.sha256()
        if (sha256 !in files.keys) {
            val baseAttachment = MockAttachment({ bytes }, sha256)
            val attachment = if (contractClassNames == null || contractClassNames.isEmpty()) baseAttachment else ContractAttachment(baseAttachment, contractClassNames.first(), contractClassNames.toSet(), uploader)
            _files[sha256] = Pair(attachment, bytes)
        }
        return sha256
    }
}
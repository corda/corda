package net.corda.node.services.persistence

import com.codahale.metrics.MetricRegistry
import net.corda.core.internal.VisibleForTesting
import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.google.common.hash.HashingInputStream
import com.google.common.io.CountingInputStream
import net.corda.core.CordaRuntimeException
import net.corda.core.internal.AbstractAttachment
import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.serialization.*
import net.corda.core.utilities.loggerFor
import net.corda.node.utilities.DatabaseTransactionManager
import net.corda.node.utilities.NODE_DATABASE_PREFIX
import java.io.*
import java.nio.file.Paths
import java.util.jar.JarInputStream
import javax.annotation.concurrent.ThreadSafe
import javax.persistence.*

/**
 * Stores attachments using Hibernate to database.
 */
@ThreadSafe
class NodeAttachmentService(metrics: MetricRegistry) : AttachmentStorage, SingletonSerializeAsToken() {

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}attachments",
           indexes = arrayOf(Index(name = "att_id_idx", columnList = "att_id")))
    class DBAttachment(
            @Id
            @Column(name = "att_id", length = 65535)
            var attId: String,

            @Column(name = "content")
            @Lob
            var content: ByteArray
    ) : Serializable

    companion object {
        private val log = loggerFor<NodeAttachmentService>()
    }

    @VisibleForTesting
    var checkAttachmentsOnLoad = true

    private val attachmentCount = metrics.counter("Attachments")

    init {
        val session = DatabaseTransactionManager.current().session
        val criteriaBuilder = session.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(Long::class.java)
        criteriaQuery.select(criteriaBuilder.count(criteriaQuery.from(NodeAttachmentService.DBAttachment::class.java)))
        val count = session.createQuery(criteriaQuery).singleResult
        attachmentCount.inc(count)
    }

    @CordaSerializable
    class HashMismatchException(val expected: SecureHash, val actual: SecureHash) : CordaRuntimeException("File $expected hashed to $actual: corruption in attachment store?")

    /**
     * Wraps a stream and hashes data as it is read: if the entire stream is consumed, then at the end the hash of
     * the read data is compared to the [expected] hash and [HashMismatchException] is thrown by either [read] or [close]
     * if they didn't match. The goal of this is to detect cases where attachments in the store have been tampered with
     * or corrupted and no longer match their file name. It won't always work: if we read a zip for our own uses and skip
     * around inside it, we haven't read the whole file, so we can't check the hash. But when copying it over the network
     * this will provide an additional safety check against user error.
     */
    @VisibleForTesting @CordaSerializable
    class HashCheckingStream(val expected: SecureHash.SHA256,
                             val expectedSize: Int,
                             input: InputStream,
                             private val counter: CountingInputStream = CountingInputStream(input),
                             private val stream: HashingInputStream = HashingInputStream(Hashing.sha256(), counter)) : FilterInputStream(stream) {
        @Throws(IOException::class)
        override fun close() {
            super.close()
            validate()
        }

        // Possibly not used, but implemented anyway to fulfil the [FilterInputStream] contract.
        @Throws(IOException::class)
        override fun read(): Int {
            return super.read().apply {
                if (this == -1) {
                    validate()
                }
            }
        }

        // This is invoked by [InputStreamSerializer], which does NOT close the stream afterwards.
        @Throws(IOException::class)
        override fun read(b: ByteArray?, off: Int, len: Int): Int {
            return super.read(b, off, len).apply {
                if (this == -1) {
                    validate()
                }
            }
        }

        private fun validate() {
            if (counter.count != expectedSize.toLong()) return

            val actual = SecureHash.SHA256(hash.asBytes())
            if (actual != expected)
                throw HashMismatchException(expected, actual)
        }

        private var _hash: HashCode? = null // Backing field for hash property
        private val hash: HashCode get() {
            var h = _hash
            return if (h == null) {
                h = stream.hash()
                _hash = h
                h
            } else {
                h
            }
        }
    }

    private class AttachmentImpl(override val id: SecureHash, dataLoader: () -> ByteArray, private val checkOnLoad: Boolean) : AbstractAttachment(dataLoader), SerializeAsToken {
        override fun open(): InputStream {
            val stream = super.open()
            // This is just an optional safety check. If it slows things down too much it can be disabled.
            return if (checkOnLoad && id is SecureHash.SHA256) HashCheckingStream(id, attachmentData.size, stream) else stream
        }

        private class Token(private val id: SecureHash, private val checkOnLoad: Boolean) : SerializationToken {
            override fun fromToken(context: SerializeAsTokenContext) = AttachmentImpl(id, context.attachmentDataLoader(id), checkOnLoad)
        }

        override fun toToken(context: SerializeAsTokenContext) = Token(id, checkOnLoad)

    }

    override fun openAttachment(id: SecureHash): Attachment? {
        val attachment = DatabaseTransactionManager.current().session.get(NodeAttachmentService.DBAttachment::class.java, id.toString())
        attachment?.let {
            return AttachmentImpl(id, { attachment.content }, checkAttachmentsOnLoad)
        }
        return null
    }

    // TODO: PLT-147: The attachment should be randomised to prevent brute force guessing and thus privacy leaks.
    override fun importAttachment(jar: InputStream): SecureHash {
        require(jar !is JarInputStream)

        // Read the file into RAM, hashing it to find the ID as we go. The attachment must fit into memory.
        // TODO: Switch to a two-phase insert so we can handle attachments larger than RAM.
        // To do this we must pipe stream into the database without knowing its hash, which we will learn only once
        // the insert/upload is complete. We can then query to see if it's a duplicate and if so, erase, and if not
        // set the hash field of the new attachment record.
        val hs = HashingInputStream(Hashing.sha256(), jar)
        val bytes = hs.readBytes()
        checkIsAValidJAR(ByteArrayInputStream(bytes))
        val id = SecureHash.SHA256(hs.hash().asBytes())

        val session = DatabaseTransactionManager.current().session
        val criteriaBuilder = session.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(Long::class.java)
        val attachments = criteriaQuery.from(NodeAttachmentService.DBAttachment::class.java)
        criteriaQuery.select(criteriaBuilder.count(criteriaQuery.from(NodeAttachmentService.DBAttachment::class.java)))
        criteriaQuery.where(criteriaBuilder.equal(attachments.get<String>(DBAttachment::attId.name), id.toString()))
        val count = session.createQuery(criteriaQuery).singleResult
        if (count == 0L) {
            val attachment = NodeAttachmentService.DBAttachment(attId = id.toString(), content = bytes)
            session.save(attachment)

            attachmentCount.inc()
            log.info("Stored new attachment $id")
        }

        return id
    }

    private fun checkIsAValidJAR(stream: InputStream) {
        // Just iterate over the entries with verification enabled: should be good enough to catch mistakes.
        // Note that JarInputStream won't throw any kind of error at all if the file stream is in fact not
        // a ZIP! It'll just pretend it's an empty archive, which is kind of stupid but that's how it works.
        // So we have to check to ensure we found at least one item.
        val jar = JarInputStream(stream, true)
        var count = 0
        while (true) {
            val cursor = jar.nextJarEntry ?: break
            val entryPath = Paths.get(cursor.name)
            // Security check to stop zips trying to escape their rightful place.
            require(!entryPath.isAbsolute) { "Path $entryPath is absolute" }
            require(entryPath.normalize() == entryPath) { "Path $entryPath is not normalised" }
            require(!('\\' in cursor.name || cursor.name == "." || cursor.name == "..")) { "Bad character in $entryPath" }
            count++
        }
        require(count > 0) { "Stream is either empty or not a JAR/ZIP" }
    }
}

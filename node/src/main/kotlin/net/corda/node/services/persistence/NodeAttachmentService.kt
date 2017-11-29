package net.corda.node.services.persistence

import com.codahale.metrics.MetricRegistry
import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.google.common.hash.HashingInputStream
import com.google.common.io.CountingInputStream
import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.AbstractAttachment
import net.corda.core.internal.VisibleForTesting
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.vault.AttachmentQueryCriteria
import net.corda.core.node.services.vault.AttachmentSort
import net.corda.core.serialization.*
import net.corda.core.utilities.contextLogger
import net.corda.node.services.vault.HibernateAttachmentQueryCriteriaParser
import net.corda.nodeapi.internal.persistence.DatabaseTransactionManager
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.currentDBSession
import java.io.*
import java.nio.file.Paths
import java.time.Instant
import java.util.jar.JarInputStream
import javax.annotation.concurrent.ThreadSafe
import javax.persistence.*

/**
 * Stores attachments using Hibernate to database.
 */
@ThreadSafe
class NodeAttachmentService(metrics: MetricRegistry) : AttachmentStorage, SingletonSerializeAsToken() {

    companion object {
        private val log = contextLogger()

        // Just iterate over the entries with verification enabled: should be good enough to catch mistakes.
        // Note that JarInputStream won't throw any kind of error at all if the file stream is in fact not
        // a ZIP! It'll just pretend it's an empty archive, which is kind of stupid but that's how it works.
        // So we have to check to ensure we found at least one item.
        private fun checkIsAValidJAR(stream: InputStream) {
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

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}attachments",
            indexes = arrayOf(Index(name = "att_id_idx", columnList = "att_id")))
    class DBAttachment(
            @Id
            @Column(name = "att_id")
            var attId: String,

            @Column(name = "content")
            @Lob
            var content: ByteArray,

            @Column(name = "insertion_date", nullable = false, updatable = false)
            var insertionDate: Instant = Instant.now(),

            @Column(name = "uploader", updatable = false)
            var uploader: String? = null,

            @Column(name = "filename", updatable = false)
            var filename: String? = null
    ) : Serializable

    @VisibleForTesting
    var checkAttachmentsOnLoad = true

    private val attachmentCount = metrics.counter("Attachments")

    init {
        val session = currentDBSession()
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
    @VisibleForTesting
    @CordaSerializable
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
        private val hash: HashCode
            get() {
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
        val attachment = currentDBSession().get(NodeAttachmentService.DBAttachment::class.java, id.toString())
        attachment?.let {
            return AttachmentImpl(id, { attachment.content }, checkAttachmentsOnLoad)
        }
        return null
    }

    override fun importAttachment(jar: InputStream): AttachmentId {
        return import(jar, null, null)
    }

    override fun importAttachment(jar: InputStream, uploader: String, filename: String): AttachmentId {
        return import(jar, uploader, filename)
    }

    fun getAttachmentIdAndBytes(jar: InputStream): Pair<AttachmentId, ByteArray> {
        val hs = HashingInputStream(Hashing.sha256(), jar)
        val bytes = hs.readBytes()
        checkIsAValidJAR(ByteArrayInputStream(bytes))
        val id = SecureHash.SHA256(hs.hash().asBytes())
        return Pair(id, bytes)
    }

    override fun hasAttachment(attachmentId: AttachmentId): Boolean {
        val session = currentDBSession()
        val criteriaBuilder = session.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(Long::class.java)
        val attachments = criteriaQuery.from(NodeAttachmentService.DBAttachment::class.java)
        criteriaQuery.select(criteriaBuilder.count(criteriaQuery.from(NodeAttachmentService.DBAttachment::class.java)))
        criteriaQuery.where(criteriaBuilder.equal(attachments.get<String>(DBAttachment::attId.name), attachmentId.toString()))
        return (session.createQuery(criteriaQuery).singleResult > 0)
    }

    // TODO: PLT-147: The attachment should be randomised to prevent brute force guessing and thus privacy leaks.
    private fun import(jar: InputStream, uploader: String?, filename: String?): AttachmentId {
        require(jar !is JarInputStream)

        // Read the file into RAM, hashing it to find the ID as we go. The attachment must fit into memory.
        // TODO: Switch to a two-phase insert so we can handle attachments larger than RAM.
        // To do this we must pipe stream into the database without knowing its hash, which we will learn only once
        // the insert/upload is complete. We can then query to see if it's a duplicate and if so, erase, and if not
        // set the hash field of the new attachment record.

        val (id, bytes) = getAttachmentIdAndBytes(jar)
        if (!hasAttachment(id)) {
            checkIsAValidJAR(ByteArrayInputStream(bytes))
            val session = currentDBSession()
            val attachment = NodeAttachmentService.DBAttachment(attId = id.toString(), content = bytes, uploader = uploader, filename = filename)
            session.save(attachment)
            attachmentCount.inc()
            log.info("Stored new attachment $id")
            return id
        } else {
            throw java.nio.file.FileAlreadyExistsException(id.toString())
        }
    }

    override fun importOrGetAttachment(jar: InputStream): AttachmentId {
        try {
            return importAttachment(jar)
        }
        catch (faee: java.nio.file.FileAlreadyExistsException) {
            return AttachmentId.parse(faee.message!!)
        }
    }

    override fun queryAttachments(criteria: AttachmentQueryCriteria, sorting: AttachmentSort?): List<AttachmentId> {
        log.info("Attachment query criteria: $criteria, sorting: $sorting")

        val session = DatabaseTransactionManager.current().session
        val criteriaBuilder = session.criteriaBuilder

        val criteriaQuery = criteriaBuilder.createQuery(DBAttachment::class.java)
        val root = criteriaQuery.from(DBAttachment::class.java)

        val criteriaParser = HibernateAttachmentQueryCriteriaParser(criteriaBuilder, criteriaQuery, root)

        // parse criteria and build where predicates
        criteriaParser.parse(criteria, sorting)

        // prepare query for execution
        val query = session.createQuery(criteriaQuery)

        // execution
        val results = query.resultList

        return results.map { AttachmentId.parse(it.attId) }
    }


}

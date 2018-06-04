package net.corda.node.services.persistence

import com.codahale.metrics.MetricRegistry
import com.github.benmanes.caffeine.cache.Weigher
import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.google.common.hash.HashingInputStream
import com.google.common.io.CountingInputStream
import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.AttachmentMetadata
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractClassName
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.*
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.vault.AttachmentQueryCriteria
import net.corda.core.node.services.vault.AttachmentSort
import net.corda.core.node.services.vault.Builder
import net.corda.core.node.services.vault.builder
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationToken
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SerializeAsTokenContext
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.vault.HibernateAttachmentQueryCriteriaParser
import net.corda.node.utilities.NonInvalidatingCache
import net.corda.node.utilities.NonInvalidatingWeightBasedCache
import net.corda.nodeapi.exceptions.DuplicateAttachmentException
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.currentDBSession
import net.corda.nodeapi.internal.withContractsInJar
import org.hibernate.query.Query
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.Serializable
import java.nio.file.Paths
import java.time.Instant
import java.util.*
import java.util.jar.JarInputStream
import javax.annotation.concurrent.ThreadSafe
import javax.persistence.CollectionTable
import javax.persistence.Column
import javax.persistence.ElementCollection
import javax.persistence.Entity
import javax.persistence.ForeignKey
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.JoinColumn
import javax.persistence.Lob
import javax.persistence.Table

/**
 * Stores attachments using Hibernate to database.
 */
@ThreadSafe
class NodeAttachmentService(
        metrics: MetricRegistry,
        attachmentContentCacheSize: Long = NodeConfiguration.defaultAttachmentContentCacheSize,
        attachmentCacheBound: Long = NodeConfiguration.defaultAttachmentCacheBound,
        private val database: CordaPersistence
) : AttachmentStorage, SingletonSerializeAsToken(
) {

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
    @Table(name = "${NODE_DATABASE_PREFIX}attachments", indexes = [Index(name = "att_id_idx", columnList = "att_id")])
    class DBAttachment(
            @Id
            @Column(name = "att_id", nullable = false)
            var attId: String,

            @Column(name = "content", nullable = false)
            @Lob
            var content: ByteArray,

            @Column(name = "insertion_date", nullable = false, updatable = false)
            var insertionDate: Instant = Instant.now(),

            @Column(name = "uploader", updatable = false, nullable = true)
            var uploader: String? = null,

            @Column(name = "filename", updatable = false, nullable = true)
            var filename: String? = null,

            @ElementCollection
            @Column(name = "contract_class_name", nullable = false)
            @CollectionTable(name = "${NODE_DATABASE_PREFIX}attachments_contracts", joinColumns = [(JoinColumn(name = "att_id", referencedColumnName = "att_id"))],
                    foreignKey = ForeignKey(name = "FK__ctr_class__attachments"))
            var contractClassNames: List<ContractClassName>? = null
    ) : Serializable

    @VisibleForTesting
    var checkAttachmentsOnLoad = true

    private val attachmentCount = metrics.counter("Attachments")

    fun start() {
        database.transaction {
            val session = currentDBSession()
            val criteriaBuilder = session.criteriaBuilder
            val criteriaQuery = criteriaBuilder.createQuery(Long::class.java)
            criteriaQuery.select(criteriaBuilder.count(criteriaQuery.from(NodeAttachmentService.DBAttachment::class.java)))
            val count = session.createQuery(criteriaQuery).singleResult
            attachmentCount.inc(count)
        }
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

    // slightly complex 2 level approach to attachment caching:
    // On the first level we cache attachment contents loaded from the DB by their key. This is a weight based
    // cache (we don't want to waste too  much memory on this) and could be evicted quite aggressively. If we fail
    // to load an attachment from the db, the loader will insert a non present optional - we invalidate this
    // immediately as we definitely want to retry whether the attachment was just delayed.
    // On the second level, we cache Attachment implementations that use the first cache to load their content
    // when required. As these are fairly small, we can cache quite a lot of them, this will make checking
    // repeatedly whether an attachment exists fairly cheap. Here as well, we evict non-existent entries immediately
    // to force a recheck if required.
    // If repeatedly looking for non-existing attachments becomes a performance issue, this is either indicating a
    // a problem somewhere else or this needs to be revisited.

    private val attachmentContentCache = NonInvalidatingWeightBasedCache(
            maxWeight = attachmentContentCacheSize,
            weigher = Weigher<SecureHash, Optional<Pair<Attachment, ByteArray>>> { key, value -> key.size + if (value.isPresent) value.get().second.size else 0 },
            loadFunction = { Optional.ofNullable(loadAttachmentContent(it)) }
    )

    private fun loadAttachmentContent(id: SecureHash): Pair<Attachment, ByteArray>? {
        return database.transaction {
            val attachment = currentDBSession().get(NodeAttachmentService.DBAttachment::class.java, id.toString()) ?: return@transaction null
            val attachmentImpl = AttachmentImpl(id, { attachment.content }, checkAttachmentsOnLoad).let {
                val contracts = attachment.contractClassNames
                if (contracts != null && contracts.isNotEmpty()) {
                    ContractAttachment(it, contracts.first(), contracts.drop(1).toSet(), attachment.uploader)
                } else {
                    it
                }
            }
            Pair(attachmentImpl, attachment.content)
        }
    }

    private val attachmentCache = NonInvalidatingCache<SecureHash, Optional<Attachment>>(
            attachmentCacheBound,
            { key -> Optional.ofNullable(createAttachment(key)) }
    )

    private fun createAttachment(key: SecureHash): Attachment? {
        val content = attachmentContentCache.get(key)!!
        if (content.isPresent) {
            return content.get().first
        }
        // if no attachement has been found, we don't want to cache that - it might arrive later
        attachmentContentCache.invalidate(key)
        return null
    }

    override fun openAttachment(id: SecureHash): Attachment? {
        val attachment = attachmentCache.get(id)!!
        if (attachment.isPresent) {
            return attachment.get()
        }
        attachmentCache.invalidate(id)
        return null
    }

    @Suppress("OverridingDeprecatedMember")
    override fun importAttachment(jar: InputStream): AttachmentId {
        return import(jar, UNKNOWN_UPLOADER, null)
    }

    override fun importAttachment(jar: InputStream, uploader: String, filename: String?): AttachmentId {
        return import(jar, uploader, filename)
    }

    override fun hasAttachment(attachmentId: AttachmentId): Boolean = database.transaction {
        currentDBSession().find(NodeAttachmentService.DBAttachment::class.java, attachmentId.toString()) != null
    }

    // TODO: PLT-147: The attachment should be randomised to prevent brute force guessing and thus privacy leaks.
    private fun import(jar: InputStream, uploader: String?, filename: String?): AttachmentId {
        return database.transaction {
            withContractsInJar(jar) { contractClassNames, inputStream ->
                require(inputStream !is JarInputStream)

                // Read the file into RAM and then calculate its hash. The attachment must fit into memory.
                // TODO: Switch to a two-phase insert so we can handle attachments larger than RAM.
                // To do this we must pipe stream into the database without knowing its hash, which we will learn only once
                // the insert/upload is complete. We can then query to see if it's a duplicate and if so, erase, and if not
                // set the hash field of the new attachment record.

                val bytes = inputStream.readFully()
                val id = bytes.sha256()
                if (!hasAttachment(id)) {
                    checkIsAValidJAR(bytes.inputStream())
                    val session = currentDBSession()
                    val attachment = NodeAttachmentService.DBAttachment(attId = id.toString(), content = bytes, uploader = uploader, filename = filename, contractClassNames = contractClassNames)
                    session.save(attachment)
                    attachmentCount.inc()
                    log.info("Stored new attachment $id")
                    id
                } else {
                    throw DuplicateAttachmentException(id.toString())
                }
            }
        }
    }

    @Suppress("OverridingDeprecatedMember")
    override fun importOrGetAttachment(jar: InputStream): AttachmentId = try {
        import(jar, UNKNOWN_UPLOADER, null)
    } catch (faee: java.nio.file.FileAlreadyExistsException) {
        AttachmentId.parse(faee.message!!)
    }

    private fun queryAttachmentsHelper(criteria: AttachmentQueryCriteria, sorting: AttachmentSort?): Query<DBAttachment> {
        val session = currentDBSession()
        val criteriaBuilder = session.criteriaBuilder

        val criteriaQuery = criteriaBuilder.createQuery(DBAttachment::class.java)
        val root = criteriaQuery.from(DBAttachment::class.java)

        val criteriaParser = HibernateAttachmentQueryCriteriaParser(criteriaBuilder, criteriaQuery, root)

        // parse criteria and build where predicates
        criteriaParser.parse(criteria, sorting)

        // prepare query for execution
        return session.createQuery(criteriaQuery)!!

    }

    override fun queryAttachments(criteria: AttachmentQueryCriteria, sorting: AttachmentSort?): List<AttachmentId> {
        log.info("Attachment query criteria: $criteria, sorting: $sorting")
        return database.transaction {
            // prepare query for execution
            val query = queryAttachmentsHelper(criteria, sorting)

            // execution
            query.resultList.map { AttachmentId.parse(it.attId) }
        }
    }

    override fun getAttachmentMetadata(attachmentId: String): List<AttachmentMetadata>{
        val criteria = AttachmentQueryCriteria.AttachmentsQueryCriteria(attId = Builder.equal(attachmentId))
        return queryAttachmentsHelper(criteria,null).resultList.map { AttachmentMetadataImpl(attId = it.attId,insertionDate = it.insertionDate,uploader = it.uploader,filename = it.filename) }
    }
}

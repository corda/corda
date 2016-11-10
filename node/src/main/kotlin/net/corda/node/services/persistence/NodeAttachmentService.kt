package net.corda.node.services.persistence

import com.codahale.metrics.MetricRegistry
import com.google.common.annotations.VisibleForTesting
import com.google.common.hash.Hashing
import com.google.common.hash.HashingInputStream
import com.google.common.io.CountingInputStream
import net.corda.core.*
import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.utilities.loggerFor
import net.corda.node.services.api.AcceptsFileUpload
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.file.*
import java.util.*
import java.util.jar.JarInputStream
import javax.annotation.concurrent.ThreadSafe

/**
 * Stores attachments in the specified local directory, which must exist. Doesn't allow new attachments to be uploaded.
 */
@ThreadSafe
class NodeAttachmentService(val storePath: Path, metrics: MetricRegistry) : AttachmentStorage, AcceptsFileUpload {
    private val log = loggerFor<NodeAttachmentService>()

    @VisibleForTesting
    var checkAttachmentsOnLoad = true

    private val attachmentCount = metrics.counter("Attachments")

    init {
        attachmentCount.inc(countAttachments())
    }

    // Just count all non-directories in the attachment store, and assume the admin hasn't dumped any junk there.
    private fun countAttachments() = storePath.list { it.filter { it.isRegularFile() }.count() }

    /**
     * If true, newly inserted attachments will be unzipped to a subdirectory of the [storePath]. This is intended for
     * human browsing convenience: the attachment itself will still be the file (that is, edits to the extracted directory
     * will not have any effect).
     */
    @Volatile var automaticallyExtractAttachments = false

    init {
        require(storePath.isDirectory()) { "$storePath must be a directory" }
    }

    class OnDiskHashMismatch(val file: Path, val actual: SecureHash) : Exception() {
        override fun toString() = "File $file hashed to $actual: corruption in attachment store?"
    }

    /**
     * Wraps a stream and hashes data as it is read: if the entire stream is consumed, then at the end the hash of
     * the read data is compared to the [expected] hash and [OnDiskHashMismatch] is thrown by [close] if they didn't
     * match. The goal of this is to detect cases where attachments in the store have been tampered with or corrupted
     * and no longer match their file name. It won't always work: if we read a zip for our own uses and skip around
     * inside it, we haven't read the whole file, so we can't check the hash. But when copying it over the network
     * this will provide an additional safety check against user error.
     */
    private class HashCheckingStream(val expected: SecureHash.SHA256,
                                     val filePath: Path,
                                     input: InputStream,
                                     private val counter: CountingInputStream = CountingInputStream(input),
                                     private val stream: HashingInputStream = HashingInputStream(Hashing.sha256(), counter)) : FilterInputStream(stream) {

        private val expectedSize = filePath.size

        override fun close() {
            super.close()
            if (counter.count != expectedSize) return
            val actual = SecureHash.SHA256(stream.hash().asBytes())
            if (actual != expected)
                throw OnDiskHashMismatch(filePath, actual)
        }
    }

    // Deliberately not an inner class to avoid holding a reference to the attachments service.
    private class AttachmentImpl(override val id: SecureHash,
                                 private val path: Path,
                                 private val checkOnLoad: Boolean) : Attachment {
        override fun open(): InputStream {
            var stream = Files.newInputStream(path)
            // This is just an optional safety check. If it slows things down too much it can be disabled.
            if (id is SecureHash.SHA256 && checkOnLoad)
                stream = HashCheckingStream(id, path, stream)
            return stream
        }

        override fun equals(other: Any?) = other is Attachment && other.id == id
        override fun hashCode(): Int = id.hashCode()
    }

    override fun openAttachment(id: SecureHash): Attachment? {
        val path = storePath / id.toString()
        if (!path.exists()) return null
        return AttachmentImpl(id, path, checkAttachmentsOnLoad)
    }

    // TODO: PLT-147: The attachment should be randomised to prevent brute force guessing and thus privacy leaks.
    override fun importAttachment(jar: InputStream): SecureHash {
        require(jar !is JarInputStream)
        val hs = HashingInputStream(Hashing.sha256(), jar)
        val tmp = storePath / "tmp.${UUID.randomUUID()}"
        hs.copyTo(tmp)
        checkIsAValidJAR(tmp)
        val id = SecureHash.SHA256(hs.hash().asBytes())
        val finalPath = storePath / id.toString()
        try {
            // Move into place atomically or fail if that isn't possible. We don't want a half moved attachment to
            // be exposed to parallel threads. This gives us thread safety.
            if (!finalPath.exists()) {
                log.info("Stored new attachment $id")
                attachmentCount.inc()
            } else {
                log.info("Replacing attachment $id - only bother doing this if you're trying to repair file corruption")
            }
            tmp.moveTo(finalPath, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            tmp.deleteIfExists()
        }
        if (automaticallyExtractAttachments) {
            val extractTo = storePath / "$id.jar"
            try {
                extractTo.createDirectory()
                extractZipFile(finalPath, extractTo)
            } catch(e: FileAlreadyExistsException) {
                log.trace("Did not extract attachment jar to directory because it already exists")
            } catch(e: Exception) {
                log.error("Failed to extract attachment jar $id, ", e)
                // TODO: Delete the extractTo directory here.
            }
        }
        return id
    }

    private fun checkIsAValidJAR(path: Path) {
        // Just iterate over the entries with verification enabled: should be good enough to catch mistakes.
        path.read {
            val jar = JarInputStream(it)
            while (true) {
                val cursor = jar.nextJarEntry ?: break
                val entryPath = Paths.get(cursor.name)
                // Security check to stop zips trying to escape their rightful place.
                if (entryPath.isAbsolute || entryPath.normalize() != entryPath || '\\' in cursor.name)
                    throw IllegalArgumentException("Path is either absolute or non-normalised: $entryPath")
            }
        }
    }

    // Implementations for AcceptsFileUpload
    override val dataTypePrefix = "attachment"
    override val acceptableFileExtensions = listOf(".jar", ".zip")
    override fun upload(data: InputStream) = importAttachment(data).toString()
}

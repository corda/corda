/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node

import com.google.common.hash.Hashing
import com.google.common.hash.HashingInputStream
import com.google.common.io.CountingInputStream
import core.Attachment
import core.AttachmentStorage
import core.crypto.SecureHash
import core.utilities.loggerFor
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.jar.JarInputStream
import javax.annotation.concurrent.ThreadSafe

/**
 * Stores attachments in the specified local directory, which must exist. Doesn't allow new attachments to be uploaded.
 */
@ThreadSafe
class NodeAttachmentStorage(val storePath: Path) : AttachmentStorage {
    private val log = loggerFor<NodeAttachmentStorage>()

    init {
        require(Files.isDirectory(storePath)) { "$storePath must be a directory" }
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

        private val expectedSize = Files.size(filePath)

        override fun close() {
            super.close()
            if (counter.count != expectedSize) return
            val actual = SecureHash.SHA256(stream.hash().asBytes())
            if (actual != expected)
                throw OnDiskHashMismatch(filePath, actual)
        }
    }

    override fun openAttachment(id: SecureHash): Attachment? {
        val path = storePath.resolve(id.toString())
        if (!Files.exists(path)) return null
        var stream = Files.newInputStream(path)
        // This is just an optional safety check. If it slows things down too much it can be disabled.
        if (id is SecureHash.SHA256)
            stream = HashCheckingStream(id, path, stream)
        log.debug("Opening attachment $id")
        return object : Attachment {
            override fun open(): InputStream = stream
            override val id: SecureHash = id
        }
    }

    override fun importAttachment(jar: InputStream): SecureHash {
        require(jar !is JarInputStream)
        val hs = HashingInputStream(Hashing.sha256(), jar)
        val tmp = storePath.resolve("tmp.${UUID.randomUUID()}")
        Files.copy(hs, tmp)
        checkIsAValidJAR(tmp)
        val id = SecureHash.SHA256(hs.hash().asBytes())
        val finalPath = storePath.resolve(id.toString())
        try {
            // Move into place atomically or fail if that isn't possible. We don't want a half moved attachment to
            // be exposed to parallel threads. This gives us thread safety.
            Files.move(tmp, finalPath, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(tmp)
        }
        log.info("Stored new attachment $id")
        return id
    }

    private fun checkIsAValidJAR(path: Path) {
        // Just iterate over the entries with verification enabled: should be good enough to catch mistakes.
        JarInputStream(Files.newInputStream(path), true).use { stream ->
            var cursor = stream.nextJarEntry
            while (cursor != null) cursor = stream.nextJarEntry
        }
    }
}

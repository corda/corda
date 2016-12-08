package net.corda.core.node.services

import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import java.io.InputStream

/**
 * An attachment store records potentially large binary objects, identified by their hash.
 */
interface AttachmentStorage {
    /**
     * Returns a handle to a locally stored attachment, or null if it's not known. The handle can be used to open
     * a stream for the data, which will be a zip/jar file.
     */
    fun openAttachment(id: SecureHash): Attachment?

    /**
     * Inserts the given attachment into the store, does *not* close the input stream. This can be an intensive
     * operation due to the need to copy the bytes to disk and hash them along the way.
     *
     * Note that you should not pass a [JarInputStream] into this method and it will throw if you do, because access
     * to the raw byte stream is required.
     *
     * @throws FileAlreadyExistsException if the given byte stream has already been inserted.
     * @throws IllegalArgumentException if the given byte stream is empty or a [JarInputStream].
     * @throws IOException if something went wrong.
     */
    fun importAttachment(jar: InputStream): SecureHash
}


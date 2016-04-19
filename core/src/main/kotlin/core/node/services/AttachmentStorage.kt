package core.node.services

import core.Attachment
import core.crypto.SecureHash
import java.io.InputStream

/**
 * An attachment store records potentially large binary objects, identified by their hash. Note that attachments are
 * immutable and can never be erased once inserted!
 */
interface AttachmentStorage {
    /**
     * Returns a newly opened stream for the given locally stored attachment, or null if no such attachment is known.
     * The returned stream must be closed when you are done with it to avoid resource leaks. You should probably wrap
     * the result in a [JarInputStream] unless you're sending it somewhere, there is a convenience helper for this
     * on [Attachment].
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
     * @throws IllegalArgumentException if the given byte stream is empty or a [JarInputStream]
     * @throws IOException if something went wrong.
     */
    fun importAttachment(jar: InputStream): SecureHash
}


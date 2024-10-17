package net.corda.core.node.services

import net.corda.core.DoNotImplement
import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.cordapp.CordappImpl.Companion.DEFAULT_CORDAPP_VERSION
import net.corda.core.node.services.vault.AttachmentQueryCriteria
import net.corda.core.node.services.vault.AttachmentSort
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileAlreadyExistsException

typealias AttachmentId = SecureHash
typealias AttachmentFixup = Pair<Set<AttachmentId>, Set<AttachmentId>>

/**
 * An attachment store records potentially large binary objects, identified by their hash.
 */
@DoNotImplement
interface AttachmentStorage {
    /**
     * Returns a handle to a locally stored attachment, or null if it's not known. The handle can be used to open
     * a stream for the data, which will be a zip/jar file.
     */
    fun openAttachment(id: AttachmentId): Attachment?

    /**
     * Inserts the given attachment into the store, does *not* close the input stream. This can be an intensive
     * operation due to the need to copy the bytes to disk and hash them along the way.
     *
     * Note that you should not pass a [java.util.jar.JarInputStream] into this method and it will throw if you do, because
     * access to the raw byte stream is required.
     *
     * @throws FileAlreadyExistsException if the given byte stream has already been inserted.
     * @throws IllegalArgumentException if the given byte stream is empty or a [java.util.jar.JarInputStream].
     * @throws IOException if something went wrong.
     */
    @Deprecated("More attachment information is required", replaceWith = ReplaceWith("importAttachment(jar, uploader, filename)"))
    @Throws(FileAlreadyExistsException::class, IOException::class)
    fun importAttachment(jar: InputStream): AttachmentId

    /**
     * Inserts the given attachment with additional metadata, see [importAttachment] for input stream handling
     * Extra parameters:
     * @param uploader Uploader name
     * @param filename Name of the file
     */
    @Throws(FileAlreadyExistsException::class, IOException::class)
    fun importAttachment(jar: InputStream, uploader: String, filename: String?): AttachmentId

    /**
     * Inserts or returns Attachment Id of attachment. Does not throw an exception if already uploaded.
     * @param jar [InputStream] of Jar file
     * @return [AttachmentId] of uploaded attachment
     */
    @Deprecated("More attachment information is required", replaceWith = ReplaceWith("importAttachment(jar, uploader, filename)"))
    fun importOrGetAttachment(jar: InputStream): AttachmentId

    /**
     * Searches attachment using given criteria and optional sort rules
     * @param criteria Query criteria to use as a filter
     * @param sorting Sorting definition, if not given, order is undefined
     *
     * @return List of AttachmentId of attachment matching criteria, sorted according to given sorting parameter
     */
    fun queryAttachments(criteria: AttachmentQueryCriteria, sorting: AttachmentSort? = null): List<AttachmentId>

    /**
     * Searches for an attachment already in the store
     * @param attachmentId The attachment Id
     * @return true if it's in there
     */
    fun hasAttachment(attachmentId: AttachmentId): Boolean

    // Note: cannot apply @JvmOverloads to interfaces nor interface implementations.
    // Java Helpers.
    fun queryAttachments(criteria: AttachmentQueryCriteria): List<AttachmentId> {
        return queryAttachments(criteria, null)
    }

    /**
     * Find the Attachment Id(s) of the contract attachments with the highest version for a given contract class name
     * from trusted upload sources.
     * Return highest version of both signed and unsigned attachment ids (signed first, unsigned second), otherwise return a
     * single signed or unsigned version id, or an empty list if none meet the criteria.
     *
     * @param contractClassName The fully qualified name of the contract class.
     * @param minContractVersion The minimum contract version that should be returned.
     * @return the [AttachmentId]s of the contract attachments (signed always first in list), or an empty list if none meet the criteria.
     */
    fun getLatestContractAttachments(contractClassName: String, minContractVersion: Int = DEFAULT_CORDAPP_VERSION): List<AttachmentId>
}


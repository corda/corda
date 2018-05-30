package net.corda.serialization.internal

import net.corda.core.Deterministic
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractAttachment
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.isUploaderTrusted
import net.corda.core.serialization.CordaSerializable
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.security.CodeSigner
import java.security.CodeSource
import java.security.SecureClassLoader
import java.util.*

/**
 * A custom ClassLoader that knows how to load classes from a set of attachments. The attachments themselves only
 * need to provide JAR streams, and so could be fetched from a database, local disk, etc. Constructing an
 * AttachmentsClassLoader is somewhat expensive, as every attachment is scanned to ensure that there are no overlapping
 * file paths.
 */
@Deterministic
class AttachmentsClassLoader(attachments: List<Attachment>, parent: ClassLoader = ClassLoader.getSystemClassLoader()) : SecureClassLoader(parent) {
    private val pathsToAttachments = HashMap<String, Attachment>()
    private val idsToAttachments = HashMap<SecureHash, Attachment>()

    @CordaSerializable
    class OverlappingAttachments(val path: String) : Exception() {
        override fun toString() = "Multiple attachments define a file at path $path"
    }

    init {
        require(attachments.mapNotNull { it as? ContractAttachment }.all { isUploaderTrusted(it.uploader) }) {
            "Attempting to load Contract Attachments downloaded from the network"
        }

        for (attachment in attachments) {
            attachment.openAsJAR().use { jar ->
                while (true) {
                    val entry = jar.nextJarEntry ?: break

                    // We already verified that paths are not strange/game playing when we inserted the attachment
                    // into the storage service. So we don't need to repeat it here.
                    //
                    // We forbid files that differ only in case, or path separator to avoid issues for Windows/Mac developers where the
                    // filesystem tries to be case insensitive. This may break developers who attempt to use ProGuard.
                    //
                    // Also convert to Unix path separators as all resource/class lookups will expect this.
                    val path = entry.name.toLowerCase().replace('\\', '/')
                    if (path in pathsToAttachments)
                        throw OverlappingAttachments(path)
                    pathsToAttachments[path] = attachment
                }
            }
            idsToAttachments[attachment.id] = attachment
        }
    }

    // Example: attachment://0b4fc1327f3bbebf1bfe98330ea402ae035936c3cb6da9bd3e26eeaa9584e74d/some/file.txt
    //
    // We have to provide a fake stream handler to satisfy the URL class that the scheme is known. But it's not
    // a real scheme and we don't register it. It's just here to ensure that there aren't codepaths that could
    // lead to data loading that we don't control right here in this class (URLs can have evil security properties!)
    private val fakeStreamHandler = object : URLStreamHandler() {
        override fun openConnection(u: URL?): URLConnection? {
            throw UnsupportedOperationException()
        }
    }

    private fun Attachment.toURL(path: String?) = URL(null, "attachment://$id/" + (path ?: ""), fakeStreamHandler)

    override fun findClass(name: String): Class<*> {
        val path = name.replace('.', '/').toLowerCase() + ".class"
        val attachment = pathsToAttachments[path] ?: throw ClassNotFoundException(name)
        val stream = ByteArrayOutputStream()
        try {
            attachment.extractFile(path, stream)
        } catch (e: FileNotFoundException) {
            throw ClassNotFoundException(name)
        }
        val bytes = stream.toByteArray()
        // We don't attempt to propagate signatures from the JAR into the codesource, because our sandbox does not
        // depend on external policy files to specify what it can do, so the data wouldn't be useful.
        val codesource = CodeSource(attachment.toURL(null), emptyArray<CodeSigner>())
        // TODO: Define an empty ProtectionDomain to start enforcing the standard Java sandbox.
        // The standard Java sandbox is insufficient for our needs and a much more sophisticated sandboxing
        // ClassLoader will appear here in future, but it can't hurt to use the default one too: defence in depth!
        return defineClass(name, bytes, 0, bytes.size, codesource)
    }

    override fun findResource(name: String): URL? {
        val attachment = pathsToAttachments[name.toLowerCase()] ?: return null
        return attachment.toURL(name)
    }

    override fun getResourceAsStream(name: String): InputStream? {
        val url = getResource(name) ?: return null   // May check parent classloaders, for example.
        if (url.protocol != "attachment") return null
        val attachment = idsToAttachments[SecureHash.parse(url.host)] ?: return null
        val path = url.path?.substring(1) ?: return null   // Chop off the leading slash.
        return try {
            val stream = ByteArrayOutputStream()
            attachment.extractFile(path, stream)
            stream.toByteArray().inputStream()
        } catch (e: FileNotFoundException) {
            null
        }
    }
}



package net.corda.core.serialization.internal

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.TransactionVerificationException.OverlappingAttachmentsException
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.cordapp.targetPlatformVersion
import net.corda.core.internal.createSimpleCache
import net.corda.core.internal.isUploaderTrusted
import net.corda.core.internal.toSynchronised
import net.corda.core.serialization.MissingAttachmentsException
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.internal.AttachmentURLStreamHandlerFactory.toUrl
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.*

/**
 * A custom ClassLoader that knows how to load classes from a set of attachments. The attachments themselves only
 * need to provide JAR streams, and so could be fetched from a database, local disk, etc. Constructing an
 * AttachmentsClassLoader is somewhat expensive, as every attachment is scanned to ensure that there are no overlapping
 * file paths.
 */
class AttachmentsClassLoader(attachments: List<Attachment>, parent: ClassLoader = ClassLoader.getSystemClassLoader()) :
        URLClassLoader(attachments.map(::toUrl).toTypedArray(), parent) {

    init {
        val untrusted = attachments.mapNotNull { it as? ContractAttachment }.filterNot { isUploaderTrusted(it.uploader) }.map(ContractAttachment::id)
        if(untrusted.isNotEmpty()) {
            throw MissingAttachmentsException(untrusted, "Attempting to load Contract Attachments downloaded from the network")
        }
        requireNoDuplicates(attachments)
    }

    companion object {
        private val log = contextLogger()

        init {
            // This is required to register the AttachmentURLStreamHandlerFactory.
            URL.setURLStreamHandlerFactory(AttachmentURLStreamHandlerFactory)
        }

        // Jolokia and Json-simple are dependencies that were bundled by mistake within contract jars.
        // In the AttachmentsClassLoader we just ignore any class in those 2 packages.
        private val ignoreDirectories = listOf("org/jolokia/", "org/json/simple/")
        private val ignorePackages = ignoreDirectories.map { it.replace("/", ".") }

        private fun shouldCheckForNoOverlap(path: String, targetPlatformVersion: Int) = when {
            path.endsWith("/") -> false                     // Directories (packages) can overlap.
            targetPlatformVersion < 4 && ignoreDirectories.any { path.startsWith(it) } -> false    // Ignore jolokia and json-simple for old cordapps.
            path.endsWith(".class") -> true                 // All class files need to be unique.
            !path.startsWith("meta-inf") -> true            // All files outside of Meta-inf need to be unique.
            else -> false                                          // This allows overlaps over any non-class files in "Meta-inf".
        }

        private fun requireNoDuplicates(attachments: List<Attachment>) {
            // Avoid unnecessary duplicate checking if possible:
            // 1. single attachment.
            // 2. multiple attachments with non-overlapping contract classes.
            if (attachments.size <= 1) return
            val overlappingContractClasses = attachments.mapNotNull { it as? ContractAttachment }.flatMap { it.allContracts }.groupingBy { it }.eachCount().filter { it.value > 1 }
            if (overlappingContractClasses.isEmpty()) return

            // this logic executes only if there are overlapping contract classes
            log.debug("Duplicate contract class checking for $overlappingContractClasses")
            val classLoaderEntries = mutableMapOf<String, Attachment>()
            for (attachment in attachments) {
                attachment.openAsJAR().use { jar ->
                    val targetPlatformVersion = jar.manifest?.targetPlatformVersion ?: 1
                    while (true) {
                        val entry = jar.nextJarEntry ?: break
                        if (entry.isDirectory) continue
                        // We already verified that paths are not strange/game playing when we inserted the attachment
                        // into the storage service. So we don't need to repeat it here.
                        //
                        // We forbid files that differ only in case, or path separator to avoid issues for Windows/Mac developers where the
                        // filesystem tries to be case insensitive. This may break developers who attempt to use ProGuard.
                        //
                        // Also convert to Unix path separators as all resource/class lookups will expect this.
                        //
                        val path = entry.name.toLowerCase().replace('\\', '/')
                        // TODO - If 2 entries are identical, it means the same file is present in both attachments, so that should be ok.
                        if (shouldCheckForNoOverlap(path, targetPlatformVersion)) {
                            if (path in classLoaderEntries.keys) {
                                // If 2 entries have the same content hash, it means the same file is present in both attachments, so that is ok.
                                val contentHash = readAttachment(attachment, path).sha256()
                                val originalAttachment = classLoaderEntries[path]!!
                                val originalContentHash = readAttachment(originalAttachment, path).sha256()
                                if (contentHash == originalContentHash) {
                                    log.debug { "Duplicate entry $path has same content hash $contentHash" }
                                    continue
                                } else {
                                    log.debug { "Content hash differs for $path" }
                                    throw OverlappingAttachmentsException(path)
                                }
                            }
                            log.debug { "Adding new entry for $path" }
                            classLoaderEntries[path] = attachment
                        }
                    }
                }
                log.debug { "${classLoaderEntries.size} classloaded entries for $attachment" }
            }
        }

        @VisibleForTesting
        private fun readAttachment(attachment: Attachment, filepath: String): ByteArray {
            ByteArrayOutputStream().use {
                attachment.extractFile(filepath, it)
                return it.toByteArray()
            }
        }
    }

    /**
     * Required to prevent classes that were excluded from the no-overlap check from being loaded by contract code.
     * As it can lead to non-determinism.
     */
    override fun loadClass(name: String?): Class<*> {
        if (ignorePackages.any { name!!.startsWith(it) }) {
            throw ClassNotFoundException(name)
        }
        return super.loadClass(name)
    }
}

// Mighty unclean, but we need a quick stopgap to the bug it's addressing.
// TODO Remove ASAP after proper handling of dependent CorDapps with regards to attachments.
object CorDappsClassLoaderHolder {
    var instance: ClassLoader? = null
    fun set(instance: ClassLoader) {
        this.instance = instance
    }
}

/**
 * This is just a factory that provides a cache to avoid constructing expensive [AttachmentsClassLoader]s.
 */
@VisibleForTesting
internal object AttachmentsClassLoaderBuilder {

    private const val ATTACHMENT_CLASSLOADER_CACHE_SIZE = 1000

    // This runs in the DJVM so it can't use caffeine.
    private val cache: MutableMap<List<SecureHash>, AttachmentsClassLoader> = createSimpleCache<List<SecureHash>, AttachmentsClassLoader>(ATTACHMENT_CLASSLOADER_CACHE_SIZE)
            .toSynchronised()

    fun build(attachments: List<Attachment>): AttachmentsClassLoader {
        return cache.computeIfAbsent(attachments.map { it.id }.sorted()) {
            AttachmentsClassLoader(attachments)
        }
    }

    fun <T> withAttachmentsClassloaderContext(attachments: List<Attachment>, block: (ClassLoader) -> T): T {
        // Create classloader from the attachments.
        // TODO This should not default to the CorDapps classloader, but it's needed for now to stop a bug preventing CorDapps with dependencies on other CorDapps from working as attachments.
        // TODO A proper fix would require gathering information about dependent CorDapps with hashes at build time, and importing these as attachments as well.
        val attachmentsClassLoader = AttachmentsClassLoaderBuilder.build(attachments)
        val cordappsClassLoader = CorDappsClassLoaderHolder.instance
        val transactionClassLoader = cordappsClassLoader?.let { CascadingClassLoader(sequenceOf(attachmentsClassLoader, it)) } ?: attachmentsClassLoader

        // Create a new serializationContext for the current Transaction.
        val transactionSerializationContext = SerializationFactory.defaultFactory.defaultContext.withPreventDataLoss().withClassLoader(transactionClassLoader)

        // Deserialize all relevant classes in the transaction classloader.
        return SerializationFactory.defaultFactory.withCurrentContext(transactionSerializationContext) {
            block(transactionClassLoader)
        }
    }
}

private class CascadingClassLoader(classLoaders: Sequence<ClassLoader>) : ClassLoader() {
    private val classLoaders = classLoaders.toList()

    override fun loadClass(name: String?): Class<*> {
        for (classLoader in classLoaders) {
            try {
                return classLoader.loadClass(name)
            } catch (e: ClassNotFoundException) {
                // Keep iterating without failing.
            }
        }
        throw ClassNotFoundException(name)
    }

    override fun getResource(name: String): URL? {
        for (classLoader in classLoaders) {
            val url = classLoader.getResource(name)
            if (url != null) {
                return url
            }
        }
        return null
    }
}

/**
 * Registers a new internal "attachment" protocol.
 * This will not be exposed as an API.
 */
object AttachmentURLStreamHandlerFactory : URLStreamHandlerFactory {
    private const val attachmentScheme = "attachment"

    // TODO - what happens if this grows too large?
    private val loadedAttachments = mutableMapOf<String, Attachment>().toSynchronised()

    override fun createURLStreamHandler(protocol: String): URLStreamHandler? {
        return if (attachmentScheme == protocol) {
            AttachmentURLStreamHandler
        } else null
    }

    fun toUrl(attachment: Attachment): URL {
        val id = attachment.id.toString()
        loadedAttachments[id] = attachment
        return URL(attachmentScheme, "", -1, id, AttachmentURLStreamHandler)
    }

    private object AttachmentURLStreamHandler : URLStreamHandler() {
        override fun openConnection(url: URL): URLConnection {
            if (url.protocol != attachmentScheme) throw IOException("Cannot handle protocol: ${url.protocol}")
            val attachment = loadedAttachments[url.path] ?: throw IOException("Could not load url: $url .")
            return AttachmentURLConnection(url, attachment)
        }
    }

    private class AttachmentURLConnection(url: URL, private val attachment: Attachment) : URLConnection(url) {
        override fun getContentLengthLong(): Long = attachment.size.toLong()
        override fun getInputStream(): InputStream = attachment.open()
        override fun connect() {
            connected = true
        }
    }
}

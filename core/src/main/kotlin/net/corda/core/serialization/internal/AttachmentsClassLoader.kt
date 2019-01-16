package net.corda.core.serialization.internal

import net.corda.core.CordaException
import net.corda.core.KeepForDJVM
import net.corda.core.internal.createInstancesOfClassesImplementing
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.TransactionVerificationException.OverlappingAttachmentsException
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.*
import net.corda.core.internal.cordapp.targetPlatformVersion
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.*
import net.corda.core.serialization.internal.AttachmentURLStreamHandlerFactory.toUrl
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.*
import java.util.*

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
            throw UntrustedAttachmentsException(untrusted)
        }
        requireNoDuplicates(attachments)
    }

    companion object {
        private val log = contextLogger()

        init {
            // Apply our own URLStreamHandlerFactory to resolve attachments
            setOrDecorateURLStreamHandlerFactory()
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

        /**
         * Apply our custom factory either directly, if `URL.setURLStreamHandlerFactory` has not been called yet,
         * or use a decorator and reflection to bypass the single-call-per-JVM restriction otherwise.
         */
        private fun setOrDecorateURLStreamHandlerFactory() {
            // Retrieve the `URL.factory` field
            val factoryField = URL::class.java.getDeclaredField("factory")
            // Make it accessible
            factoryField.isAccessible = true

            // Check for preset factory, set directly if missing
            val existingFactory: URLStreamHandlerFactory? = factoryField.get(null) as URLStreamHandlerFactory?
            if (existingFactory == null) {
                URL.setURLStreamHandlerFactory(AttachmentURLStreamHandlerFactory)
            }
            // Otherwise, decorate the existing and replace via reflection
            // as calling `URL.setURLStreamHandlerFactory` again will throw an error
            else {
                log.warn("The URLStreamHandlerFactory was already set in the JVM. Please be aware that this is not recommended.")
                // Retrieve the field "streamHandlerLock" of the class URL that
                // is the lock used to synchronize access to the protocol handlers
                val lockField = URL::class.java.getDeclaredField("streamHandlerLock")
                // It is a private field so we need to make it accessible
                // Note: this will only work as-is in JDK8.
                lockField.isAccessible = true
                // Use the same lock to reset the factory
                synchronized(lockField.get(null)) {
                    // Reset the value to prevent Error due to a factory already defined
                    factoryField.set(null, null)
                    // Set our custom factory and wrap the current one into it
                    URL.setURLStreamHandlerFactory(
                            // Set the factory to a decorator
                            object : URLStreamHandlerFactory {
                                // route between our own and the pre-existing factory
                                override fun createURLStreamHandler(protocol: String): URLStreamHandler? {
                                    return AttachmentURLStreamHandlerFactory.createURLStreamHandler(protocol)
                                            ?: existingFactory.createURLStreamHandler(protocol)
                                }
                            }
                    )
                }
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

/**
 * This is just a factory that provides caches to optimise expensive construction/loading of classloaders, serializers, whitelisted classes.
 */
@VisibleForTesting
internal object AttachmentsClassLoaderBuilder {

    private const val CACHE_SIZE = 1000

    // This runs in the DJVM so it can't use caffeine.
    private val cache: MutableMap<Set<SecureHash>, SerializationContext> = createSimpleCache(CACHE_SIZE)

    fun <T> withAttachmentsClassloaderContext(attachments: List<Attachment>, block: (ClassLoader) -> T): T {
        val attachmentIds = attachments.map { it.id }.toSet()

        val serializationContext = cache.computeIfAbsent(attachmentIds) {
            // Create classloader and load serializers, whitelisted classes
            val transactionClassLoader = AttachmentsClassLoader(attachments)
            val serializers = createInstancesOfClassesImplementing(transactionClassLoader, SerializationCustomSerializer::class.java)
            val whitelistedClasses = ServiceLoader.load(SerializationWhitelist::class.java, transactionClassLoader)
                    .flatMap { it.whitelist }
                    .toList()

            // Create a new serializationContext for the current Transaction.
            SerializationFactory.defaultFactory.defaultContext
                    .withPreventDataLoss()
                    .withClassLoader(transactionClassLoader)
                    .withWhitelist(whitelistedClasses)
                    .withCustomSerializers(serializers)
        }

        // Deserialize all relevant classes in the transaction classloader.
        return SerializationFactory.defaultFactory.withCurrentContext(serializationContext) {
            block(serializationContext.deserializationClassLoader)
        }
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

/** Thrown during classloading upon encountering an untrusted attachment (eg. not in the [TRUSTED_UPLOADERS] list) */
@KeepForDJVM
@CordaSerializable
class UntrustedAttachmentsException(val ids: List<SecureHash>) :
        CordaException("Attempting to load untrusted Contract Attachments: $ids" +
                "These may have been received over the p2p network from a remote node." +
                "Please follow the operational steps outlined in https://docs.corda.net/cordapp-build-systems.html#cordapp-contract-attachments to continue."
        )

@file:KeepForDJVM
package net.corda.core.internal

import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.serialization.MissingAttachmentsException
import net.corda.core.serialization.SerializeAsTokenContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.CodeSigner
import java.security.cert.X509Certificate
import java.util.jar.JarEntry
import java.util.jar.JarInputStream

const val DEPLOYED_CORDAPP_UPLOADER = "app"
const val RPC_UPLOADER = "rpc"
const val P2P_UPLOADER = "p2p"
const val UNKNOWN_UPLOADER = "unknown"

private val TRUSTED_UPLOADERS = listOf(DEPLOYED_CORDAPP_UPLOADER, RPC_UPLOADER)

fun isUploaderTrusted(uploader: String?): Boolean = uploader in TRUSTED_UPLOADERS

@KeepForDJVM
abstract class AbstractAttachment(dataLoader: () -> ByteArray) : Attachment {
    companion object {
        @DeleteForDJVM
        fun SerializeAsTokenContext.attachmentDataLoader(id: SecureHash): () -> ByteArray {
            return {
                val a = serviceHub.attachments.openAttachment(id) ?: throw MissingAttachmentsException(listOf(id))
                (a as? AbstractAttachment)?.attachmentData ?: a.open().readFully()
            }
        }
    }

    protected val attachmentData: ByteArray by lazy(dataLoader)

    // TODO: read file size information from metadata instead of loading the data.
    override val size: Int get() = attachmentData.size

    override fun open(): InputStream = attachmentData.inputStream()
    override val signers by lazy {
        openAsJAR().use(JarSignatureCollector::collectSigningParties)
    }

    override fun equals(other: Any?) = other === this || other is Attachment && other.id == this.id
    override fun hashCode() = id.hashCode()
    override fun toString() = "${javaClass.simpleName}(id=$id)"
}

@Throws(IOException::class)
fun JarInputStream.extractFile(path: String, outputTo: OutputStream) {
    fun String.norm() = toLowerCase().split('\\', '/') // XXX: Should this really be locale-sensitive?
    val p = path.norm()
    while (true) {
        val e = nextJarEntry ?: break
        if (!e.isDirectory && e.name.norm() == p) {
            copyTo(outputTo)
            return
        }
        closeEntry()
    }
    throw FileNotFoundException(path)
}

private object JarSignatureCollector {

    /** @see <https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Signed_JAR_File> */
    private val unsignableEntryName = "META-INF/(?:.*[.](?:SF|DSA|RSA)|SIG-.*)".toRegex()

    /**
     * An array which essentially serves as /dev/null for reading through a jar and obtaining its code signers.
     */
    private val shredder = ByteArray(1024)

    /**
     * Returns an ordered list of every [Party] which has signed every signable item in the given [JarInputStream].
     * Any party which has signed some but not all signable items is eliminated.
     *
     * @param jar The open [JarInputStream] to collect signing parties from.
     */
    fun collectSigningParties(jar: JarInputStream): List<Party> {
        // Can't start with empty set if we're doing intersections. Logically the null means "all possible signers":
        var attachmentSigners: MutableSet<CodeSigner>? = null

        for (signers in jar.getSignerSets()) {
            attachmentSigners = attachmentSigners?.intersect(signers) ?: signers.toMutableSet()

            if (attachmentSigners.isEmpty()) return emptyList() // Performance short-circuit.
        }

        return attachmentSigners?.toPartiesOrderedByName() ?: emptyList()
    }

    private fun <T> MutableSet<T>.intersect(others: Iterable<T>) = apply { retainAll(others) }

    private fun JarInputStream.getSignerSets() =
            entries().thatAreSignable().shreddedFrom(this).toSignerSets()

    private fun Sequence<JarEntry>.thatAreSignable() =
            filterNot { entry -> entry.isDirectory || unsignableEntryName.matches(entry.name) }

    private fun Sequence<JarEntry>.shreddedFrom(jar: JarInputStream) = map { entry ->
        entry.apply {
            while (jar.read(shredder) != -1) { // Must read entry fully for codeSigners to be valid.
                // Do nothing.
            }
        }
    }

    private fun Sequence<JarEntry>.toSignerSets() = map { entry -> entry.codeSigners?.toList() ?: emptyList() }

    private fun Set<CodeSigner>.toPartiesOrderedByName() = map {
        Party(it.signerCertPath.certificates[0] as X509Certificate)
    }.sortedBy { it.name.toString() } // Sorted for determinism.

    private fun JarInputStream.entries() = generateSequence(nextJarEntry) { nextJarEntry }
}

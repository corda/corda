@file:KeepForDJVM
package net.corda.core.internal

import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.MissingAttachmentsException
import net.corda.core.serialization.SerializeAsTokenContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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


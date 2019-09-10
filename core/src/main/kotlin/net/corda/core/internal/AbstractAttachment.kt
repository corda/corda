@file:KeepForDJVM

package net.corda.core.internal

import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractAttachment
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.serialization.MissingAttachmentsException
import net.corda.core.serialization.SerializeAsTokenContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import java.util.jar.JarInputStream

const val DEPLOYED_CORDAPP_UPLOADER = "app"
const val RPC_UPLOADER = "rpc"
const val P2P_UPLOADER = "p2p"
const val TESTDSL_UPLOADER = "TestDSL"
const val UNKNOWN_UPLOADER = "unknown"

// We whitelist sources of transaction JARs for now as a temporary state until the DJVM and other security sandboxes
// have been integrated, at which point we'll be able to run untrusted code downloaded over the network and this mechanism
// can be removed. Because we ARE downloading attachments over the P2P network in anticipation of this upgrade, we
// track the source of each attachment in our store. TestDSL is used by LedgerDSLInterpreter when custom attachments
// are added in unit test code.
val TRUSTED_UPLOADERS = listOf(DEPLOYED_CORDAPP_UPLOADER, RPC_UPLOADER, TESTDSL_UPLOADER)

fun isUploaderTrusted(uploader: String?): Boolean = uploader in TRUSTED_UPLOADERS

fun Attachment.isUploaderTrusted(): Boolean = when (this) {
    is ContractAttachment -> isUploaderTrusted(uploader)
    is AbstractAttachment -> isUploaderTrusted(uploader)
    else -> false
}

@KeepForDJVM
abstract class AbstractAttachment(dataLoader: () -> ByteArray, val uploader: String?) : Attachment {
    companion object {
        /**
         * Returns a function that knows how to load an attachment.
         *
         * TODO - this code together with the rest of the Attachment handling (including [FetchedAttachment]) needs some refactoring as it is really hard to follow.
         */
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

    override val signerKeys: List<PublicKey> by lazy {
        openAsJAR().use(JarSignatureCollector::collectSigners)
    }

    override val signers: List<Party> by lazy {
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


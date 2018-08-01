/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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

        /** @see <https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Signed_JAR_File> */
        private val unsignableEntryName = "META-INF/(?:.*[.](?:SF|DSA|RSA)|SIG-.*)".toRegex()
    }

    protected val attachmentData: ByteArray by lazy(dataLoader)

    // TODO: read file size information from metadata instead of loading the data.
    override val size: Int get() = attachmentData.size

    override fun open(): InputStream = attachmentData.inputStream()
    override val signers by lazy {
        // Can't start with empty set if we're doing intersections. Logically the null means "all possible signers":
        var attachmentSigners: MutableSet<CodeSigner>? = null
        openAsJAR().use { jar ->
            val shredder = ByteArray(1024)
            while (true) {
                val entry = jar.nextJarEntry ?: break
                if (entry.isDirectory || unsignableEntryName.matches(entry.name)) continue
                while (jar.read(shredder) != -1) { // Must read entry fully for codeSigners to be valid.
                    // Do nothing.
                }
                val entrySigners = entry.codeSigners ?: emptyArray()
                attachmentSigners?.retainAll(entrySigners) ?: run { attachmentSigners = entrySigners.toMutableSet() }
                if (attachmentSigners!!.isEmpty()) break // Performance short-circuit.
            }
        }
        (attachmentSigners ?: emptySet<CodeSigner>()).map {
            Party(it.signerCertPath.certificates[0] as X509Certificate)
        }.sortedBy { it.name.toString() } // Determinism.
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

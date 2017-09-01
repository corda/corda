package net.corda.testing.node

import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.serialization.SingletonSerializeAsToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.HashMap
import java.util.jar.JarInputStream

class MockAttachmentStorage : AttachmentStorage, SingletonSerializeAsToken() {
    val files = HashMap<SecureHash, ByteArray>()

    override fun openAttachment(id: SecureHash): Attachment? {
        val f = files[id] ?: return null
        return object : Attachment {
            override fun open(): InputStream = ByteArrayInputStream(f)
            override val id: SecureHash = id
        }
    }

    override fun importAttachment(jar: InputStream): SecureHash {
        // JIS makes read()/readBytes() return bytes of the current file, but we want to hash the entire container here.
        require(jar !is JarInputStream)

        val bytes = run {
            val s = ByteArrayOutputStream()
            jar.copyTo(s)
            s.close()
            s.toByteArray()
        }
        val sha256 = bytes.sha256()
        if (files.containsKey(sha256))
            throw FileAlreadyExistsException(File("!! MOCK FILE NAME"))
        files[sha256] = bytes
        return sha256
    }
}

package net.corda.deterministic.contracts

import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.PublicKey
import java.util.jar.JarOutputStream
import java.util.zip.Deflater.*
import java.util.zip.ZipEntry

class AttachmentTest {
    private companion object {
        private val data = byteArrayOf(0x73, 0x71, 0x18, 0x5F, 0x3A, 0x47, -0x22, 0x38)
        private val jarData: ByteArray = ByteArrayOutputStream().let { baos ->
           JarOutputStream(baos).use { jar ->
               jar.setLevel(BEST_COMPRESSION)
               jar.putNextEntry(ZipEntry("data.bin").apply { method = DEFLATED })
               data.inputStream().copyTo(jar)
           }
           baos.toByteArray()
        }

        private val ALICE_NAME = CordaX500Name("Alice Corp", "Madrid", "ES")
        private val ALICE_KEY: PublicKey = object : PublicKey {
            override fun getAlgorithm(): String = "TEST-256"
            override fun getFormat(): String = "<none>"
            override fun getEncoded() = byteArrayOf()
        }
        private val ALICE = Party(ALICE_NAME, ALICE_KEY)
    }

    private lateinit var attachment: Attachment

    @Before
    fun setup() {
        attachment = object : Attachment {
            override val id: SecureHash
                get() = SecureHash.allOnesHash
            override val signers: List<Party>
                get() = listOf(ALICE)
            override val size: Int
                get() = jarData.size

            override fun open(): InputStream {
                return jarData.inputStream()
            }
        }
    }

    @Test
    fun testAttachmentJar() {
        attachment.openAsJAR().use { jar ->
            val entry = jar.nextJarEntry ?: return@use
            assertEquals("data.bin", entry.name)
            val entryData = ByteArrayOutputStream().use {
                jar.copyTo(it)
                it.toByteArray()
            }
            assertArrayEquals(data, entryData)
        }
    }

    @Test
    fun testExtractFromAttachment() {
        val resultData = ByteArrayOutputStream().use {
            attachment.extractFile("data.bin", it)
            it.toByteArray()
        }
        assertArrayEquals(data, resultData)
    }
}
package net.corda.core.contracts

import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.PublicKey
import java.util.UUID
import java.util.jar.JarFile.MANIFEST_NAME
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class AttachmentTest {

    @Test(timeout=300_000)
    @Suppress("ThrowsCount")
	fun `openAsJAR does not leak file handle if attachment has corrupted manifest`() {
        var closeCalls = 0
        val inputStream = spy(ByteArrayOutputStream().apply {
            ZipOutputStream(this).use {
                with(it) {
                    putNextEntry(ZipEntry(MANIFEST_NAME))
                    write(ByteArray(512)) // One byte above the limit.
                }
            }
        }.toByteArray().inputStream()).apply { doAnswer { closeCalls += 1 }.whenever(this).close() }
        val attachment = object : Attachment {
            override val id get() = throw UnsupportedOperationException()
            override fun open() = inputStream
            override val signerKeys get() = throw UnsupportedOperationException()
            @Suppress("OVERRIDE_DEPRECATION")
            override val signers: List<Party> get() = throw UnsupportedOperationException()
            override val size: Int = 512
        }
        try {
            attachment.openAsJAR()
            fail("Expected line too long.")
        } catch (e: IOException) {
            assertTrue { e.message!!.contains("line too long") }
        }
        assertEquals(1, closeCalls)
    }

    data class FakeAttachment(
            override val id: SecureHash,
            val version: Int? = null
    ) : Attachment, HasContractVersion {
        override val signerKeys: List<PublicKey> = emptyList()
        @Suppress("OverridingDeprecatedMember")
        @Deprecated("Use signerKeys. There is no requirement that attachment signers are Corda parties.")
        override val signers: List<Party> = emptyList()
        override val size: Int = 0
        override val contractVersion: Int get() = version ?: 0
        override fun open(): InputStream = throw NotImplementedError()
    }

    @Test(timeout=300_000)
    fun `sort attachments by version descending then id alphabetically`() {
        val hash1 = SecureHash.sha256("hash1") // AF316ECB91A8EE7AE99210702B2D4758F30CDDE3BF61E3D8E787D74681F90A6E
        val hash2 = SecureHash.sha256("hash2") // E7BF382F6E5915B3F88619B866223EBF1D51C4C5321CCCDE2E9FF700A3259086
        val hash3 = SecureHash.sha256("hash3") // 42CAA4ABB7B60F8F914E5BFB8E6511D7D9BD9817DE719B74251755D97FE97BF1
        val hash4 = SecureHash.sha256("hash4") // 1C27099B3B84B13D0E3FBD299BA93AE7853EC1D0D3A4E5DAA89E68B7AD59D7CB
        val hash5 = SecureHash.sha256("hash5") // 7DA450AB64A26820E56DD73CD346950D656E60A20DBA00BD4BE9CED75BA7CDEF

        val attachment1 = FakeAttachment(hash1, 1)
        val attachment2 = FakeAttachment(hash2, 2)
        val attachment3 = FakeAttachment(hash3)
        val attachment4 = FakeAttachment(hash4)
        val attachment5 = FakeAttachment(hash5, 1)

        val sorted = listOf(attachment1, attachment2, attachment3, attachment4, attachment5).sort()
        val expectedOrder = listOf(
                attachment2, // highest version
                attachment5, // version 1, alphabetically
                attachment1, // version 1, alphabetically
                attachment4, // no version, alphabetically
                attachment3  // no version, alphabetically
        )

        assertThat(sorted).containsExactlyElementsOf(expectedOrder)
    }
}

class UniqueIdentifierTests {

    @Test(timeout=300_000)
	fun `unique identifier comparison`() {
        val ids = listOf(UniqueIdentifier.fromString("e363f00e-4759-494d-a7ca-0dc966a92494"),
                UniqueIdentifier.fromString("10ed0cc3-7bdf-4000-b610-595e36667d7d"),
                UniqueIdentifier("Test", UUID.fromString("10ed0cc3-7bdf-4000-b610-595e36667d7d"))
        )
        assertEquals(-1, ids[0].compareTo(ids[1]))
        assertEquals(1, ids[1].compareTo(ids[0]))
        assertEquals(0, ids[0].compareTo(ids[0]))
        // External ID is not taken into account
        assertEquals(0, ids[1].compareTo(ids[2]))
    }

    @Test(timeout=300_000)
	fun `unique identifier equality`() {
        val ids = listOf(UniqueIdentifier.fromString("e363f00e-4759-494d-a7ca-0dc966a92494"),
                UniqueIdentifier.fromString("10ed0cc3-7bdf-4000-b610-595e36667d7d"),
                UniqueIdentifier("Test", UUID.fromString("10ed0cc3-7bdf-4000-b610-595e36667d7d"))
        )
        assertEquals(ids[0], ids[0])
        assertNotEquals(ids[0], ids[1])
        assertEquals(ids[0].hashCode(), ids[0].hashCode())
        assertNotEquals(ids[0].hashCode(), ids[1].hashCode())
        // External ID is not taken into account
        assertEquals(ids[1], ids[2])
        assertEquals(ids[1].hashCode(), ids[2].hashCode())
    }
}

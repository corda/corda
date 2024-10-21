package net.corda.core.contracts

import net.corda.core.identity.Party
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.io.IOException
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

package net.corda.core.contracts

import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.spy
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.jar.JarFile.MANIFEST_NAME
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.fail

class AttachmentTest {

    @Test
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
        }
        try {
            attachment.openAsJAR()
            fail("Expected line too long.")
        } catch (e: IOException) {
            assertEquals("line too long", e.message)
        }
        assertEquals(1, closeCalls)
    }

}

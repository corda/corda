/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.contracts

import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.spy
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*
import java.util.jar.JarFile.MANIFEST_NAME
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
            override val signers get() = throw UnsupportedOperationException()
            override val size: Int = 512
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

class UniqueIdentifierTests {

    @Test
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

    @Test
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
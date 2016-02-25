/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node

import com.google.common.jimfs.Jimfs
import core.crypto.SecureHash
import core.use
import org.junit.Before
import org.junit.Test
import java.nio.charset.Charset
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NodeAttachmentStorageTest {
    // Use an in memory file system for testing attachment storage.
    lateinit var fs: FileSystem

    @Before
    fun setUp() {
        fs = Jimfs.newFileSystem()
    }

    @Test
    fun `insert and retrieve`() {
        val testJar = makeTestJar()
        val expectedHash = SecureHash.sha256(Files.readAllBytes(testJar))

        val storage = NodeAttachmentStorage(fs.getPath("/"))
        val id =  testJar.use { storage.importAttachment(it) }
        assertEquals(expectedHash, id)

        assertNull(storage.openAttachment(SecureHash.randomSHA256()))
        val stream = storage.openAttachment(expectedHash)!!.openAsJAR()
        val e1 = stream.nextJarEntry!!
        assertEquals("test1.txt", e1.name)
        assertEquals(stream.readBytes().toString(Charset.defaultCharset()), "This is some useful content")
        val e2 = stream.nextJarEntry!!
        assertEquals("test2.txt", e2.name)
        assertEquals(stream.readBytes().toString(Charset.defaultCharset()), "Some more useful content")
    }

    @Test
    fun `duplicates not allowed`() {
        val testJar = makeTestJar()
        val storage = NodeAttachmentStorage(fs.getPath("/"))
        testJar.use { storage.importAttachment(it) }
        assertFailsWith<java.nio.file.FileAlreadyExistsException> {
            testJar.use { storage.importAttachment(it) }
        }
    }

    @Test
    fun `corrupt entry throws exception`() {
        val testJar = makeTestJar()
        val storage = NodeAttachmentStorage(fs.getPath("/"))
        val id = testJar.use { storage.importAttachment(it) }

        // Corrupt the file in the store.
        Files.write(fs.getPath("/", id.toString()), "arggghhhh".toByteArray(), StandardOpenOption.WRITE)

        val e = assertFailsWith<NodeAttachmentStorage.OnDiskHashMismatch> {
            storage.openAttachment(id)!!.open().use { it.readBytes() }
        }
        assertEquals(e.file, storage.storePath.resolve(id.toString()))

        // But if we skip around and read a single entry, no exception is thrown.
        storage.openAttachment(id)!!.openAsJAR().use {
            it.nextJarEntry
            it.readBytes()
        }
    }

    private var counter = 0
    private fun makeTestJar(): Path {
        counter++
        val f = fs.getPath("$counter.jar")
        JarOutputStream(Files.newOutputStream(f)).use {
            it.putNextEntry(JarEntry("test1.txt"))
            it.write("This is some useful content".toByteArray())
            it.closeEntry()
            it.putNextEntry(JarEntry("test2.txt"))
            it.write("Some more useful content".toByteArray())
            it.closeEntry()
        }
        return f
    }
}
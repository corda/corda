package com.r3corda.node.services

import com.codahale.metrics.MetricRegistry
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.use
import com.r3corda.node.services.persistence.NodeAttachmentService
import org.junit.Before
import org.junit.Test
import java.nio.charset.Charset
import java.nio.file.*
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
        fs = Jimfs.newFileSystem(Configuration.unix())
    }

    @Test
    fun `insert and retrieve`() {
        val testJar = makeTestJar()
        val expectedHash = SecureHash.sha256(Files.readAllBytes(testJar))

        val storage = NodeAttachmentService(fs.getPath("/"), MetricRegistry())
        val id = testJar.use { storage.importAttachment(it) }
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
        val storage = NodeAttachmentService(fs.getPath("/"), MetricRegistry())
        testJar.use { storage.importAttachment(it) }
        assertFailsWith<FileAlreadyExistsException> {
            testJar.use { storage.importAttachment(it) }
        }
    }

    @Test
    fun `corrupt entry throws exception`() {
        val testJar = makeTestJar()
        val storage = NodeAttachmentService(fs.getPath("/"), MetricRegistry())
        val id = testJar.use { storage.importAttachment(it) }

        // Corrupt the file in the store.
        Files.write(fs.getPath("/", id.toString()), "arggghhhh".toByteArray(), StandardOpenOption.WRITE)

        val e = assertFailsWith<NodeAttachmentService.OnDiskHashMismatch> {
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
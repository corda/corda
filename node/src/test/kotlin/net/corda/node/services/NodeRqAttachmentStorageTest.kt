package net.corda.node.services

import com.codahale.metrics.MetricRegistry
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.requery.Persistable
import io.requery.sql.KotlinEntityDataStore
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.read
import net.corda.core.readAll
import net.corda.core.utilities.LogHelper
import net.corda.core.write
import net.corda.node.services.persistence.AttachmentTable
import net.corda.node.services.persistence.Models
import net.corda.node.services.persistence.NodeRqAttachmentService
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.node.makeTestDataSourceProperties
import org.jetbrains.exposed.sql.Database
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import java.nio.charset.Charset
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystem
import java.nio.file.Path
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NodeRqAttachmentStorageTest {
    // Use an in memory file system for testing attachment storage.
    lateinit var fs: FileSystem

    lateinit var dataSource: Closeable
    lateinit var database: Database

    lateinit var dataSourceProperties: Properties

    lateinit var configuration: RequeryConfiguration


    @Before
    fun setUp() {
        LogHelper.setLevel(PersistentUniquenessProvider::class)

        dataSourceProperties = makeTestDataSourceProperties()
        val dataSourceAndDatabase = configureDatabase(dataSourceProperties)
        dataSource = dataSourceAndDatabase.first
        database = dataSourceAndDatabase.second

        configuration = RequeryConfiguration(dataSourceProperties)
        fs = Jimfs.newFileSystem(Configuration.unix())
    }

    @Test
    fun `insert and retrieve`() {
        val testJar = makeTestJar()
        val expectedHash = testJar.readAll().sha256()

        databaseTransaction(database) {
            val storage = NodeRqAttachmentService(dataSourceProperties, MetricRegistry())
            val id = testJar.read { storage.importAttachment(it) }
            assertEquals(expectedHash, id)

            assertNull(storage.openAttachment(SecureHash.randomSHA256()))
            val stream = storage.openAttachment(expectedHash)!!.openAsJAR()
            val e1 = stream.nextJarEntry!!
            assertEquals("test1.txt", e1.name)
            assertEquals(stream.readBytes().toString(Charset.defaultCharset()), "This is some useful content")
            val e2 = stream.nextJarEntry!!
            assertEquals("test2.txt", e2.name)
            assertEquals(stream.readBytes().toString(Charset.defaultCharset()), "Some more useful content")

            stream.close()

            storage.openAttachment(id)!!.openAsJAR().use {
                it.nextJarEntry
                it.readBytes()
            }
        }
    }

    @Test
    fun `duplicates not allowed`() {

        val testJar = makeTestJar()
        databaseTransaction(database) {
            val storage = NodeRqAttachmentService(dataSourceProperties, MetricRegistry())
            testJar.read {
                val id = storage.importAttachment(it)
                println(id)
            }
            assertFailsWith<FileAlreadyExistsException> {
                testJar.read {
                    val id = storage.importAttachment(it)
                    println(id)
                }
            }
        }
    }

    @Test
    fun `corrupt entry throws exception`() {
        val testJar = makeTestJar()
        databaseTransaction(database)
        {
            val storage = NodeRqAttachmentService(dataSourceProperties, MetricRegistry())
            val id = testJar.read { storage.importAttachment(it) }

            // Corrupt the file in the store.
            val x = testJar.readAll();
            val y = "arggghhhh".toByteArray()
            System.arraycopy(y, 0, x, 0, y.size)
            val corrupt = AttachmentTable()
            corrupt.attId = id
            corrupt.content = x
            storage.session.update(corrupt)

            val e = assertFailsWith<NodeRqAttachmentService.HashMismatchException> {
                storage.openAttachment(id)!!.open().use { it.readBytes() }
            }
            assertEquals(e.expected, id)

            // But if we skip around and read a single entry, no exception is thrown.
            storage.openAttachment(id)!!.openAsJAR().use {
                it.nextJarEntry
                it.readBytes()
            }
        }
    }

    private var counter = 0
    private fun makeTestJar(): Path {
        counter++
        val file = fs.getPath("$counter.jar")
        file.write {
            val jar = JarOutputStream(it)
            jar.putNextEntry(JarEntry("test1.txt"))
            jar.write("This is some useful content".toByteArray())
            jar.closeEntry()
            jar.putNextEntry(JarEntry("test2.txt"))
            jar.write("Some more useful content".toByteArray())
            jar.closeEntry()
        }
        return file
    }
}

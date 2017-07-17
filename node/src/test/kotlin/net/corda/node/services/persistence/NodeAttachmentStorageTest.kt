package net.corda.node.services.persistence

import com.codahale.metrics.MetricRegistry
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.read
import net.corda.core.readAll
import net.corda.testing.LogHelper
import net.corda.core.write
import net.corda.core.writeLines
import net.corda.node.services.database.RequeryConfiguration
import net.corda.node.services.persistence.schemas.requery.AttachmentEntity
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.configureDatabase
import net.corda.testing.node.makeTestDataSourceProperties
import org.junit.After
import org.junit.Before
import org.junit.Test
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

class NodeAttachmentStorageTest {
    // Use an in memory file system for testing attachment storage.
    lateinit var fs: FileSystem
    lateinit var database: CordaPersistence
    lateinit var dataSourceProperties: Properties
    lateinit var configuration: RequeryConfiguration

    @Before
    fun setUp() {
        LogHelper.setLevel(PersistentUniquenessProvider::class)

        dataSourceProperties = makeTestDataSourceProperties()
        database = configureDatabase(dataSourceProperties)

        configuration = RequeryConfiguration(dataSourceProperties)
        fs = Jimfs.newFileSystem(Configuration.unix())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert and retrieve`() {
        val testJar = makeTestJar()
        val expectedHash = testJar.readAll().sha256()

        database.transaction {
            val storage = NodeAttachmentService(fs.getPath("/"), dataSourceProperties, MetricRegistry())
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
        database.transaction {
            val storage = NodeAttachmentService(fs.getPath("/"), dataSourceProperties, MetricRegistry())
            testJar.read {
                storage.importAttachment(it)
            }
            assertFailsWith<FileAlreadyExistsException> {
                testJar.read {
                    storage.importAttachment(it)
                }
            }
        }
    }

    @Test
    fun `corrupt entry throws exception`() {
        val testJar = makeTestJar()
        database.transaction {
            val storage = NodeAttachmentService(fs.getPath("/"), dataSourceProperties, MetricRegistry())
            val id = testJar.read { storage.importAttachment(it) }

            // Corrupt the file in the store.
            val bytes = testJar.readAll()
            val corruptBytes = "arggghhhh".toByteArray()
            System.arraycopy(corruptBytes, 0, bytes, 0, corruptBytes.size)
            val corruptAttachment = AttachmentEntity()
            corruptAttachment.attId = id
            corruptAttachment.content = bytes
            storage.session.update(corruptAttachment)

            val e = assertFailsWith<NodeAttachmentService.HashMismatchException> {
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

    @Test
    fun `non jar rejected`() {
        database.transaction {
            val storage = NodeAttachmentService(fs.getPath("/"), dataSourceProperties, MetricRegistry())
            val path = fs.getPath("notajar")
            path.writeLines(listOf("Hey", "there!"))
            path.read {
                assertFailsWith<IllegalArgumentException>("either empty or not a JAR") {
                    storage.importAttachment(it)
                }
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

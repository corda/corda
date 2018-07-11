package net.corda.node.services.persistence

import com.codahale.metrics.MetricRegistry
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.*
import net.corda.core.node.services.vault.AttachmentQueryCriteria
import net.corda.core.node.services.vault.AttachmentSort
import net.corda.core.node.services.vault.Builder
import net.corda.core.node.services.vault.Sort
import net.corda.node.internal.configureDatabase
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.internal.LogHelper
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystem
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NodeAttachmentStorageTest {
    // Use an in memory file system for testing attachment storage.
    private lateinit var fs: FileSystem
    private lateinit var database: CordaPersistence
    private lateinit var storage: NodeAttachmentService

    @Before
    fun setUp() {
        LogHelper.setLevel(PersistentUniquenessProvider::class)

        val dataSourceProperties = makeTestDataSourceProperties()
        database = configureDatabase(dataSourceProperties, DatabaseConfig(), { null }, { null })
        fs = Jimfs.newFileSystem(Configuration.unix())
        storage = NodeAttachmentService(MetricRegistry(), database = database).also { it.start() }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert and retrieve`() {
        val (testJar, expectedHash) = makeTestJar()

        val id = testJar.read { storage.importAttachment(it, "test", null) }
        assertEquals(expectedHash, id)

        assertNull(storage.openAttachment(SecureHash.randomSHA256()))
        val stream = storage.openAttachment(expectedHash)!!.openAsJAR()
        val e1 = stream.nextJarEntry!!
        assertEquals("test1.txt", e1.name)
        assertEquals(stream.readBytes().toString(StandardCharsets.UTF_8), "This is some useful content")
        val e2 = stream.nextJarEntry!!
        assertEquals("test2.txt", e2.name)
        assertEquals(stream.readBytes().toString(StandardCharsets.UTF_8), "Some more useful content")

        stream.close()

        storage.openAttachment(id)!!.openAsJAR().use {
            it.nextJarEntry
            it.readBytes()
        }
    }

    @Test
    fun `missing is not cached`() {
        val (testJar, expectedHash) = makeTestJar()
        val (jarB, hashB) = makeTestJar(listOf(Pair("file", "content")))

        val id = testJar.read { storage.importAttachment(it, "test", null) }
        assertEquals(expectedHash, id)


        assertNull(storage.openAttachment(hashB))
        val stream = storage.openAttachment(expectedHash)!!.openAsJAR()
        val e1 = stream.nextJarEntry!!
        assertEquals("test1.txt", e1.name)
        assertEquals(stream.readBytes().toString(StandardCharsets.UTF_8), "This is some useful content")
        val e2 = stream.nextJarEntry!!
        assertEquals("test2.txt", e2.name)
        assertEquals(stream.readBytes().toString(StandardCharsets.UTF_8), "Some more useful content")

        stream.close()

        val idB = jarB.read { storage.importAttachment(it, "test", null) }
        assertEquals(hashB, idB)

        storage.openAttachment(id)!!.openAsJAR().use {
            it.nextJarEntry
            it.readBytes()
        }

        storage.openAttachment(idB)!!.openAsJAR().use {
            it.nextJarEntry
            it.readBytes()
        }
    }

    @Test
    fun `metadata can be used to search`() {
        val (jarA, _) = makeTestJar()
        val (jarB, hashB) = makeTestJar(listOf(Pair("file", "content")))
        val (jarC, hashC) = makeTestJar(listOf(Pair("magic_file", "magic_content_puff")))

        @Suppress("DEPRECATION")
        jarA.read { storage.importAttachment(it) }
        jarB.read { storage.importAttachment(it, "uploaderB", "fileB.zip") }
        jarC.read { storage.importAttachment(it, "uploaderC", "fileC.zip") }

        assertEquals(
                listOf(hashB),
                storage.queryAttachments(AttachmentQueryCriteria.AttachmentsQueryCriteria(Builder.equal("uploaderB")))
        )

        assertEquals(
                listOf(hashB, hashC),
                storage.queryAttachments(AttachmentQueryCriteria.AttachmentsQueryCriteria(Builder.like("%uploader%")))
        )
    }

    @Test
    fun `sorting and compound conditions work`() {
        val (jarA, hashA) = makeTestJar(listOf(Pair("a", "a")))
        val (jarB, hashB) = makeTestJar(listOf(Pair("b", "b")))
        val (jarC, hashC) = makeTestJar(listOf(Pair("c", "c")))

        fun uploaderCondition(s: String) = AttachmentQueryCriteria.AttachmentsQueryCriteria(uploaderCondition = Builder.equal(s))
        fun filenamerCondition(s: String) = AttachmentQueryCriteria.AttachmentsQueryCriteria(filenameCondition = Builder.equal(s))

        fun filenameSort(direction: Sort.Direction) = AttachmentSort(listOf(AttachmentSort.AttachmentSortColumn(AttachmentSort.AttachmentSortAttribute.FILENAME, direction)))

        jarA.read { storage.importAttachment(it, "complexA", "archiveA.zip") }
        jarB.read { storage.importAttachment(it, "complexB", "archiveB.zip") }
        jarC.read { storage.importAttachment(it, "complexC", "archiveC.zip") }

        // DOCSTART AttachmentQueryExample1

        assertEquals(
                emptyList(),
                storage.queryAttachments(
                        AttachmentQueryCriteria.AttachmentsQueryCriteria(uploaderCondition = Builder.equal("complexA"))
                                .and(AttachmentQueryCriteria.AttachmentsQueryCriteria(uploaderCondition = Builder.equal("complexB"))))
        )

        assertEquals(
                listOf(hashA, hashB),
                storage.queryAttachments(

                        AttachmentQueryCriteria.AttachmentsQueryCriteria(uploaderCondition = Builder.equal("complexA"))
                                .or(AttachmentQueryCriteria.AttachmentsQueryCriteria(uploaderCondition = Builder.equal("complexB"))))
        )

        val complexCondition =
                (uploaderCondition("complexB").and(filenamerCondition("archiveB.zip"))).or(filenamerCondition("archiveC.zip"))

        // DOCEND AttachmentQueryExample1

        assertEquals(
                listOf(hashB, hashC),
                storage.queryAttachments(complexCondition, sorting = filenameSort(Sort.Direction.ASC))
        )
        assertEquals(
                listOf(hashC, hashB),
                storage.queryAttachments(complexCondition, sorting = filenameSort(Sort.Direction.DESC))
        )
    }

    @Ignore("We need to be able to restart nodes - make importing attachments idempotent?")
    @Test
    fun `duplicates not allowed`() {
        val (testJar, _) = makeTestJar()
        testJar.read {
            storage.importAttachment(it, "test", null)
        }
        assertFailsWith<FileAlreadyExistsException> {
            testJar.read {
                storage.importAttachment(it, "test", null)
            }
        }
    }

    @Test
    fun `corrupt entry throws exception`() {
        val (testJar, _) = makeTestJar()
        val id = database.transaction {
            val id = testJar.read { storage.importAttachment(it, "test", null) }

            // Corrupt the file in the store.
            val bytes = testJar.readAll()
            val corruptBytes = "arggghhhh".toByteArray()
            System.arraycopy(corruptBytes, 0, bytes, 0, corruptBytes.size)
            val corruptAttachment = NodeAttachmentService.DBAttachment(attId = id.toString(), content = bytes)
            session.merge(corruptAttachment)
            id
        }
        val e = assertFailsWith<NodeAttachmentService.HashMismatchException> {
            storage.openAttachment(id)!!.open().readFully()
        }
        assertEquals(e.expected, id)

        // But if we skip around and read a single entry, no exception is thrown.
        storage.openAttachment(id)!!.openAsJAR().use {
            it.nextJarEntry
            it.readBytes()
        }
    }

    @Test
    fun `non jar rejected`() {
        val path = fs.getPath("notajar")
        path.writeLines(listOf("Hey", "there!"))
        path.read {
            assertFailsWith<IllegalArgumentException>("either empty or not a JAR") {
                storage.importAttachment(it, "test", null)
            }
        }
    }

    private var counter = 0
    private fun makeTestJar(extraEntries: List<Pair<String, String>> = emptyList()): Pair<Path, SecureHash> {
        counter++
        val file = fs.getPath("$counter.jar")
        file.write {
            val jar = JarOutputStream(it)
            jar.putNextEntry(JarEntry("test1.txt"))
            jar.write("This is some useful content".toByteArray())
            jar.closeEntry()
            jar.putNextEntry(JarEntry("test2.txt"))
            jar.write("Some more useful content".toByteArray())
            extraEntries.forEach {
                jar.putNextEntry(JarEntry(it.first))
                jar.write(it.second.toByteArray())
            }
            jar.closeEntry()
        }
        return Pair(file, file.readAll().sha256())
    }
}

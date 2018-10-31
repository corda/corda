package net.corda.node.services.persistence

import co.paralleluniverse.fibers.Suspendable
import com.codahale.metrics.MetricRegistry
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.ContractAttachment
import net.corda.testing.core.JarSignatureTestUtils.createJar
import net.corda.testing.core.JarSignatureTestUtils.generateKey
import net.corda.testing.core.JarSignatureTestUtils.signJar
import net.corda.core.cJarSignatureTestUtilsrypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.*
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.vault.AttachmentQueryCriteria
import net.corda.core.node.services.vault.AttachmentSort
import net.corda.core.node.services.vault.Builder
import net.corda.core.node.services.vault.Sort
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.internal.LogHelper
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.*
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.security.PublicKey
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull


class NodeAttachmentServiceTest {

    // Use an in memory file system for testing attachment storage.
    private lateinit var fs: FileSystem
    private lateinit var database: CordaPersistence
    private lateinit var storage: NodeAttachmentService
    private val services = rigorousMock<ServicesForResolution>()

    @Before
    fun setUp() {
        LogHelper.setLevel(PersistentUniquenessProvider::class)

        val dataSourceProperties = makeTestDataSourceProperties()
        database = configureDatabase(dataSourceProperties, DatabaseConfig(), { null }, { null })
        fs = Jimfs.newFileSystem(Configuration.unix())

        doReturn(testNetworkParameters()).whenever(services).networkParameters

        storage = NodeAttachmentService(MetricRegistry(), TestingNamedCacheFactory(), database).also {
            database.transaction {
                it.start()
            }
        }
        storage.servicesForResolution = services
    }

    @After
    fun tearDown() {
        dir.list { subdir ->
            subdir.forEach(Path::deleteRecursively)
        }
        database.close()
    }

    @Test
    fun `importing a signed jar saves the signers to the storage`() {
        val jarAndSigner = makeTestSignedContractJar("com.example.MyContract")
        val signedJar = jarAndSigner.first
        val attachmentId = storage.importAttachment(signedJar.inputStream(), "test", null)
        assertEquals(listOf(jarAndSigner.second.hash), storage.openAttachment(attachmentId)!!.signers.map { it.hash })
    }

    @Test
    fun `importing a non-signed jar will save no signers`() {
        val jarName = makeTestContractJar("com.example.MyContract")
        val attachmentId = storage.importAttachment(dir.resolve(jarName).inputStream(), "test", null)
        assertEquals(0, storage.openAttachment(attachmentId)!!.signers.size)
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
    fun `insert contract attachment as an untrusted uploader and then as trusted CorDapp uploader`() {
        val contractJarName = makeTestContractJar("com.example.MyContract")
        val testJar = dir.resolve(contractJarName)
        val expectedHash = testJar.readAll().sha256()

        // PRIVILEGED_UPLOADERS = listOf(DEPLOYED_CORDAPP_UPLOADER, RPC_UPLOADER, P2P_UPLOADER, UNKNOWN_UPLOADER)
        // TRUSTED_UPLOADERS = listOf(DEPLOYED_CORDAPP_UPLOADER, RPC_UPLOADER)

        database.transaction {
            val id = testJar.read { storage.privilegedImportOrGetAttachment(it, P2P_UPLOADER, null) }
            assertEquals(expectedHash, id)
            val attachment1 = storage.openAttachment(expectedHash)

            val id2 = testJar.read { storage.privilegedImportOrGetAttachment(it, DEPLOYED_CORDAPP_UPLOADER, null) }
            assertEquals(expectedHash, id2)
            val attachment2 = storage.openAttachment(expectedHash)

            assertNotEquals(attachment1, attachment2)
            assertEquals(P2P_UPLOADER, (attachment1 as ContractAttachment).uploader)
            assertEquals(DEPLOYED_CORDAPP_UPLOADER, (attachment2 as ContractAttachment).uploader)
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
        val (testJar) = makeTestJar()
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
        val (testJar) = makeTestJar()
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

    @Test
    fun `using reserved uploader tokens`() {
        val (testJar) = makeTestJar()

        fun assertImportFails(uploader: String) {
            testJar.read {
                assertThatIllegalArgumentException().isThrownBy {
                    storage.importAttachment(it, uploader, null)
                }.withMessageContaining(uploader)
            }
        }

        database.transaction {
            assertImportFails(DEPLOYED_CORDAPP_UPLOADER)
            assertImportFails(P2P_UPLOADER)
            assertImportFails(RPC_UPLOADER)
            assertImportFails(UNKNOWN_UPLOADER)
        }

        // Import an attachment similar to how net.corda.core.internal.FetchAttachmentsFlow does it.
        InternalMockNetwork(threadPerNode = true).use { mockNet ->
            val node = mockNet.createNode()
            val result = node.services.startFlow(FetchAttachmentsFlow()).resultFuture
            assertThatIllegalArgumentException().isThrownBy {
                result.getOrThrow()
            }.withMessageContaining(P2P_UPLOADER)
        }
    }

    // Not the real FetchAttachmentsFlow!
    private class FetchAttachmentsFlow : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val baos = ByteArrayOutputStream()
            makeTestJar(baos)
            serviceHub.attachments.importAttachment(baos.toByteArray().inputStream(), "$P2P_UPLOADER:${ourIdentity.name}", null)
        }
    }

    private var counter = 0
    private fun makeTestJar(extraEntries: List<Pair<String, String>> = emptyList()): Pair<Path, SecureHash> {
        counter++
        val file = fs.getPath("$counter.jar")
        makeTestJar(file.outputStream(), extraEntries)
        return Pair(file, file.readAll().sha256())
    }

    companion object {
        private val dir = Files.createTempDirectory(NodeAttachmentServiceTest::class.simpleName)

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            dir.deleteRecursively()
        }

        private fun makeTestJar(output: OutputStream, extraEntries: List<Pair<String, String>> = emptyList()) {
            output.use {
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
        }

        private fun makeTestSignedContractJar(contractName: String): Pair<Path, PublicKey> {
            val alias = "testAlias"
            val pwd = "testPassword"
            dir.generateKey(alias, pwd, ALICE_NAME.toString())
            val jarName = makeTestContractJar(contractName)
            val signer = dir.signJar(jarName, alias, pwd)
            return dir.resolve(jarName) to signer
        }

        private fun makeTestContractJar(contractName: String): String {
            val packages = contractName.split(".")
            val jarName = "testattachment.jar"
            val className = packages.last()
            createTestClass(className, packages.subList(0, packages.size - 1))
            dir.createJar(jarName, "${contractName.replace(".", "/")}.class")
            return jarName
        }

        private fun createTestClass(className: String, packages: List<String>): Path {
            val newClass = """package ${packages.joinToString(".")};
                import net.corda.core.contracts.*;
                import net.corda.core.transactions.*;

                public class $className implements Contract {
                    @Override
                    public void verify(LedgerTransaction tx) throws IllegalArgumentException {
                    }
                }
            """.trimIndent()
            val compiler = ToolProvider.getSystemJavaCompiler()
            val source = object : SimpleJavaFileObject(URI.create("string:///${packages.joinToString("/")}/${className}.java"), JavaFileObject.Kind.SOURCE) {
                override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence {
                    return newClass
                }
            }
            val fileManager = compiler.getStandardFileManager(null, null, null)
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(dir.toFile()))

            val compile = compiler.getTask(System.out.writer(), fileManager, null, null, null, listOf(source)).call()
            return Paths.get(fileManager.list(StandardLocation.CLASS_OUTPUT, "", setOf(JavaFileObject.Kind.CLASS), true).single().name)
        }
    }

}

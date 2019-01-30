package net.corda.node.services.persistence

import co.paralleluniverse.fibers.Suspendable
import com.codahale.metrics.MetricRegistry
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.ContractAttachment
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.*
import net.corda.core.internal.cordapp.CordappImpl.Companion.DEFAULT_CORDAPP_VERSION
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.vault.AttachmentQueryCriteria.AttachmentsQueryCriteria
import net.corda.core.node.services.vault.AttachmentSort
import net.corda.core.node.services.vault.Builder
import net.corda.core.node.services.vault.Sort
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.nodeapi.exceptions.DuplicateAttachmentException
import net.corda.nodeapi.exceptions.DuplicateContractClassException
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.internal.ContractJarTestUtils.makeTestContractJar
import net.corda.testing.core.internal.ContractJarTestUtils.makeTestJar
import net.corda.testing.core.internal.ContractJarTestUtils.makeTestSignedContractJar
import net.corda.testing.core.internal.SelfCleaningDir
import net.corda.testing.internal.*
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions.*
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystem
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class NodeAttachmentServiceTest {

    // Use an in memory file system for testing attachment storage.
    private lateinit var fs: FileSystem
    private lateinit var database: CordaPersistence
    private lateinit var storage: NodeAttachmentService
    private val services = rigorousMock<ServicesForResolution>().also {
        doReturn(testNetworkParameters()).whenever(it).networkParameters
    }

    @Before
    fun setUp() {
        LogHelper.setLevel(PersistentUniquenessProvider::class)

        val dataSourceProperties = makeTestDataSourceProperties()
        database = configureDatabase(dataSourceProperties, DatabaseConfig(), { null }, { null })
        fs = Jimfs.newFileSystem(Configuration.unix())

        storage = NodeAttachmentService(MetricRegistry(), TestingNamedCacheFactory(), database).also {
            database.transaction {
                it.start()
            }
        }
        storage.servicesForResolution = services
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `importing a signed jar saves the signers to the storage`() {
        SelfCleaningDir().use { file ->
            val jarAndSigner = makeTestSignedContractJar(file.path, "com.example.MyContract")
            val signedJar = jarAndSigner.first
            signedJar.inputStream().use { jarStream ->
                val attachmentId = storage.importAttachment(jarStream, "test", null)
                assertEquals(listOf(jarAndSigner.second.hash), storage.openAttachment(attachmentId)!!.signerKeys.map { it.hash })
            }
        }
    }

    @Test
    fun `importing a non-signed jar will save no signers`() {
        SelfCleaningDir().use {
            val jarName = makeTestContractJar(it.path, "com.example.MyContract")
            it.path.resolve(jarName).inputStream().use { jarStream ->
                val attachmentId = storage.importAttachment(jarStream, "test", null)
                assertEquals(0, storage.openAttachment(attachmentId)!!.signerKeys.size)
            }
        }
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
    fun `attachment can be overridden by trusted uploader`() {
        SelfCleaningDir().use { file ->
            val contractJarName = makeTestContractJar(file.path, "com.example.MyContract")
            val attachment = file.path.resolve(contractJarName)
            val expectedAttachmentId = attachment.readAll().sha256()

            val initialUploader = "test"
            val attachmentId = attachment.read { storage.privilegedImportAttachment(it, initialUploader, null) }
            assertThat(attachmentId).isEqualTo(expectedAttachmentId)
            assertThat((storage.openAttachment(expectedAttachmentId) as ContractAttachment).uploader).isEqualTo(initialUploader)

            val trustedUploader = TRUSTED_UPLOADERS.randomOrNull()!!

            val overriddenAttachmentId = attachment.read { storage.privilegedImportAttachment(it, trustedUploader, null) }
            assertThat(overriddenAttachmentId).isEqualTo(expectedAttachmentId)
            assertThat((storage.openAttachment(expectedAttachmentId) as ContractAttachment).uploader).isEqualTo(trustedUploader)
        }
    }

    @Test
    fun `attachment cannot be overridden by untrusted uploader`() {
        SelfCleaningDir().use { file ->
            val contractJarName = makeTestContractJar(file.path, "com.example.MyContract")
            val attachment = file.path.resolve(contractJarName)
            val expectedAttachmentId = attachment.readAll().sha256()

            val trustedUploader = TRUSTED_UPLOADERS.randomOrNull()!!
            val attachmentId = attachment.read { storage.privilegedImportAttachment(it, trustedUploader, null) }
            assertThat(attachmentId).isEqualTo(expectedAttachmentId)
            assertThat((storage.openAttachment(expectedAttachmentId) as ContractAttachment).uploader).isEqualTo(trustedUploader)

            val untrustedUploader = "test"
            assertThatThrownBy { attachment.read { storage.privilegedImportAttachment(it, untrustedUploader, null) } }.isInstanceOf(DuplicateAttachmentException::class.java)
        }
    }

    @Test
    fun `insert contract attachment as an untrusted uploader and then as trusted CorDapp uploader`() {
        SelfCleaningDir().use { file ->
            val contractJarName = makeTestContractJar(file.path, "com.example.MyContract")
            val testJar = file.path.resolve(contractJarName)
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
                storage.queryAttachments(AttachmentsQueryCriteria(Builder.equal("uploaderB")))
        )

        assertEquals(
                listOf(hashB, hashC),
                storage.queryAttachments(AttachmentsQueryCriteria(Builder.like("%uploader%")))
        )
    }

    @Test
    fun `contract class, versioning and signing metadata can be used to search`() {
        SelfCleaningDir().use { file ->
            val (sampleJar, _) = makeTestJar()
            val contractJar = makeTestContractJar(file.path, "com.example.MyContract")
            val (signedContractJar, publicKey) = makeTestSignedContractJar(file.path, "com.example.MyContract")
            val (anotherSignedContractJar, _) = makeTestSignedContractJar(file.path,"com.example.AnotherContract")
            val contractJarV2 = makeTestContractJar(file.path,"com.example.MyContract", version = 2)
            val (signedContractJarV2, _) = makeTestSignedContractJar(file.path,"com.example.MyContract", version = 2)

            sampleJar.read { storage.importAttachment(it, "uploaderA", "sample.jar") }
            contractJar.read { storage.importAttachment(it, "uploaderB", "contract.jar") }
            signedContractJar.read { storage.importAttachment(it, "uploaderC", "contract-signed.jar") }
            anotherSignedContractJar.read { storage.importAttachment(it, "uploaderD", "another-contract-signed.jar") }
            contractJarV2.read { storage.importAttachment(it, "uploaderB", "contract-V2.jar") }
            signedContractJarV2.read { storage.importAttachment(it, "uploaderC", "contract-signed-V2.jar") }

            assertEquals(
                    4,
                    storage.queryAttachments(AttachmentsQueryCriteria(contractClassNamesCondition = Builder.equal(listOf("com.example.MyContract")))).size
            )

            assertEquals(
                    1,
                    storage.queryAttachments(AttachmentsQueryCriteria(signersCondition = Builder.equal(listOf(publicKey)))).size
            )

            val allAttachments = storage.queryAttachments(AttachmentsQueryCriteria())
            assertEquals(6, allAttachments.size)

            val signedAttachments = storage.queryAttachments(AttachmentsQueryCriteria(isSignedCondition = Builder.equal(true)))
            assertEquals(3, signedAttachments.size)

            val unsignedAttachments = storage.queryAttachments(AttachmentsQueryCriteria(isSignedCondition = Builder.equal(false)))
            assertEquals(3, unsignedAttachments.size)

            assertNotEquals(signedAttachments.toSet(), unsignedAttachments.toSet())

            assertEquals(signedAttachments.toSet() + unsignedAttachments.toSet(), allAttachments.toSet())

            assertEquals(
                    1,
                    storage.queryAttachments(AttachmentsQueryCriteria(
                            contractClassNamesCondition = Builder.equal(listOf("com.example.MyContract")),
                            versionCondition = Builder.equal(2),
                            isSignedCondition = Builder.equal(true))).size
            )

            assertEquals(
                    2,
                    storage.queryAttachments(AttachmentsQueryCriteria(
                            contractClassNamesCondition = Builder.equal(listOf("com.example.MyContract", "com.example.AnotherContract")),
                            versionCondition = Builder.equal(1),
                            isSignedCondition = Builder.equal(true))).size
            )

            assertEquals(
                    2,storage.queryAttachments(AttachmentsQueryCriteria(
                    contractClassNamesCondition = Builder.equal(listOf("com.example.MyContract")),
                    versionCondition = Builder.greaterThanOrEqual(1),
                    isSignedCondition = Builder.equal(true)),
                    AttachmentSort(listOf(AttachmentSort.AttachmentSortColumn(AttachmentSort.AttachmentSortAttribute.VERSION)))).size
            )

            assertEquals(
                    1,storage.queryAttachments(AttachmentsQueryCriteria(
                    contractClassNamesCondition = Builder.equal(listOf("com.example.MyContract")),
                    versionCondition = Builder.greaterThanOrEqual(2),
                    isSignedCondition = Builder.equal(true)),
                    AttachmentSort(listOf(AttachmentSort.AttachmentSortColumn(AttachmentSort.AttachmentSortAttribute.VERSION)))).size
            )

            assertEquals(
                    0,storage.queryAttachments(AttachmentsQueryCriteria(
                    contractClassNamesCondition = Builder.equal(listOf("com.example.MyContract")),
                    versionCondition = Builder.greaterThanOrEqual(10),
                    isSignedCondition = Builder.equal(true)),
                    AttachmentSort(listOf(AttachmentSort.AttachmentSortColumn(AttachmentSort.AttachmentSortAttribute.VERSION)))).size
            )
        }
    }

    @Test
    fun `cannot import jar with duplicated contract class, version and signers for trusted uploader`() {
        SelfCleaningDir().use { file ->
            val (contractJar, _) = makeTestSignedContractJar(file.path, "com.example.MyContract")
            val anotherContractJar = makeTestContractJar(file.path, listOf( "com.example.MyContract", "com.example.AnotherContract"), true, generateManifest = false, jarFileName = "another-sample.jar")
            contractJar.read { storage.privilegedImportAttachment(it, "app", "sample.jar") }

            assertThatExceptionOfType(DuplicateContractClassException::class.java).isThrownBy {
                anotherContractJar.read { storage.privilegedImportAttachment(it, "app", "another-sample.jar") }
            }
        }
    }

    @Test
    fun `can import jar with duplicated contract class, version and signers - when one uploader is trusted and other isnt`() {
        SelfCleaningDir().use { file ->
            val (contractJar, _) = makeTestSignedContractJar(file.path, "com.example.MyContract")
            val anotherContractJar = makeTestContractJar(file.path, listOf( "com.example.MyContract", "com.example.AnotherContract"), true, generateManifest = false, jarFileName = "another-sample.jar")
            val attachmentId =  contractJar.read { storage.importAttachment(it, "uploaderA", "sample.jar") }
            val anotherAttachmentId = anotherContractJar.read { storage.privilegedImportAttachment(it, "app", "another-sample.jar") }
            assertNotEquals(attachmentId, anotherAttachmentId)
        }
    }

    @Test
    fun `can promote to trusted uploader for the same attachment`() {
        SelfCleaningDir().use { file ->
            val (contractJar, _) = makeTestSignedContractJar(file.path, "com.example.MyContract")
            val attachmentId = contractJar.read { storage.importAttachment(it, "uploaderA", "sample.jar") }
            val reimportedAttachmentId = contractJar.read { storage.privilegedImportAttachment(it, "app", "sample.jar") }
            assertEquals(attachmentId, reimportedAttachmentId)
        }
    }

    @Test
    fun `cannot promote to trusted uploader if other trusted attachment already has duplicated contract class, version and signers`() {
        SelfCleaningDir().use { file ->
            val (contractJar, _) = makeTestSignedContractJar(file.path, "com.example.MyContract")
            contractJar.read { storage.importAttachment(it, "uploaderA", "sample.jar") }
            val anotherContractJar = makeTestContractJar(file.path, listOf( "com.example.MyContract", "com.example.AnotherContract"), true, generateManifest = false, jarFileName = "another-sample.jar")
            anotherContractJar.read { storage.privilegedImportAttachment(it, "app", "another-sample.jar") }

            assertThatExceptionOfType(DuplicateContractClassException::class.java).isThrownBy {
                contractJar.read { storage.privilegedImportAttachment(it, "app", "sample.jar") }
            }

        }
    }

    @Test
    fun `cannot promote to trusted uploder the same jar if other trusted uplodaer `() {
        SelfCleaningDir().use { file ->
            val (contractJar, _) = makeTestSignedContractJar(file.path, "com.example.MyContract")
            val anotherContractJar = makeTestContractJar(file.path, listOf( "com.example.MyContract", "com.example.AnotherContract"), true, generateManifest = false, jarFileName = "another-sample.jar")
            contractJar.read { storage.privilegedImportAttachment(it, "app", "sample.jar") }

            assertThatExceptionOfType(DuplicateContractClassException::class.java).isThrownBy {
                anotherContractJar.read { storage.privilegedImportAttachment(it, "app", "another-sample.jar") }
            }
        }
    }

    @Test
    fun `can import duplicated contract class and signers if versions differ`() {
        SelfCleaningDir().use { file ->
            val (contractJar, _) = makeTestSignedContractJar(file.path, "com.example.MyContract",  2)
            val anotherContractJar = makeTestContractJar(file.path, listOf( "com.example.MyContract", "com.example.AnotherContract"), true, generateManifest = false, jarFileName = "another-sample.jar")
            contractJar.read { storage.importAttachment(it, "uploaderA", "sample.jar") }
            anotherContractJar.read { storage.importAttachment(it, "uploaderA", "another-sample.jar") }

            val attachments = storage.queryAttachments(AttachmentsQueryCriteria(contractClassNamesCondition = Builder.equal(listOf("com.example.MyContract"))))
            assertEquals(2, attachments.size)
            attachments.forEach {
                assertTrue("com.example.MyContract" in (storage.openAttachment(it) as ContractAttachment).allContracts)
            }
        }
    }

    @Test
    fun `can import duplicated contract class and version from unsiged attachment if a signed attachment already exists`() {
        SelfCleaningDir().use { file ->
            val (contractJar, _) = makeTestSignedContractJar(file.path, "com.example.MyContract")
            val anotherContractJar = makeTestContractJar(file.path, listOf( "com.example.MyContract", "com.example.AnotherContract"), generateManifest = false, jarFileName = "another-sample.jar")
            contractJar.read { storage.importAttachment(it, "uploaderA", "sample.jar") }
            anotherContractJar.read { storage.importAttachment(it, "uploaderB", "another-sample.jar") }

            val attachments = storage.queryAttachments(AttachmentsQueryCriteria(contractClassNamesCondition = Builder.equal(listOf("com.example.MyContract"))))
            assertEquals(2, attachments.size)
            attachments.forEach {
                val att = storage.openAttachment(it)
                assertTrue(att is ContractAttachment)
                assertTrue("com.example.MyContract" in (att as ContractAttachment).allContracts)
            }
        }
    }

    @Test
    fun `can import duplicated contract class and version from siged attachment if an unsigned attachment already exists`() {
        SelfCleaningDir().use { file ->
            val contractJar = makeTestContractJar(file.path, "com.example.MyContract")
            val anotherContractJar = makeTestContractJar(file.path, listOf( "com.example.MyContract", "com.example.AnotherContract"), true, generateManifest = false, jarFileName = "another-sample.jar")
            contractJar.read { storage.importAttachment(it, "uploaderA", "sample.jar") }
            anotherContractJar.read { storage.importAttachment(it, "uploaderB", "another-sample.jar") }

            val attachments = storage.queryAttachments(AttachmentsQueryCriteria(contractClassNamesCondition = Builder.equal(listOf("com.example.MyContract"))))
            assertEquals(2, attachments.size)
            attachments.forEach {
                val att = storage.openAttachment(it)
                assertTrue(att is ContractAttachment)
                assertTrue("com.example.MyContract" in (att as ContractAttachment).allContracts)
            }
        }
    }

    @Test
    fun `can import duplicated contract class and version for unsigned attachments`() {
        SelfCleaningDir().use { file ->
            val contractJar = makeTestContractJar(file.path, "com.example.MyContract")
            val anotherContractJar = makeTestContractJar(file.path, listOf( "com.example.MyContract", "com.example.AnotherContract"), generateManifest = false, jarFileName = "another-sample.jar")
            contractJar.read { storage.importAttachment(it, "uploaderA", "sample.jar") }
            anotherContractJar.read { storage.importAttachment(it, "uploaderB", "another-sample.jar") }

            val attachments = storage.queryAttachments(AttachmentsQueryCriteria(contractClassNamesCondition = Builder.equal(listOf("com.example.MyContract"))))
            assertEquals(2, attachments.size)
            attachments.forEach {
                val att = storage.openAttachment(it)
                assertTrue(att is ContractAttachment)
                assertTrue("com.example.MyContract" in (att as ContractAttachment).allContracts)
            }
        }
    }

    @Test
    fun `sorting and compound conditions work`() {
        val (jarA, hashA) = makeTestJar(listOf(Pair("a", "a")))
        val (jarB, hashB) = makeTestJar(listOf(Pair("b", "b")))
        val (jarC, hashC) = makeTestJar(listOf(Pair("c", "c")))

        fun uploaderCondition(s: String) = AttachmentsQueryCriteria(uploaderCondition = Builder.equal(s))
        fun filenamerCondition(s: String) = AttachmentsQueryCriteria(filenameCondition = Builder.equal(s))

        fun filenameSort(direction: Sort.Direction) = AttachmentSort(listOf(AttachmentSort.AttachmentSortColumn(AttachmentSort.AttachmentSortAttribute.FILENAME, direction)))

        jarA.read { storage.importAttachment(it, "complexA", "archiveA.zip") }
        jarB.read { storage.importAttachment(it, "complexB", "archiveB.zip") }
        jarC.read { storage.importAttachment(it, "complexC", "archiveC.zip") }

        // DOCSTART AttachmentQueryExample1

        assertEquals(
                emptyList(),
                storage.queryAttachments(
                        AttachmentsQueryCriteria(uploaderCondition = Builder.equal("complexA"))
                                .and(AttachmentsQueryCriteria(uploaderCondition = Builder.equal("complexB"))))
        )

        assertEquals(
                listOf(hashA, hashB),
                storage.queryAttachments(
                        AttachmentsQueryCriteria(uploaderCondition = Builder.equal("complexA"))
                                .or(AttachmentsQueryCriteria(uploaderCondition = Builder.equal("complexB"))))
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
            val corruptAttachment = NodeAttachmentService.DBAttachment(attId = id.toString(), content = bytes, version = DEFAULT_CORDAPP_VERSION)
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
            assertThatIllegalArgumentException().isThrownBy {
                storage.importAttachment(it, "test", null)
            }.withMessageContaining("either empty or not a JAR")
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

    @Test
    fun `retrieve latest versions of unsigned and signed contracts - both exist at same version`() {
        SelfCleaningDir().use { file ->
            val contractJar = makeTestContractJar(file.path, "com.example.MyContract")
            val (signedContractJar, publicKey) = makeTestSignedContractJar(file.path, "com.example.MyContract")
            val contractJarV2 = makeTestContractJar(file.path,"com.example.MyContract", version = 2)
            val (signedContractJarV2, _) = makeTestSignedContractJar(file.path,"com.example.MyContract", version = 2)

            contractJar.read { storage.privilegedImportAttachment(it, "app", "contract.jar") }
            signedContractJar.read { storage.privilegedImportAttachment(it, "app", "contract-signed.jar") }
            var attachmentIdV2Unsigned: AttachmentId? = null
            contractJarV2.read { attachmentIdV2Unsigned = storage.privilegedImportAttachment(it, "app", "contract-V2.jar") }
            var attachmentIdV2Signed: AttachmentId? = null
            signedContractJarV2.read { attachmentIdV2Signed = storage.privilegedImportAttachment(it, "app", "contract-signed-V2.jar") }

            val latestAttachments = storage.getLatestContractAttachments("com.example.MyContract")
            assertEquals(2, latestAttachments.size)
            assertEquals(attachmentIdV2Signed, latestAttachments[0])
            assertEquals(attachmentIdV2Unsigned, latestAttachments[1])
        }
    }

    @Test
    fun `retrieve latest versions of unsigned and signed contracts - signed is later version than unsigned`() {
        SelfCleaningDir().use { file ->
            val contractJar = makeTestContractJar(file.path, "com.example.MyContract")
            val (signedContractJar, publicKey) = makeTestSignedContractJar(file.path, "com.example.MyContract")
            val contractJarV2 = makeTestContractJar(file.path,"com.example.MyContract", version = 2)

            contractJar.read { storage.privilegedImportAttachment(it, "app", "contract.jar") }
            var attachmentIdV1Signed: AttachmentId? = null
            signedContractJar.read { attachmentIdV1Signed = storage.privilegedImportAttachment(it, "app", "contract-signed.jar") }
            var attachmentIdV2Unsigned: AttachmentId? = null
            contractJarV2.read { attachmentIdV2Unsigned = storage.privilegedImportAttachment(it, "app", "contract-V2.jar") }

            val latestAttachments = storage.getLatestContractAttachments("com.example.MyContract")
            assertEquals(2, latestAttachments.size)
            assertEquals(attachmentIdV1Signed, latestAttachments[0])
            assertEquals(attachmentIdV2Unsigned, latestAttachments[1])
        }
    }

    @Test
    fun `retrieve latest versions of unsigned and signed contracts - unsigned is later version than signed`() {
        SelfCleaningDir().use { file ->
            val contractJar = makeTestContractJar(file.path, "com.example.MyContract")
            val (signedContractJar, publicKey) = makeTestSignedContractJar(file.path, "com.example.MyContract")
            val contractJarV2 = makeTestContractJar(file.path,"com.example.MyContract", version = 2)

            contractJar.read { storage.privilegedImportAttachment(it, "app", "contract.jar") }
            var attachmentIdV1Signed: AttachmentId? = null
            signedContractJar.read { attachmentIdV1Signed = storage.privilegedImportAttachment(it, "app", "contract-signed.jar") }
            var attachmentIdV2Unsigned: AttachmentId? = null
            contractJarV2.read { attachmentIdV2Unsigned = storage.privilegedImportAttachment(it, "app", "contract-V2.jar") }

            val latestAttachments = storage.getLatestContractAttachments("com.example.MyContract")
            assertEquals(2, latestAttachments.size)
            assertEquals(attachmentIdV1Signed, latestAttachments[0])
            assertEquals(attachmentIdV2Unsigned, latestAttachments[1])
        }
    }

    @Test
    fun `retrieve latest versions of unsigned and signed contracts - only signed contracts exist in store`() {
        SelfCleaningDir().use { file ->
            val (signedContractJar, publicKey) = makeTestSignedContractJar(file.path, "com.example.MyContract")
            val (signedContractJarV2, _) = makeTestSignedContractJar(file.path,"com.example.MyContract", version = 2)

            signedContractJar.read { storage.privilegedImportAttachment(it, "app", "contract-signed.jar") }
            var attachmentIdV2Signed: AttachmentId? = null
            signedContractJarV2.read { attachmentIdV2Signed = storage.privilegedImportAttachment(it, "app", "contract-signed-V2.jar") }

            val latestAttachments = storage.getLatestContractAttachments("com.example.MyContract")
            assertEquals(1, latestAttachments.size)
            assertEquals(attachmentIdV2Signed, latestAttachments[0])
        }
    }

    @Test
    fun `retrieve latest versions of unsigned and signed contracts - only unsigned contracts exist in store`() {
        SelfCleaningDir().use { file ->
            val contractJar = makeTestContractJar(file.path, "com.example.MyContract")
            val contractJarV2 = makeTestContractJar(file.path,"com.example.MyContract", version = 2)

            contractJar.read { storage.privilegedImportAttachment(it, "app", "contract.jar") }
            var attachmentIdV2Unsigned: AttachmentId? = null
            contractJarV2.read { attachmentIdV2Unsigned = storage.privilegedImportAttachment(it, "app", "contract-V2.jar") }

            val latestAttachments = storage.getLatestContractAttachments("com.example.MyContract")
            assertEquals(1, latestAttachments.size)
            assertEquals(attachmentIdV2Unsigned, latestAttachments[0])
        }
    }

    @Test
    fun `retrieve latest versions of unsigned and signed contracts - none exist in store`() {
        SelfCleaningDir().use { _ ->
            val latestAttachments = storage.getLatestContractAttachments("com.example.MyContract")
            assertEquals(0, latestAttachments.size)
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
}

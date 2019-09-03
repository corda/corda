package net.corda.node.services.attachments

import com.codahale.metrics.MetricRegistry
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.*
import net.corda.core.node.ServicesForResolution
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.internal.ContractJarTestUtils
import net.corda.testing.core.internal.JarSignatureTestUtils.generateKey
import net.corda.testing.core.internal.JarSignatureTestUtils.signJar
import net.corda.testing.core.internal.SelfCleaningDir
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.internal.configureDatabase
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.FileSystem
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AttachmentTrustCalculatorTest {

    // Use an in memory file system for testing attachment storage.
    private lateinit var fs: FileSystem
    private lateinit var database: CordaPersistence
    private lateinit var storage: NodeAttachmentService
    private lateinit var attachmentTrustCalculator: AttachmentTrustCalculator
    private val services = rigorousMock<ServicesForResolution>().also {
        doReturn(testNetworkParameters()).whenever(it).networkParameters
    }

    @Before
    fun setUp() {
        val dataSourceProperties = MockServices.makeTestDataSourceProperties()
        database = configureDatabase(dataSourceProperties, DatabaseConfig(), { null }, { null })
        fs = Jimfs.newFileSystem(Configuration.unix())

        storage = NodeAttachmentService(MetricRegistry(), TestingNamedCacheFactory(), database).also {
            database.transaction {
                it.start()
            }
        }
        storage.servicesForResolution = services
        attachmentTrustCalculator = NodeAttachmentTrustCalculator(storage)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `Jar uploaded by trusted uploader is trusted`() {
        SelfCleaningDir().use { file ->
            val (jar, _) = ContractJarTestUtils.makeTestSignedContractJar(
                file.path,
                "foo.bar.DummyContract"
            )
            val unsignedJar = ContractJarTestUtils.makeTestContractJar(file.path, "com.example.MyContract")
            val (attachment, _) = makeTestJar()

            val signedId = jar.read { storage.privilegedImportAttachment(it, "app", "signed-contract.jar")}
            val unsignedId = unsignedJar.read { storage.privilegedImportAttachment(it, "app", "unsigned-contract.jar") }
            val attachmentId = attachment.read { storage.privilegedImportAttachment(it, "app", "attachment.jar")}

            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(signedId)!!), "Signed contract $signedId should be trusted but isn't")
            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(unsignedId)!!), "Unsigned contract $unsignedId should be trusted but isn't")
            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(attachmentId)!!), "Attachment $attachmentId should be trusted but isn't")
        }
    }

    @Test
    fun `jar trusted if signed by same key and has same contract as existing jar`() {
        SelfCleaningDir().use { file ->
            val alias = "testAlias"
            val password = "testPassword"
            val jarV1 = ContractJarTestUtils.makeTestContractJar(file.path, "foo.bar.DummyContract")
            file.path.generateKey(alias, password)
            val key1 = file.path.signJar(jarV1.toAbsolutePath().toString(), alias, password)
            val jarV2 = ContractJarTestUtils.makeTestContractJar(
                file.path,
                "foo.bar.DummyContract",
                version = 2
            )
            val key2 = file.path.signJar(jarV2.toAbsolutePath().toString(), alias, password)

            val v1Id = jarV1.read { storage.privilegedImportAttachment(it, "app", "dummy-contract.jar") }
            val v2Id = jarV2.read { storage.privilegedImportAttachment(it, "untrusted", "dummy-contract.jar") }

            // Sanity check.
            assertEquals(key1, key2, "Different public keys used to sign jars")
            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(v1Id)!!), "Initial contract $v1Id should be trusted")
            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(v2Id)!!), "Upgraded contract $v2Id should be trusted")
        }
    }

    @Test
    fun `jar trusted if same key but different contract`() {
        SelfCleaningDir().use { file ->
            val alias = "testAlias"
            val password = "testPassword"
            val jarV1 = ContractJarTestUtils.makeTestContractJar(file.path, "foo.bar.DummyContract")
            file.path.generateKey(alias, password)
            val key1 = file.path.signJar(jarV1.toAbsolutePath().toString(), alias, password)
            val jarV2 = ContractJarTestUtils.makeTestContractJar(
                file.path,
                "foo.bar.DifferentContract",
                version = 2
            )
            val key2 = file.path.signJar(jarV2.toAbsolutePath().toString(), alias, password)

            val v1Id = jarV1.read { storage.privilegedImportAttachment(it, "app", "dummy-contract.jar") }
            val v2Id = jarV2.read { storage.privilegedImportAttachment(it, "untrusted", "dummy-contract.jar") }

            // Sanity check.
            assertEquals(key1, key2, "Different public keys used to sign jars")
            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(v1Id)!!), "Initial contract $v1Id should be trusted")
            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(v2Id)!!), "Upgraded contract $v2Id should be trusted")
        }
    }

    @Test
    fun `jar trusted if the signing keys are a subset of an existing trusted jar's signers`() {
        SelfCleaningDir().use { file ->
            val alias = "testAlias"
            val password = "testPassword"
            val alias2 = "anotherTestAlias"
            file.path.generateKey(alias, password)
            file.path.generateKey(alias2, password)

            val jarV1 = ContractJarTestUtils.makeTestContractJar(file.path, "foo.bar.DummyContract")
            file.path.signJar(jarV1.toAbsolutePath().toString(), alias, password)
            file.path.signJar(jarV1.toAbsolutePath().toString(), alias2, password)

            val jarV2 = ContractJarTestUtils.makeTestContractJar(
                file.path,
                "foo.bar.DifferentContract",
                version = 2
            )
            file.path.signJar(jarV2.toAbsolutePath().toString(), alias, password)

            val v1Id = jarV1.read { storage.privilegedImportAttachment(it, "app", "dummy-contract.jar") }
            val v2Id = jarV2.read { storage.privilegedImportAttachment(it, "untrusted", "dummy-contract.jar") }

            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(v1Id)!!), "Initial contract $v1Id should be trusted")
            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(v2Id)!!), "Upgraded contract $v2Id should be trusted")
        }
    }

    @Test
    fun `jar trusted if the signing keys are an intersection of an existing trusted jar's signers`() {
        SelfCleaningDir().use { file ->
            val alias = "testAlias"
            val password = "testPassword"
            val alias2 = "anotherTestAlias"
            val alias3 = "yetAnotherTestAlias"
            file.path.generateKey(alias, password)
            file.path.generateKey(alias2, password)
            file.path.generateKey(alias3, password)

            val jarV1 = ContractJarTestUtils.makeTestContractJar(file.path, "foo.bar.DummyContract")
            file.path.signJar(jarV1.toAbsolutePath().toString(), alias, password)
            file.path.signJar(jarV1.toAbsolutePath().toString(), alias2, password)

            val jarV2 = ContractJarTestUtils.makeTestContractJar(
                file.path,
                "foo.bar.DifferentContract",
                version = 2
            )
            file.path.signJar(jarV2.toAbsolutePath().toString(), alias, password)
            file.path.signJar(jarV2.toAbsolutePath().toString(), alias3, password)

            val v1Id = jarV1.read { storage.privilegedImportAttachment(it, "app", "dummy-contract.jar") }
            val v2Id = jarV2.read { storage.privilegedImportAttachment(it, "untrusted", "dummy-contract.jar") }

            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(v1Id)!!), "Initial contract $v1Id should be trusted")
            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(v2Id)!!), "Upgraded contract $v2Id should be trusted")
        }
    }

    @Test
    fun `jar trusted if the signing keys are a superset of an existing trusted jar's signers`() {
        SelfCleaningDir().use { file ->
            val alias = "testAlias"
            val password = "testPassword"
            val alias2 = "anotherTestAlias"
            file.path.generateKey(alias, password)
            file.path.generateKey(alias2, password)

            val jarV1 = ContractJarTestUtils.makeTestContractJar(file.path, "foo.bar.DummyContract")
            file.path.signJar(jarV1.toAbsolutePath().toString(), alias, password)

            val jarV2 = ContractJarTestUtils.makeTestContractJar(
                file.path,
                "foo.bar.DifferentContract",
                version = 2
            )
            file.path.signJar(jarV2.toAbsolutePath().toString(), alias, password)
            file.path.signJar(jarV2.toAbsolutePath().toString(), alias2, password)

            val v1Id = jarV1.read { storage.privilegedImportAttachment(it, "app", "dummy-contract.jar") }
            val v2Id = jarV2.read { storage.privilegedImportAttachment(it, "untrusted", "dummy-contract.jar") }

            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(v1Id)!!), "Initial contract $v1Id should be trusted")
            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(v2Id)!!), "Upgraded contract $v2Id should be trusted")
        }
    }

    @Test
    fun `jar with inherited trust does not grant trust to other jars (no chain of trust)`() {
        SelfCleaningDir().use { file ->
            val aliasA = "Daredevil"
            val aliasB = "The Punisher"
            val aliasC = "Jessica Jones"
            val password = "i am a netflix series"
            file.path.generateKey(aliasA, password)
            file.path.generateKey(aliasB, password)
            file.path.generateKey(aliasC, password)

            val jarSignedByA =
                ContractJarTestUtils.makeTestContractJar(file.path, "foo.bar.DummyContract")
            file.path.signJar(jarSignedByA.toAbsolutePath().toString(), aliasA, password)

            val jarSignedByAB = ContractJarTestUtils.makeTestContractJar(
                file.path,
                "foo.bar.DifferentContract",
                version = 2
            )
            file.path.signJar(jarSignedByAB.toAbsolutePath().toString(), aliasB, password)
            file.path.signJar(jarSignedByAB.toAbsolutePath().toString(), aliasA, password)

            val jarSignedByBC = ContractJarTestUtils.makeTestContractJar(
                file.path,
                "foo.bar.AnotherContract",
                version = 2
            )
            file.path.signJar(jarSignedByBC.toAbsolutePath().toString(), aliasB, password)
            file.path.signJar(jarSignedByBC.toAbsolutePath().toString(), aliasC, password)

            val attachmentA = jarSignedByA.read { storage.privilegedImportAttachment(it, "app", "dummy-contract.jar") }
            val attachmentB = jarSignedByAB.read { storage.privilegedImportAttachment(it, "untrusted", "dummy-contract.jar") }
            val attachmentC = jarSignedByBC.read { storage.privilegedImportAttachment(it, "untrusted", "dummy-contract.jar") }

            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(attachmentA)!!), "Contract $attachmentA should be trusted")
            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(attachmentB)!!), "Contract $attachmentB should inherit trust")
            assertFalse(attachmentTrustCalculator.calculate(storage.openAttachment(attachmentC)!!), "Contract $attachmentC should not be trusted (no chain of trust)")
        }
    }

    @Test
    fun `jar not trusted if different key but same contract`() {
        SelfCleaningDir().use { file ->
            val alias = "testAlias"
            val password = "testPassword"
            val jarV1 = ContractJarTestUtils.makeTestContractJar(file.path, "foo.bar.DummyContract")
            file.path.generateKey(alias, password)
            val key1 = file.path.signJar(jarV1.toAbsolutePath().toString(), alias, password)
            (file.path / "_shredder").delete()
            (file.path / "_teststore").delete()
            file.path.generateKey(alias, password)
            val jarV2 = ContractJarTestUtils.makeTestContractJar(
                file.path,
                "foo.bar.DummyContract",
                version = 2
            )
            val key2 = file.path.signJar(jarV2.toAbsolutePath().toString(), alias, password)

            val v1Id = jarV1.read { storage.privilegedImportAttachment(it, "app", "dummy-contract.jar") }
            val v2Id = jarV2.read { storage.privilegedImportAttachment(it, "untrusted", "dummy-contract.jar") }

            // Sanity check.
            assertNotEquals(key1, key2, "Same public keys used to sign jars")
            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(v1Id)!!), "Initial contract $v1Id should be trusted")
            assertFalse(attachmentTrustCalculator.calculate(storage.openAttachment(v2Id)!!), "Upgraded contract $v2Id should not be trusted")
        }
    }

    @Test
    fun `neither jar trusted if same contract and signer but not uploaded by a trusted uploader`() {
        SelfCleaningDir().use { file ->
            val alias = "testAlias"
            val password = "testPassword"
            val jarV1 = ContractJarTestUtils.makeTestContractJar(file.path, "foo.bar.DummyContract")
            file.path.generateKey(alias, password)
            val key1 = file.path.signJar(jarV1.toAbsolutePath().toString(), alias, password)
            val jarV2 = ContractJarTestUtils.makeTestContractJar(
                file.path,
                "foo.bar.DummyContract",
                version = 2
            )
            val key2 = file.path.signJar(jarV2.toAbsolutePath().toString(), alias, password)

            val v1Id = jarV1.read { storage.privilegedImportAttachment(it, "untrusted", "dummy-contract.jar") }
            val v2Id = jarV2.read { storage.privilegedImportAttachment(it, "untrusted", "dummy-contract.jar") }

            // Sanity check.
            assertEquals(key1, key2, "Different public keys used to sign jars")
            assertFalse(attachmentTrustCalculator.calculate(storage.openAttachment(v1Id)!!), "Initial contract $v1Id should not be trusted")
            assertFalse(attachmentTrustCalculator.calculate(storage.openAttachment(v2Id)!!), "Upgraded contract $v2Id should not be trusted")
        }
    }

    @Test
    fun `non contract jar trusted if trusted jar with same key present`() {
        SelfCleaningDir().use { file ->
            val alias = "testAlias"
            val password = "testPassword"

            // Directly use the ContractJarTestUtils version of makeTestJar to ensure jars are created in the right place, in order to sign
            // them.
            var counter = 0
            val jarV1 = file.path / "$counter.jar"
            ContractJarTestUtils.makeTestJar(jarV1.outputStream())
            counter++
            val jarV2 = file.path / "$counter.jar"
            // Ensure that the first and second jars do not have the same hash
            ContractJarTestUtils.makeTestJar(jarV2.outputStream(), entries = listOf(Pair("foo", "bar")))

            file.path.generateKey(alias, password)
            val key1 = file.path.signJar(jarV1.toAbsolutePath().toString(), alias, password)
            val key2 = file.path.signJar(jarV2.toAbsolutePath().toString(), alias, password)

            val v1Id = jarV1.read { storage.privilegedImportAttachment(it, "app", "dummy-attachment.jar") }
            val v2Id = jarV2.read { storage.privilegedImportAttachment(it, "untrusted", "dummy-attachment-2.jar") }

            // Sanity check.
            assertEquals(key1, key2, "Different public keys used to sign jars")
            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(v1Id)!!), "Initial attachment $v1Id should be trusted")
            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(v2Id)!!), "Other attachment $v2Id should be trusted")
        }
    }

    @Test
    fun `all non contract jars not trusted if all are uploaded by non trusted uploaders`() {
        SelfCleaningDir().use { file ->
            val alias = "testAlias"
            val password = "testPassword"

            // Directly use the ContractJarTestUtils version of makeTestJar to ensure jars are created in the right place, in order to sign
            // them.
            var counter = 0
            val jarV1 = file.path / "$counter.jar"
            ContractJarTestUtils.makeTestJar(jarV1.outputStream())
            counter++
            val jarV2 = file.path / "$counter.jar"
            // Ensure that the first and second jars do not have the same hash
            ContractJarTestUtils.makeTestJar(jarV2.outputStream(), entries = listOf(Pair("foo", "bar")))

            file.path.generateKey(alias, password)
            val key1 = file.path.signJar(jarV1.toAbsolutePath().toString(), alias, password)
            val key2 = file.path.signJar(jarV2.toAbsolutePath().toString(), alias, password)

            val v1Id = jarV1.read { storage.privilegedImportAttachment(it, "untrusted", "dummy-attachment.jar") }
            val v2Id = jarV2.read { storage.privilegedImportAttachment(it, "untrusted", "dummy-attachment-2.jar") }

            // Sanity check.
            assertEquals(key1, key2, "Different public keys used to sign jars")
            assertFalse(attachmentTrustCalculator.calculate(storage.openAttachment(v1Id)!!), "Initial attachment $v1Id should not be trusted")
            assertFalse(attachmentTrustCalculator.calculate(storage.openAttachment(v2Id)!!), "Other attachment $v2Id should not be trusted")
        }
    }

    @Test
    fun `non contract jars not trusted if unsigned`() {
        SelfCleaningDir().use {
            val (jarV1, _) = makeTestJar()
            val (jarV2, _) = makeTestJar(entries = listOf(Pair("foo", "bar")))

            val v1Id = jarV1.read { storage.privilegedImportAttachment(it, "app", "dummy-attachment.jar") }
            val v2Id = jarV2.read { storage.privilegedImportAttachment(it, "untrusted", "dummy-attachment-2.jar") }

            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(v1Id)!!), "Initial attachment $v1Id should not be trusted")
            assertFalse(attachmentTrustCalculator.calculate(storage.openAttachment(v2Id)!!), "Other attachment $v2Id should not be trusted")
        }
    }

    @Test
    fun `jar not trusted if signed by a blacklisted key and not uploaded by trusted uploader`() {
        SelfCleaningDir().use { file ->

            val aliasA = "Antman"
            val aliasB = "The Wasp"
            val password = "antman and the wasp"
            file.path.generateKey(aliasA, password)
            val keyB = file.path.generateKey(aliasB, password)

            attachmentTrustCalculator = NodeAttachmentTrustCalculator(
                storage,
                listOf(keyB.hash.toString())
            )

            val jarA = ContractJarTestUtils.makeTestContractJar(file.path, "foo.bar.DummyContract")
            file.path.signJar(jarA.toAbsolutePath().toString(), aliasA, password)
            file.path.signJar(jarA.toAbsolutePath().toString(), aliasB, password)
            val jarB =
                ContractJarTestUtils.makeTestContractJar(file.path, "foo.bar.AnotherDummyContract")
            file.path.signJar(jarB.toAbsolutePath().toString(), aliasA, password)
            file.path.signJar(jarB.toAbsolutePath().toString(), aliasB, password)

            val attachmentA = jarA.read { storage.privilegedImportAttachment(it, "app", "dummy-contract.jar") }
            val attachmentB = jarB.read { storage.privilegedImportAttachment(it, "untrusted", "dummy-contract.jar") }

            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(attachmentA)!!), "Contract $attachmentA should be trusted")
            assertFalse(attachmentTrustCalculator.calculate(storage.openAttachment(attachmentB)!!), "Contract $attachmentB should not be trusted")
        }
    }

    @Test
    fun `jar uploaded by trusted uploader is still trusted even if it is signed by a blacklisted key`() {
        SelfCleaningDir().use { file ->

            val aliasA = "Thanos"
            val password = "what did it cost? everything"
            val key = file.path.generateKey(aliasA, password)

            attachmentTrustCalculator = NodeAttachmentTrustCalculator(
                storage,
                listOf(key.hash.toString())
            )

            val jar = ContractJarTestUtils.makeTestContractJar(file.path, "foo.bar.DummyContract")
            file.path.signJar(jar.toAbsolutePath().toString(), aliasA, password)
            val attachment = jar.read { storage.privilegedImportAttachment(it, "app", "dummy-contract.jar") }

            assertTrue(attachmentTrustCalculator.calculate(storage.openAttachment(attachment)!!), "Contract $attachment should be trusted")
        }
    }

    @Test
    fun `resolveAttachmentTrustRoots returns all attachment trust roots`() {
        SelfCleaningDir().use { file ->
            val aliasA = "dan"
            val aliasB = "james"
            val password = "one day the attachment service will be refactored"
            file.path.generateKey(aliasA, password)
            file.path.generateKey(aliasB, password)

            val jarSignedByA =
                ContractJarTestUtils.makeTestContractJar(file.path, "foo.bar.AnotherContract")
            file.path.signJar(jarSignedByA.toAbsolutePath().toString(), aliasA, password)

            val jarSignedByAB =
                ContractJarTestUtils.makeTestContractJar(file.path, "foo.bar.DummyContract")
            file.path.signJar(jarSignedByAB.toAbsolutePath().toString(), aliasA, password)
            file.path.signJar(jarSignedByAB.toAbsolutePath().toString(), aliasB, password)

            val (zipC, _) = makeTestJar(listOf(Pair("file", "content")))
            val (zipD, _) = makeTestJar(listOf(Pair("magic_file", "magic_content_puff")))

            val attachmentA = jarSignedByA.read { storage.privilegedImportAttachment(it, "app", "A.jar") }
            val attachmentB = jarSignedByAB.read { storage.privilegedImportAttachment(it, "untrusted", "B.jar") }
            val attachmentC = zipC.read { storage.privilegedImportAttachment(it, "app", "C.zip") }
            val attachmentD = zipD.read { storage.privilegedImportAttachment(it, "untrusted", null) }

            assertThat(attachmentTrustCalculator.calculateAllTrustRoots()).containsOnly(
                AttachmentTrustInfo(
                    attachmentId = attachmentA,
                    fileName = "A.jar",
                    uploader = "app",
                    trustRootId = attachmentA,
                    trustRootFileName = "A.jar"
                ),
                AttachmentTrustInfo(
                    attachmentId = attachmentB,
                    fileName = "B.jar",
                    uploader = "untrusted",
                    trustRootId = attachmentA,
                    trustRootFileName = "A.jar"
                ),
                AttachmentTrustInfo(
                    attachmentId = attachmentC,
                    fileName = "C.zip",
                    uploader = "app",
                    trustRootId = attachmentC,
                    trustRootFileName = "C.zip"
                ),
                AttachmentTrustInfo(
                    attachmentId = attachmentD,
                    fileName = null,
                    uploader = "untrusted",
                    trustRootId = null,
                    trustRootFileName = null
                )
            )
        }
    }

    @Test
    fun `resolveAttachmentTrustRoots attachments signed by blacklisted keys output without trust root fields filled in`() {
        SelfCleaningDir().use { file ->

            val aliasA = "batman"
            val aliasB = "the joker"
            val password = "nanananana batman"
            file.path.generateKey(aliasA, password)
            val keyB = file.path.generateKey(aliasB, password)

            attachmentTrustCalculator = NodeAttachmentTrustCalculator(
                storage,
                listOf(keyB.hash.toString())
            )

            val jarSignedByA =
                ContractJarTestUtils.makeTestContractJar(file.path, "foo.bar.AnotherContract")
            file.path.signJar(jarSignedByA.toAbsolutePath().toString(), aliasA, password)

            val jarSignedByAB =
                ContractJarTestUtils.makeTestContractJar(file.path, "foo.bar.DummyContract")
            file.path.signJar(jarSignedByAB.toAbsolutePath().toString(), aliasA, password)
            file.path.signJar(jarSignedByAB.toAbsolutePath().toString(), aliasB, password)

            val attachmentA = jarSignedByA.read { storage.privilegedImportAttachment(it, "app", "A.jar") }
            val attachmentB = jarSignedByAB.read { storage.privilegedImportAttachment(it, "untrusted", "B.jar") }

            assertThat(attachmentTrustCalculator.calculateAllTrustRoots()).containsOnly(
                AttachmentTrustInfo(
                    attachmentId = attachmentA,
                    fileName = "A.jar",
                    uploader = "app",
                    trustRootId = attachmentA,
                    trustRootFileName = "A.jar"
                ),
                AttachmentTrustInfo(
                    attachmentId = attachmentB,
                    fileName = "B.jar",
                    uploader = "untrusted",
                    trustRootId = null,
                    trustRootFileName = null
                )
            )
        }
    }

    private var counter = 0
    private fun makeTestJar(
        entries: List<Pair<String, String>> = listOf(
            Pair("test1.txt", "This is some useful content"),
            Pair("test2.txt", "Some more useful content")
        )
    ): Pair<Path, SecureHash> {
        counter++
        val file = fs.getPath("$counter.jar")
        ContractJarTestUtils.makeTestJar(file.outputStream(), entries)
        return Pair(file, file.readAll().sha256())
    }
}
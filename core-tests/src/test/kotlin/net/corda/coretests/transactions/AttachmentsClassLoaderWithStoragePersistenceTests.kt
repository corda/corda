package net.corda.coretests.transactions

import com.codahale.metrics.MetricRegistry
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.AttachmentTrustCalculator
import net.corda.core.internal.hash
import net.corda.core.node.NetworkParameters
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.internal.AttachmentsClassLoader
import net.corda.coretesting.internal.rigorousMock
import net.corda.node.services.attachments.NodeAttachmentTrustCalculator
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.internal.ContractJarTestUtils
import net.corda.testing.core.internal.ContractJarTestUtils.signContractJar
import net.corda.testing.core.internal.JarSignatureTestUtils.generateKey
import net.corda.testing.core.internal.JarSignatureTestUtils.signJar
import net.corda.testing.core.internal.SelfCleaningDir
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.URL
import kotlin.test.assertFailsWith

class AttachmentsClassLoaderWithStoragePersistenceTests {
    companion object {
        val ISOLATED_CONTRACTS_JAR_PATH_V4: URL = AttachmentsClassLoaderWithStoragePersistenceTests::class.java.getResource("isolated-4.0.jar")!!
        private val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val DUMMY_NOTARY get() = dummyNotary.party
        const val PROGRAM_ID = "net.corda.testing.contracts.MyDummyContract"
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private lateinit var database: CordaPersistence
    private lateinit var storage: NodeAttachmentService
    private lateinit var attachmentTrustCalculator2: AttachmentTrustCalculator
    private val networkParameters = testNetworkParameters()
    private val cacheFactory = TestingNamedCacheFactory(1)
    private val cacheFactory2 = TestingNamedCacheFactory()
    private val services = rigorousMock<ServicesForResolution>().also {
        doReturn(testNetworkParameters()).whenever(it).networkParameters
    }

    private fun createClassloader(
            attachment: AttachmentId,
            params: NetworkParameters = networkParameters
    ): AttachmentsClassLoader {
        return createClassloader(listOf(attachment), params)
    }

    private fun createClassloader(
            attachments: List<AttachmentId>,
            params: NetworkParameters = networkParameters
    ): AttachmentsClassLoader {
        return AttachmentsClassLoader(
                attachments.map { storage.openAttachment(it)!! },
                params,
                SecureHash.zeroHash,
                attachmentTrustCalculator2::calculate
        )
    }

    @Before
    fun setUp() {
        val dataSourceProperties = MockServices.makeTestDataSourceProperties()
        database = configureDatabase(dataSourceProperties, DatabaseConfig(), { null }, { null })
        storage = NodeAttachmentService(MetricRegistry(), TestingNamedCacheFactory(), database).also {
            database.transaction {
                it.start()
            }
        }
        storage.servicesForResolution = services
        attachmentTrustCalculator2 = NodeAttachmentTrustCalculator(storage, database, cacheFactory2)
    }

    @Test(timeout=300_000)
	fun `Cannot load an untrusted contract jar if no other attachment exists that was signed with the same keys and uploaded by a trusted uploader`() {
        val signedJar = signContractJar(ISOLATED_CONTRACTS_JAR_PATH_V4, copyFirst = true)
        val isolatedSignedId = storage.importAttachment(signedJar.first.toUri().toURL().openStream(), "untrusted", "isolated-signed.jar" )

        assertFailsWith(TransactionVerificationException.UntrustedAttachmentsException::class) {
            createClassloader(isolatedSignedId).use {}
        }
    }

    @Test(timeout=300_000)
    fun `Cannot load an untrusted contract jar if no other attachment exists that was signed with the same keys`() {
        SelfCleaningDir().use { file ->
            val path = file.path
            val alias1 = "AAAA"
            val alias2 = "BBBB"
            val password = "testPassword"

            path.generateKey(alias1, password)
            path.generateKey(alias2, password)

            val contractName = "net.corda.testing.contracts.MyDummyContract"
            val content = createContractString(contractName)
            val contractJarPath = ContractJarTestUtils.makeTestContractJar(path, contractName, content = content, version = 2)
            path.signJar(contractJarPath.toAbsolutePath().toString(), alias1, password)
            path.signJar(contractJarPath.toAbsolutePath().toString(), alias2, password)
            val untrustedAttachment = storage.importAttachment(contractJarPath.toUri().toURL().openStream(), "untrusted", "contract.jar")

            assertFailsWith(TransactionVerificationException.UntrustedAttachmentsException::class) {
                createClassloader(untrustedAttachment).use {}
            }
        }
    }

    @Test(timeout=300_000)
	fun `Attachments with inherited trust do not grant trust to attachments being loaded (no chain of trust)`() {
        SelfCleaningDir().use { file ->
            val path = file.path
            val alias1 = "AAAA"
            val alias2 = "BBBB"
            val alias3 = "CCCC"
            val password = "testPassword"

            path.generateKey(alias1, password)
            path.generateKey(alias2, password)
            path.generateKey(alias3, password)

            val contractName1 = "net.corda.testing.contracts.MyDummyContract1"
            val contractName2 = "net.corda.testing.contracts.MyDummyContract2"
            val contractName3 = "net.corda.testing.contracts.MyDummyContract3"

            val content = createContractString(contractName1)
            val contractJar = ContractJarTestUtils.makeTestContractJar(path, contractName1, content = content)
            path.signJar(contractJar.toAbsolutePath().toString(), alias1, password)
            storage.privilegedImportAttachment(contractJar.toUri().toURL().openStream(), "app", "contract.jar")

            val content2 = createContractString(contractName2)
            val contractJarPath2 = ContractJarTestUtils.makeTestContractJar(path, contractName2, content = content2, version = 2)
            path.signJar(contractJarPath2.toAbsolutePath().toString(), alias1, password)
            path.signJar(contractJarPath2.toAbsolutePath().toString(), alias2, password)
            val inheritedTrustAttachment = storage.importAttachment(contractJarPath2.toUri().toURL().openStream(), "untrusted", "dummy-contract.jar")

            val content3 = createContractString(contractName3)
            val contractJarPath3 = ContractJarTestUtils.makeTestContractJar(path, contractName3, content = content3, version = 3)
            path.signJar(contractJarPath3.toAbsolutePath().toString(), alias2, password)
            path.signJar(contractJarPath3.toAbsolutePath().toString(), alias3, password)
            val untrustedAttachment = storage.importAttachment(contractJarPath3.toUri().toURL()
                    .openStream(), "untrusted", "contract.jar")

            // pass the inherited trust attachment through the classloader first to ensure it does not affect the next loaded attachment
            createClassloader(inheritedTrustAttachment).use {
                assertFailsWith(TransactionVerificationException.UntrustedAttachmentsException::class) {
                    createClassloader(untrustedAttachment).use {}
                }
            }
        }
    }

    @Test(timeout=300_000)
	fun `Cannot load an untrusted contract jar if it is signed by a blacklisted key even if there is another attachment signed by the same keys that is trusted`() {
        SelfCleaningDir().use { file ->

            val path = file.path
            val aliasA = "AAAA"
            val aliasB = "BBBB"
            val password = "testPassword"

            val publicKeyA = path.generateKey(aliasA, password)
            path.generateKey(aliasB, password)

            attachmentTrustCalculator2 = NodeAttachmentTrustCalculator(
                    storage,
                    cacheFactory,
                    blacklistedAttachmentSigningKeys = listOf(publicKeyA.hash)
            )

            val contractName1 = "net.corda.testing.contracts.MyDummyContract1"
            val contractName2 = "net.corda.testing.contracts.MyDummyContract2"

            val contentTrusted = createContractString(contractName1)
            val classJar = ContractJarTestUtils.makeTestContractJar(path, contractName1, content = contentTrusted)
            path.signJar(classJar.toAbsolutePath().toString(), aliasA, password)
            path.signJar(classJar.toAbsolutePath().toString(), aliasB, password)
            storage.privilegedImportAttachment(classJar.toUri().toURL().openStream(), "app", "contract.jar")

            val contentUntrusted = createContractString(contractName2)
            val untrustedClassJar = ContractJarTestUtils.makeTestContractJar(path, contractName2, content = contentUntrusted)
            path.signJar(untrustedClassJar.toAbsolutePath().toString(), aliasA, password)
            path.signJar(untrustedClassJar.toAbsolutePath().toString(), aliasB, password)
            val untrustedAttachment = storage.importAttachment(untrustedClassJar.toUri().toURL()
                    .openStream(), "untrusted", "untrusted-contract.jar")

            assertFailsWith(TransactionVerificationException.UntrustedAttachmentsException::class) {
                createClassloader(untrustedAttachment).use {}
            }
        }
    }

    private fun createContractString(contractName: String, versionSeed: Int = 0): String {
        val pkgs = contractName.split(".")
        val className = pkgs.last()
        val packages = pkgs.subList(0, pkgs.size - 1)

        val output = """package ${packages.joinToString(".")};
                import net.corda.core.contracts.*;
                import net.corda.core.transactions.*;
                import java.net.URL;
                import java.io.InputStream;

                public class $className implements Contract {
                    private int seed = $versionSeed;
                    @Override
                    public void verify(LedgerTransaction tx) throws IllegalArgumentException {
                       System.gc();
                       InputStream str = this.getClass().getClassLoader().getResourceAsStream("importantDoc.pdf");
                       if (str == null) throw new IllegalStateException("Could not find importantDoc.pdf");
                    }
                }
            """.trimIndent()

        println(output)
        return output
    }
}

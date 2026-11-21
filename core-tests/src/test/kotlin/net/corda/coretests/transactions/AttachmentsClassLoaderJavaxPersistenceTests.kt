package net.corda.coretests.transactions

import jakarta.persistence.AttributeConverter
import net.corda.core.contracts.AlwaysAcceptAttachmentConstraint
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.AttachmentConstraint
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.HashAttachmentConstraint
import net.corda.core.contracts.PrivacySalt
import net.corda.core.contracts.SignatureAttachmentConstraint
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.internal.AbstractAttachment
import net.corda.core.internal.JarSignatureCollector
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.createLedgerTransaction
import net.corda.core.serialization.internal.AttachmentsClassLoader
import net.corda.core.serialization.internal.AttachmentsClassLoaderCacheImpl
import net.corda.core.serialization.internal.JavaxPersistenceRenamer
import net.corda.core.serialization.internal.JavaxPersistenceRenamer.JAKARTA_PERSISTENCE
import net.corda.core.serialization.internal.JavaxPersistenceRenamer.JAVAX_PERSISTENCE
import net.corda.coretests.transactions.javaxfixture.BothApisFixture
import net.corda.coretests.transactions.javaxfixture.CodeFixture
import net.corda.coretests.transactions.javaxfixture.ContractFixture
import net.corda.coretests.transactions.javaxfixture.ConverterFixture
import net.corda.coretests.transactions.javaxfixture.PlainFixture
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.internal.ContractJarTestUtils.signContractJar
import net.corda.testing.internal.TestingNamedCacheFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.InputStream
import java.net.URLClassLoader
import java.nio.file.Path
import java.security.PublicKey
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.reflect.full.memberFunctions
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The classes of an attachment that were built for `javax.persistence` (Corda 4.14 and earlier) are converted to `jakarta.persistence`
 * by [AttachmentsClassLoader] as it defines them.
 */
class AttachmentsClassLoaderJavaxPersistenceTests {
    private companion object {
        const val FIXTURE_PACKAGE = "net/corda/coretests/transactions/javaxfixture/"
        const val ATTACHMENT_PACKAGE = "attachmentonly/javaxfixture/"
        val ALICE = TestIdentity(ALICE_NAME, 70).party
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party

        /** The fixture classes, and whether the attachment has them built for `javax.persistence`. */
        val fixtures = mapOf(
                ConverterFixture::class.java to true,
                CodeFixture::class.java to true,
                ContractFixture::class.java to true,
                PlainFixture::class.java to true,
                BothApisFixture::class.java to false
        )

        fun attachmentName(fixture: Class<*>): String = ATTACHMENT_PACKAGE.replace('/', '.') + fixture.simpleName

        /** The class file of the fixture as the attachment has it: in the attachment's own package, and built for `javax.persistence`. */
        fun attachmentBytes(fixture: Class<*>): ByteArray {
            val original = fixture.getResourceAsStream(fixture.simpleName + ".class")!!.use { it.readBytes() }
            val moved = JavaxPersistenceRenamer.rename(original, FIXTURE_PACKAGE, ATTACHMENT_PACKAGE)
            return if (fixtures.getValue(fixture)) JavaxPersistenceRenamer.rename(moved, JAKARTA_PERSISTENCE, JAVAX_PERSISTENCE) else moved
        }
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val opened = AtomicInteger()

    private fun fixtureJar(sealed: Boolean = false): Path {
        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        if (sealed) manifest.mainAttributes[Attributes.Name.SEALED] = "true"
        val jar = tempFolder.root.toPath().resolve("javax-fixture.jar")
        JarOutputStream(jar.outputStream(), manifest).use { output ->
            for (fixture in fixtures.keys) {
                output.putNextEntry(JarEntry(ATTACHMENT_PACKAGE + fixture.simpleName + ".class"))
                output.write(attachmentBytes(fixture))
                output.closeEntry()
            }
        }
        return jar
    }

    private fun attachment(jar: Path): Attachment {
        return object : AbstractAttachment(jar::readBytes, uploader = "app") {
            @Suppress("OverridingDeprecatedMember")
            @Deprecated("Use signerKeys. There is no requirement that attachment signers are Corda parties.")
            override val signers: List<Party> = emptyList()
            // What the node's attachment storage records: the keys that signed the jar as it is stored.
            override val signerKeys: List<PublicKey> = JarInputStream(jar.inputStream()).use(JarSignatureCollector::collectSigners)
            override val size: Int = 1234
            override val id: SecureHash = SecureHash.sha256(attachmentData)

            override fun open(): InputStream {
                opened.incrementAndGet()
                return super.open()
            }
        }
    }

    private fun classLoader(jar: Path): AttachmentsClassLoader {
        return AttachmentsClassLoader(listOf(attachment(jar)), testNetworkParameters(), SecureHash.zeroHash, { true })
    }

    private fun call(classLoader: ClassLoader, fixture: Class<*>): String {
        val loaded = Class.forName(attachmentName(fixture), true, classLoader)
        return (loaded.getDeclaredConstructor().newInstance() as Callable<*>).call() as String
    }

    @Test(timeout = 300_000)
    fun `the fixture classes are built for javax persistence and fail to load unconverted`() {
        assertTrue(JavaxPersistenceRenamer.isConverted(attachmentName(CodeFixture::class.java), attachmentBytes(CodeFixture::class.java)))
        assertFalse(JavaxPersistenceRenamer.isConverted(attachmentName(PlainFixture::class.java), attachmentBytes(PlainFixture::class.java)))
        assertFalse(JavaxPersistenceRenamer.isConverted(attachmentName(BothApisFixture::class.java), attachmentBytes(BothApisFixture::class.java)))
        // A class of the javax.persistence API that an attachment bundles keeps its name.
        assertFalse(JavaxPersistenceRenamer.isConverted("javax.persistence.Bundled", attachmentBytes(CodeFixture::class.java)))

        // What happens without the conversion: only jakarta.persistence is on the classpath.
        URLClassLoader(arrayOf(fixtureJar().toUri().toURL()), ClassLoader.getSystemClassLoader()).use { unconverted ->
            assertThatThrownBy { call(unconverted, CodeFixture::class.java) }
                    .isInstanceOf(NoClassDefFoundError::class.java)
                    .hasMessageContaining("javax/persistence")
            assertEquals("plain", call(unconverted, PlainFixture::class.java))
        }
    }

    @Test(timeout = 300_000)
    fun `a class that uses javax persistence in its code is converted as it is loaded`() {
        classLoader(fixtureJar()).use { classLoader ->
            assertEquals("CONVERTEDALL", call(classLoader, CodeFixture::class.java))
            val converter = Class.forName(attachmentName(ConverterFixture::class.java), true, classLoader)
            assertThat(converter.interfaces).containsExactly(AttributeConverter::class.java)
            assertThat(converter.classLoader).isSameAs(classLoader)
        }
    }

    @Test(timeout = 300_000)
    fun `Kotlin reflection sees the converted signatures`() {
        classLoader(fixtureJar()).use { classLoader ->
            val cascade = Class.forName(attachmentName(CodeFixture::class.java), true, classLoader).kotlin.memberFunctions.single { it.name == "cascade" }
            assertEquals("jakarta.persistence.CascadeType", cascade.returnType.toString())
        }
    }

    @Test(timeout = 300_000)
    fun `the other classes of the attachment are loaded as before`() {
        classLoader(fixtureJar()).use { classLoader ->
            assertEquals("plain", call(classLoader, PlainFixture::class.java))
            assertEquals("javax/persistence/Entity", call(classLoader, BothApisFixture::class.java))
        }
    }

    @Test(timeout = 300_000)
    fun `a converted class has the code source and the package of the classes that are not converted`() {
        classLoader(fixtureJar()).use { classLoader ->
            // In both orders: the converted class defines the package here, the plain class in the next loader.
            val converted = Class.forName(attachmentName(CodeFixture::class.java), true, classLoader)
            val plain = Class.forName(attachmentName(PlainFixture::class.java), true, classLoader)
            assertEquals(plain.protectionDomain.codeSource, converted.protectionDomain.codeSource)
            assertEquals(plain.protectionDomain.codeSource.location.toString(), converted.protectionDomain.codeSource.location.toString())
            assertThat(converted.`package`).isSameAs(plain.`package`)
            assertThat(converted.protectionDomain.permissions.elements().toList()).isEmpty()
        }
        classLoader(fixtureJar()).use { classLoader ->
            val plain = Class.forName(attachmentName(PlainFixture::class.java), true, classLoader)
            val converted = Class.forName(attachmentName(CodeFixture::class.java), true, classLoader)
            assertEquals(plain.protectionDomain.codeSource, converted.protectionDomain.codeSource)
        }
    }

    @Test(timeout = 300_000)
    fun `a converted class of a signed attachment keeps the signers of its class file`() {
        val (signedJar, _) = signContractJar(fixtureJar().toUri().toURL(), copyFirst = true)
        classLoader(signedJar).use { classLoader ->
            val plain = Class.forName(attachmentName(PlainFixture::class.java), true, classLoader)
            // Defining a class whose signers differ from those of its package fails with a SecurityException.
            val converted = Class.forName(attachmentName(CodeFixture::class.java), true, classLoader)
            val signers = assertNotNull(plain.protectionDomain.codeSource.codeSigners)
            assertThat(signers).isNotEmpty
            assertThat(converted.protectionDomain.codeSource.codeSigners).containsExactly(*signers)
            assertEquals("CONVERTEDALL", call(classLoader, CodeFixture::class.java))
        }
    }

    @Test(timeout = 300_000)
    fun `a converted class is accepted in the sealed package of its attachment`() {
        for (first in listOf(CodeFixture::class.java, PlainFixture::class.java)) {
            classLoader(fixtureJar(sealed = true)).use { classLoader ->
                val loaded = Class.forName(attachmentName(first), true, classLoader)
                assertTrue(loaded.`package`.isSealed)
                assertEquals("CONVERTEDALL", call(classLoader, CodeFixture::class.java))
                assertEquals("plain", call(classLoader, PlainFixture::class.java))
            }
        }
    }

    @Test(timeout = 300_000)
    fun `converting classes does not read the attachment again`() {
        classLoader(fixtureJar()).use { classLoader ->
            assertEquals("plain", call(classLoader, PlainFixture::class.java))
            val openedForTheClassLoader = opened.get()
            assertEquals("CONVERTEDALL", call(classLoader, CodeFixture::class.java))
            assertEquals("javax/persistence/Entity", call(classLoader, BothApisFixture::class.java))
            assertEquals(openedForTheClassLoader, opened.get())
        }
    }

    @Test(timeout = 300_000)
    fun `a transaction whose contract uses javax persistence in its code verifies`() {
        verify(attachment(fixtureJar()), AlwaysAcceptAttachmentConstraint)
    }

    @Test(timeout = 300_000)
    fun `the hash and the signers of an attachment with converted classes are what its constraints expect`() {
        val (signedJar, signer) = signContractJar(fixtureJar().toUri().toURL(), copyFirst = true)
        val storedBytes = signedJar.readBytes()
        val attachment = attachment(signedJar)
        assertEquals(SecureHash.sha256(storedBytes), attachment.id)
        assertThat(attachment.signerKeys).containsExactly(signer)

        // The conversion is made on the bytes of a class as it is defined, never on the attachment, so the constraints of the states,
        // which are checked against the hash and the signers of the attachment, hold as they did.
        verify(attachment, HashAttachmentConstraint(attachment.id))
        verify(attachment, SignatureAttachmentConstraint(signer))

        assertThat(signedJar.readBytes()).isEqualTo(storedBytes)
    }

    private fun verify(attachment: Attachment, constraint: AttachmentConstraint) {
        val contractName = attachmentName(ContractFixture::class.java)
        val output = TransactionState(DummyContract.SingleOwnerState(0, ALICE), contractName, DUMMY_NOTARY, constraint = constraint)
        val cacheFactory = TestingNamedCacheFactory(1)
        val transaction = createLedgerTransaction(
                emptyList<StateAndRef<*>>(),
                listOf(output),
                emptyList<CommandWithParties<CommandData>>(),
                listOf(ContractAttachment.create(attachment, contractName, uploader = "app", signerKeys = attachment.signerKeys)),
                SecureHash.randomSHA256(),
                null,
                null,
                PrivacySalt(),
                testNetworkParameters(minimumPlatformVersion = PLATFORM_VERSION),
                emptyList(),
                emptyList(),
                isAttachmentTrusted = { true },
                attachmentsClassLoaderCache = AttachmentsClassLoaderCacheImpl(cacheFactory)
        )
        transaction.verify()
    }
}

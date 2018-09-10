package net.corda.serialization.internal

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.Contract
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.declaredField
import net.corda.core.internal.toWireTransaction
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.serialization.*
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.OpaqueBytes
import net.corda.node.internal.cordapp.JarScanningCordappLoader
import net.corda.node.internal.cordapp.CordappProviderImpl
import net.corda.nodeapi.DummyContractBackdoor
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.MockCordappConfigProvider
import net.corda.testing.internal.kryoSpecific
import net.corda.testing.internal.rigorousMock
import net.corda.testing.services.MockAttachmentStorage
import org.apache.commons.io.IOUtils
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.test.assertFailsWith

class AttachmentsClassLoaderTests {
    companion object {
        val ISOLATED_CONTRACTS_JAR_PATH: URL = AttachmentsClassLoaderTests::class.java.getResource("isolated.jar")
        private const val ISOLATED_CONTRACT_CLASS_NAME = "net.corda.finance.contracts.isolated.AnotherDummyContract"
        private val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        private val MEGA_CORP = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).party
        private fun SerializationContext.withAttachmentStorage(attachmentStorage: AttachmentStorage): SerializationContext {
            val serviceHub = rigorousMock<ServiceHub>()
            doReturn(attachmentStorage).whenever(serviceHub).attachments
            return this.withServiceHub(serviceHub)
        }

        private fun SerializationContext.withServiceHub(serviceHub: ServiceHub): SerializationContext {
            return this.withTokenContext(SerializeAsTokenContextImpl(serviceHub) {}).withProperty(attachmentsClassLoaderEnabledPropertyName, true)
        }
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    private val attachments = MockAttachmentStorage()
    private val networkParameters = testNetworkParameters()
    private val cordappProvider = CordappProviderImpl(JarScanningCordappLoader.fromJarUrls(listOf(ISOLATED_CONTRACTS_JAR_PATH), 1000), MockCordappConfigProvider(), attachments).apply {
        start(networkParameters.whitelistedContractImplementations)
    }
    private val cordapp get() = cordappProvider.cordapps.first()
    private val attachmentId get() = cordappProvider.getCordappAttachmentId(cordapp)!!
    private val appContext get() = cordappProvider.getAppContext(cordapp)
    private val serviceHub = rigorousMock<ServiceHub>().also {
        doReturn(attachments).whenever(it).attachments
        doReturn(cordappProvider).whenever(it).cordappProvider
        doReturn(networkParameters).whenever(it).networkParameters
    }

    // These ClassLoaders work together to load 'AnotherDummyContract' in a disposable way, such that even though
    // the class may be on the unit test class path (due to default IDE settings, etc), it won't be loaded into the
    // regular app classloader but rather than ClassLoaderForTests. This helps keep our environment clean and
    // ensures we have precise control over where it's loaded.
    object FilteringClassLoader : ClassLoader() {
        @Throws(ClassNotFoundException::class)
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if ("AnotherDummyContract" in name) {
                throw ClassNotFoundException(name)
            }
            return super.loadClass(name, resolve)
        }
    }

    class ClassLoaderForTests : URLClassLoader(arrayOf(ISOLATED_CONTRACTS_JAR_PATH), FilteringClassLoader)
    @Test
    fun `dynamically load AnotherDummyContract from isolated contracts jar`() {
        ClassLoaderForTests().use { child ->
            val contractClass = Class.forName(ISOLATED_CONTRACT_CLASS_NAME, true, child)
            val contract = contractClass.newInstance() as Contract

            assertEquals("helloworld", contract.declaredField<Any?>("magicString").value)
        }
    }

    private fun fakeAttachment(filepath: String, content: String): ByteArray {
        val bs = ByteArrayOutputStream()
        JarOutputStream(bs).use { js ->
            js.putNextEntry(ZipEntry(filepath))
            js.writer().apply { append(content); flush() }
            js.closeEntry()
        }
        return bs.toByteArray()
    }

    private fun readAttachment(attachment: Attachment, filepath: String): ByteArray {
        ByteArrayOutputStream().use {
            attachment.extractFile(filepath, it)
            return it.toByteArray()
        }
    }

    @Test
    fun `test MockAttachmentStorage open as jar`() {
        val storage = attachments
        val key = attachmentId
        val attachment = storage.openAttachment(key)!!

        val jar = attachment.openAsJAR()

        assertNotNull(jar.nextEntry)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `test overlapping file exception`() {
        val storage = attachments
        val att0 = attachmentId
        val att1 = storage.importAttachment(fakeAttachment("file.txt", "some data").inputStream())
        val att2 = storage.importAttachment(fakeAttachment("file.txt", "some other data").inputStream())

        assertFailsWith(AttachmentsClassLoader.OverlappingAttachments::class) {
            AttachmentsClassLoader(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! })
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun basic() {
        val storage = attachments
        val att0 = attachmentId
        val att1 = storage.importAttachment(fakeAttachment("file1.txt", "some data").inputStream())
        val att2 = storage.importAttachment(fakeAttachment("file2.txt", "some other data").inputStream())

        val cl = AttachmentsClassLoader(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! })
        val txt = IOUtils.toString(cl.getResourceAsStream("file1.txt"), Charsets.UTF_8.name())
        assertEquals("some data", txt)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `Check platform independent path handling in attachment jars`() {
        val storage = MockAttachmentStorage()

        val att1 = storage.importAttachment(fakeAttachment("/folder1/foldera/file1.txt", "some data").inputStream())
        val att2 = storage.importAttachment(fakeAttachment("\\folder1\\folderb\\file2.txt", "some other data").inputStream())

        val data1a = readAttachment(storage.openAttachment(att1)!!, "/folder1/foldera/file1.txt")
        assertArrayEquals("some data".toByteArray(), data1a)

        val data1b = readAttachment(storage.openAttachment(att1)!!, "\\folder1\\foldera\\file1.txt")
        assertArrayEquals("some data".toByteArray(), data1b)

        val data2a = readAttachment(storage.openAttachment(att2)!!, "\\folder1\\folderb\\file2.txt")
        assertArrayEquals("some other data".toByteArray(), data2a)

        val data2b = readAttachment(storage.openAttachment(att2)!!, "/folder1/folderb/file2.txt")
        assertArrayEquals("some other data".toByteArray(), data2b)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `loading class AnotherDummyContract`() {
        val storage = attachments
        val att0 = attachmentId
        val att1 = storage.importAttachment(fakeAttachment("file1.txt", "some data").inputStream())
        val att2 = storage.importAttachment(fakeAttachment("file2.txt", "some other data").inputStream())

        val cl = AttachmentsClassLoader(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! }, FilteringClassLoader)
        val contractClass = Class.forName(ISOLATED_CONTRACT_CLASS_NAME, true, cl)
        val contract = contractClass.newInstance() as Contract
        assertEquals(cl, contract.javaClass.classLoader)
        assertEquals("helloworld", contract.declaredField<Any?>("magicString").value)
    }

    private fun createContract2Cash(): Contract {
        ClassLoaderForTests().use { cl ->
            val contractClass = Class.forName(ISOLATED_CONTRACT_CLASS_NAME, true, cl)
            return contractClass.newInstance() as Contract
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `testing Kryo with ClassLoader (with top level class name)`() {
        val contract = createContract2Cash()

        val bytes = contract.serialize()
        val storage = attachments
        val att0 = attachmentId
        val att1 = storage.importAttachment(fakeAttachment("file1.txt", "some data").inputStream())
        val att2 = storage.importAttachment(fakeAttachment("file2.txt", "some other data").inputStream())

        val cl = AttachmentsClassLoader(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! }, FilteringClassLoader)

        val context = SerializationFactory.defaultFactory.defaultContext.withClassLoader(cl).withWhitelisted(contract.javaClass)
        val state2 = bytes.deserialize(context = context)
        assertTrue(state2.javaClass.classLoader is AttachmentsClassLoader)
        assertNotNull(state2)
    }

    // top level wrapper
    @CordaSerializable
    class Data(val contract: Contract)

    @Test
    @Suppress("DEPRECATION")
    fun `testing Kryo with ClassLoader (without top level class name)`() {
        val data = Data(createContract2Cash())

        assertNotNull(data.contract)

        val context2 = SerializationFactory.defaultFactory.defaultContext.withWhitelisted(data.contract.javaClass)

        val bytes = data.serialize(context = context2)
        val storage = attachments
        val att0 = attachmentId
        val att1 = storage.importAttachment(fakeAttachment("file1.txt", "some data").inputStream())
        val att2 = storage.importAttachment(fakeAttachment("file2.txt", "some other data").inputStream())

        val cl = AttachmentsClassLoader(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! }, FilteringClassLoader)

        val context = SerializationFactory.defaultFactory.defaultContext.withClassLoader(cl).withWhitelisted(Class.forName(ISOLATED_CONTRACT_CLASS_NAME, true, cl))

        val state2 = bytes.deserialize(context = context)
        assertEquals(cl, state2.contract.javaClass.classLoader)
        assertNotNull(state2)

        // We should be able to load same class from a different class loader and have them be distinct.
        val cl2 = AttachmentsClassLoader(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! }, FilteringClassLoader)

        val context3 = SerializationFactory.defaultFactory.defaultContext.withClassLoader(cl2).withWhitelisted(Class.forName(ISOLATED_CONTRACT_CLASS_NAME, true, cl2))

        val state3 = bytes.deserialize(context = context3)
        assertEquals(cl2, state3.contract.javaClass.classLoader)
        assertNotNull(state3)
    }

    @Test
    fun `test serialization of SecureHash`() {
        val secureHash = SecureHash.randomSHA256()
        val bytes = secureHash.serialize()
        val copiedSecuredHash = bytes.deserialize()

        assertEquals(secureHash, copiedSecuredHash)
    }

    @Test
    fun `test serialization of OpaqueBytes`() {
        val opaqueBytes = OpaqueBytes("0123456789".toByteArray())
        val bytes = opaqueBytes.serialize()
        val copiedOpaqueBytes = bytes.deserialize()

        assertEquals(opaqueBytes, copiedOpaqueBytes)
    }

    @Test
    fun `test serialization of sub-sequence OpaqueBytes`() {
        val bytesSequence = ByteSequence.of("0123456789".toByteArray(), 3, 2)
        val bytes = bytesSequence.serialize()
        val copiedBytesSequence = bytes.deserialize()

        assertEquals(bytesSequence, copiedBytesSequence)
    }

    @Test
    fun `test serialization of WireTransaction with dynamically loaded contract`() {
        val child = appContext.classLoader
        val contractClass = Class.forName(ISOLATED_CONTRACT_CLASS_NAME, true, child)
        val contract = contractClass.newInstance() as DummyContractBackdoor
        val tx = contract.generateInitial(MEGA_CORP.ref(0), 42, DUMMY_NOTARY)
        val context = SerializationFactory.defaultFactory.defaultContext
                .withWhitelisted(contract.javaClass)
                .withWhitelisted(Class.forName("$ISOLATED_CONTRACT_CLASS_NAME\$State", true, child))
                .withWhitelisted(Class.forName("$ISOLATED_CONTRACT_CLASS_NAME\$Commands\$Create", true, child))
                .withServiceHub(serviceHub)
                .withClassLoader(child)

        val bytes = run {
            val wireTransaction = tx.toWireTransaction(serviceHub, context)
            wireTransaction.serialize(context = context)
        }
        val copiedWireTransaction = bytes.deserialize(context = context)
        assertEquals(1, copiedWireTransaction.outputs.size)
        // Contracts need to be loaded by the same classloader as the ContractState itself
        val contractClassloader = copiedWireTransaction.getOutput(0).javaClass.classLoader
        val contract2 = contractClassloader.loadClass(copiedWireTransaction.outputs.first().contract).newInstance() as DummyContractBackdoor
        assertEquals(contract2.javaClass.classLoader, copiedWireTransaction.outputs[0].data.javaClass.classLoader)
        assertEquals(42, contract2.inspectState(copiedWireTransaction.outputs[0].data))
    }

    @Test
    fun `test deserialize of WireTransaction where contract cannot be found`() {
        kryoSpecific("Kryo verifies/loads attachments on deserialization, whereas AMQP currently does not") {
            ClassLoaderForTests().use { child ->
                val contractClass = Class.forName(ISOLATED_CONTRACT_CLASS_NAME, true, child)
                val contract = contractClass.newInstance() as DummyContractBackdoor
                val tx = contract.generateInitial(MEGA_CORP.ref(0), 42, DUMMY_NOTARY)
                val attachmentRef = attachmentId
                val bytes = run {
                    val outboundContext = SerializationFactory.defaultFactory.defaultContext
                            .withServiceHub(serviceHub)
                            .withClassLoader(child)
                    val wireTransaction = tx.toWireTransaction(serviceHub, outboundContext)
                    wireTransaction.serialize(context = outboundContext)
                }
                // use empty attachmentStorage

                val e = assertFailsWith(MissingAttachmentsException::class) {
                    val mockAttStorage = MockAttachmentStorage()
                    val inboundContext = SerializationFactory.defaultFactory.defaultContext
                            .withAttachmentStorage(mockAttStorage)
                            .withAttachmentsClassLoader(listOf(attachmentRef))
                    bytes.deserialize(context = inboundContext)

                    if (mockAttStorage.openAttachment(attachmentRef) == null) {
                        throw MissingAttachmentsException(listOf(attachmentRef))
                    }
                }
                assertEquals(attachmentRef, e.ids.single())
            }
        }
    }

    @Test
    fun `test loading a class from attachment during deserialization`() {
        ClassLoaderForTests().use { child ->
            val contractClass = Class.forName(ISOLATED_CONTRACT_CLASS_NAME, true, child)
            val contract = contractClass.newInstance() as DummyContractBackdoor
            val outboundContext = SerializationFactory.defaultFactory.defaultContext.withClassLoader(child)
            val attachmentRef = attachmentId
            // We currently ignore annotations in attachments, so manually whitelist.
            val inboundContext = SerializationFactory
                    .defaultFactory
                    .defaultContext
                    .withWhitelisted(contract.javaClass)
                    .withServiceHub(serviceHub)
                    .withAttachmentsClassLoader(listOf(attachmentRef))

            // Serialize with custom context to avoid populating the default context with the specially loaded class
            val serialized = contract.serialize(context = outboundContext)
            // Then deserialize with the attachment class loader associated with the attachment
            serialized.deserialize(context = inboundContext)
        }
    }

    @Test
    fun `test loading a class with attachment missing during deserialization`() {
        ClassLoaderForTests().use { child ->
            val contractClass = Class.forName(ISOLATED_CONTRACT_CLASS_NAME, true, child)
            val contract = contractClass.newInstance() as DummyContractBackdoor
            val attachmentRef = SecureHash.randomSHA256()
            val outboundContext = SerializationFactory.defaultFactory.defaultContext.withClassLoader(child)
            // Serialize with custom context to avoid populating the default context with the specially loaded class
            val serialized = contract.serialize(context = outboundContext)

            // Then deserialize with the attachment class loader associated with the attachment
            val e = assertFailsWith(MissingAttachmentsException::class) {
                // We currently ignore annotations in attachments, so manually whitelist.
                val inboundContext = SerializationFactory
                        .defaultFactory
                        .defaultContext
                        .withWhitelisted(contract.javaClass)
                        .withServiceHub(serviceHub)
                        .withAttachmentsClassLoader(listOf(attachmentRef))
                serialized.deserialize(context = inboundContext)
            }
            assertEquals(attachmentRef, e.ids.single())
        }
    }
}

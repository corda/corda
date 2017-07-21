package net.corda.core.node

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.serialization.*
import net.corda.core.serialization.SerializationDefaults.P2P_CONTEXT
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.MEGA_CORP
import net.corda.testing.TestDependencyInjectionBase
import net.corda.testing.node.MockAttachmentStorage
import org.apache.commons.io.IOUtils
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

interface DummyContractBackdoor {
    fun generateInitial(owner: PartyAndReference, magicNumber: Int, notary: Party): TransactionBuilder
    fun inspectState(state: ContractState): Int
}

val ATTACHMENT_TEST_PROGRAM_ID = AttachmentClassLoaderTests.AttachmentDummyContract()

class AttachmentClassLoaderTests : TestDependencyInjectionBase() {
    companion object {
        val ISOLATED_CONTRACTS_JAR_PATH: URL = AttachmentClassLoaderTests::class.java.getResource("isolated.jar")

        private fun SerializationContext.withAttachmentStorage(attachmentStorage: AttachmentStorage): SerializationContext {
            val serviceHub = mock<ServiceHub>()
            whenever(serviceHub.attachments).thenReturn(attachmentStorage)
            return this.withTokenContext(SerializeAsTokenContext(serviceHub) {}).withProperty(WireTransactionSerializer.attachmentsClassLoaderEnabled, true)
        }
    }

    class AttachmentDummyContract : Contract {
        data class State(val magicNumber: Int = 0) : ContractState {
            override val contract = ATTACHMENT_TEST_PROGRAM_ID
            override val participants: List<AbstractParty>
                get() = listOf()
        }

        interface Commands : CommandData {
            class Create : TypeOnlyCommandData(), Commands
        }

        override fun verify(tx: LedgerTransaction) {
            // Always accepts.
        }

        // The "empty contract"
        override val legalContractReference: SecureHash = SecureHash.sha256("")

        fun generateInitial(owner: PartyAndReference, magicNumber: Int, notary: Party): TransactionBuilder {
            val state = State(magicNumber)
            return TransactionType.General.Builder(notary = notary).withItems(state, Command(Commands.Create(), owner.party.owningKey))
        }
    }

    fun importJar(storage: AttachmentStorage) = ISOLATED_CONTRACTS_JAR_PATH.openStream().use { storage.importAttachment(it) }

    // These ClassLoaders work together to load 'AnotherDummyContract' in a disposable way, such that even though
    // the class may be on the unit test class path (due to default IDE settings, etc), it won't be loaded into the
    // regular app classloader but rather than ClassLoaderForTests. This helps keep our environment clean and
    // ensures we have precise control over where it's loaded.
    object FilteringClassLoader : ClassLoader() {
        override fun loadClass(name: String, resolve: Boolean): Class<*>? {
            if ("AnotherDummyContract" in name) {
                return null
            } else
                return super.loadClass(name, resolve)
        }
    }

    class ClassLoaderForTests : URLClassLoader(arrayOf(ISOLATED_CONTRACTS_JAR_PATH), FilteringClassLoader)

    @Test
    fun `dynamically load AnotherDummyContract from isolated contracts jar`() {
        val child = ClassLoaderForTests()

        val contractClass = Class.forName("net.corda.contracts.isolated.AnotherDummyContract", true, child)
        val contract = contractClass.newInstance() as Contract

        assertEquals(SecureHash.sha256("https://anotherdummy.org"), contract.legalContractReference)
    }

    fun fakeAttachment(filepath: String, content: String): ByteArray {
        val bs = ByteArrayOutputStream()
        val js = JarOutputStream(bs)
        js.putNextEntry(ZipEntry(filepath))
        js.writer().apply { append(content); flush() }
        js.closeEntry()
        js.close()
        return bs.toByteArray()
    }

    fun readAttachment(attachment: Attachment, filepath: String): ByteArray {
        ByteArrayOutputStream().use {
            attachment.extractFile(filepath, it)
            return it.toByteArray()
        }

    }

    @Test
    fun `test MockAttachmentStorage open as jar`() {
        val storage = MockAttachmentStorage()
        val key = importJar(storage)
        val attachment = storage.openAttachment(key)!!

        val jar = attachment.openAsJAR()

        assertNotNull(jar.nextEntry)
    }

    @Test
    fun `test overlapping file exception`() {
        val storage = MockAttachmentStorage()

        val att0 = importJar(storage)
        val att1 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file.txt", "some data")))
        val att2 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file.txt", "some other data")))

        assertFailsWith(AttachmentsClassLoader.OverlappingAttachments::class) {
            AttachmentsClassLoader(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! })
        }
    }

    @Test
    fun `basic`() {
        val storage = MockAttachmentStorage()

        val att0 = importJar(storage)
        val att1 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file1.txt", "some data")))
        val att2 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")))

        val cl = AttachmentsClassLoader(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! })
        val txt = IOUtils.toString(cl.getResourceAsStream("file1.txt"), Charsets.UTF_8.name())
        assertEquals("some data", txt)
    }

    @Test
    fun `Check platform independent path handling in attachment jars`() {
        val storage = MockAttachmentStorage()

        val att1 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("/folder1/foldera/file1.txt", "some data")))
        val att2 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("\\folder1\\folderb\\file2.txt", "some other data")))

        val data1a = readAttachment(storage.openAttachment(att1)!!, "/folder1/foldera/file1.txt")
        Assert.assertArrayEquals("some data".toByteArray(), data1a)

        val data1b = readAttachment(storage.openAttachment(att1)!!, "\\folder1\\foldera\\file1.txt")
        Assert.assertArrayEquals("some data".toByteArray(), data1b)

        val data2a = readAttachment(storage.openAttachment(att2)!!, "\\folder1\\folderb\\file2.txt")
        Assert.assertArrayEquals("some other data".toByteArray(), data2a)

        val data2b = readAttachment(storage.openAttachment(att2)!!, "/folder1/folderb/file2.txt")
        Assert.assertArrayEquals("some other data".toByteArray(), data2b)

    }

    @Test
    fun `loading class AnotherDummyContract`() {
        val storage = MockAttachmentStorage()

        val att0 = importJar(storage)
        val att1 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file1.txt", "some data")))
        val att2 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")))

        val cl = AttachmentsClassLoader(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! }, FilteringClassLoader)
        val contractClass = Class.forName("net.corda.contracts.isolated.AnotherDummyContract", true, cl)
        val contract = contractClass.newInstance() as Contract
        assertEquals(cl, contract.javaClass.classLoader)
        assertEquals(SecureHash.sha256("https://anotherdummy.org"), contract.legalContractReference)
    }


    @Test
    fun `verify that contract DummyContract is in classPath`() {
        val contractClass = Class.forName("net.corda.core.node.AttachmentClassLoaderTests\$AttachmentDummyContract")
        val contract = contractClass.newInstance() as Contract

        assertNotNull(contract)
    }

    fun createContract2Cash(): Contract {
        val cl = ClassLoaderForTests()
        val contractClass = Class.forName("net.corda.contracts.isolated.AnotherDummyContract", true, cl)
        return contractClass.newInstance() as Contract
    }

    @Test
    fun `testing Kryo with ClassLoader (with top level class name)`() {
        val contract = createContract2Cash()

        val bytes = contract.serialize()

        val storage = MockAttachmentStorage()

        val att0 = importJar(storage)
        val att1 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file1.txt", "some data")))
        val att2 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")))

        val cl = AttachmentsClassLoader(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! }, FilteringClassLoader)

        val context = P2P_CONTEXT.withClassLoader(cl).withWhitelisted(contract.javaClass)
        val state2 = bytes.deserialize(context = context)
        assertTrue(state2.javaClass.classLoader is AttachmentsClassLoader)
        assertNotNull(state2)
    }

    // top level wrapper
    @CordaSerializable
    class Data(val contract: Contract)

    @Test
    fun `testing Kryo with ClassLoader (without top level class name)`() {
        val data = Data(createContract2Cash())

        assertNotNull(data.contract)

        val context2 = P2P_CONTEXT.withWhitelisted(data.contract.javaClass)

        val bytes = data.serialize(context = context2)

        val storage = MockAttachmentStorage()

        val att0 = importJar(storage)
        val att1 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file1.txt", "some data")))
        val att2 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")))

        val cl = AttachmentsClassLoader(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! }, FilteringClassLoader)

        val context = P2P_CONTEXT.withClassLoader(cl).withWhitelisted(Class.forName("net.corda.contracts.isolated.AnotherDummyContract", true, cl))

        val state2 = bytes.deserialize(context = context)
        assertEquals(cl, state2.contract.javaClass.classLoader)
        assertNotNull(state2)

        // We should be able to load same class from a different class loader and have them be distinct.
        val cl2 = AttachmentsClassLoader(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! }, FilteringClassLoader)

        val context3 = P2P_CONTEXT.withClassLoader(cl2).withWhitelisted(Class.forName("net.corda.contracts.isolated.AnotherDummyContract", true, cl2))

        val state3 = bytes.deserialize(context = context3)
        assertEquals(cl2, state3.contract.javaClass.classLoader)
        assertNotNull(state3)
    }


    @Test
    fun `test serialization of WireTransaction with statically loaded contract`() {
        val tx = ATTACHMENT_TEST_PROGRAM_ID.generateInitial(MEGA_CORP.ref(0), 42, DUMMY_NOTARY)
        val wireTransaction = tx.toWireTransaction()
        val bytes = wireTransaction.serialize()
        val copiedWireTransaction = bytes.deserialize()

        assertEquals(1, copiedWireTransaction.outputs.size)
        assertEquals(42, (copiedWireTransaction.outputs[0].data as AttachmentDummyContract.State).magicNumber)
    }

    @Test
    fun `test serialization of WireTransaction with dynamically loaded contract`() {
        val child = ClassLoaderForTests()
        val contractClass = Class.forName("net.corda.contracts.isolated.AnotherDummyContract", true, child)
        val contract = contractClass.newInstance() as DummyContractBackdoor
        val tx = contract.generateInitial(MEGA_CORP.ref(0), 42, DUMMY_NOTARY)
        val storage = MockAttachmentStorage()
        val context = P2P_CONTEXT.withWhitelisted(contract.javaClass)
                .withWhitelisted(Class.forName("net.corda.contracts.isolated.AnotherDummyContract\$State", true, child))
                .withWhitelisted(Class.forName("net.corda.contracts.isolated.AnotherDummyContract\$Commands\$Create", true, child))
                .withAttachmentStorage(storage)

        // todo - think about better way to push attachmentStorage down to serializer
        val bytes = run {
            val attachmentRef = importJar(storage)
            tx.addAttachment(storage.openAttachment(attachmentRef)!!.id)
            val wireTransaction = tx.toWireTransaction()
            wireTransaction.serialize(context = context)
        }
        val copiedWireTransaction = bytes.deserialize(context = context)
        assertEquals(1, copiedWireTransaction.outputs.size)
        val contract2 = copiedWireTransaction.getOutput(0).contract as DummyContractBackdoor
        assertEquals(42, contract2.inspectState(copiedWireTransaction.outputs[0].data))
    }

    @Test
    fun `test deserialize of WireTransaction where contract cannot be found`() {
        val child = ClassLoaderForTests()
        val contractClass = Class.forName("net.corda.contracts.isolated.AnotherDummyContract", true, child)
        val contract = contractClass.newInstance() as DummyContractBackdoor
        val tx = contract.generateInitial(MEGA_CORP.ref(0), 42, DUMMY_NOTARY)
        val storage = MockAttachmentStorage()

        // todo - think about better way to push attachmentStorage down to serializer
        val attachmentRef = importJar(storage)
        val bytes = run {

            tx.addAttachment(storage.openAttachment(attachmentRef)!!.id)

            val wireTransaction = tx.toWireTransaction()

            wireTransaction.serialize(context = P2P_CONTEXT.withAttachmentStorage(storage))
        }
        // use empty attachmentStorage

        val e = assertFailsWith(MissingAttachmentsException::class) {
            bytes.deserialize(context = P2P_CONTEXT.withAttachmentStorage(MockAttachmentStorage()))
        }
        assertEquals(attachmentRef, e.ids.single())
    }
}

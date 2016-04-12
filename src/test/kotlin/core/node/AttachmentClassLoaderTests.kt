package core.node

import contracts.DUMMY_PROGRAM_ID
import contracts.DummyContract
import core.*
import core.crypto.SecureHash
import core.node.services.AttachmentStorage
import core.serialization.*
import core.testutils.DUMMY_NOTARY
import core.testutils.MEGA_CORP
import org.apache.commons.io.IOUtils
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLClassLoader
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

interface DummyContractBackdoor {
    fun generateInitial(owner: PartyAndReference, magicNumber: Int, notary: Party): TransactionBuilder
    fun inspectState(state: ContractState): Int
}

class AttachmentClassLoaderTests {
    companion object {
        val ISOLATED_CONTRACTS_JAR_PATH = AttachmentClassLoaderTests::class.java.getResource("isolated.jar")
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

        val contractClass = Class.forName("contracts.isolated.AnotherDummyContract", true, child)
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

    @Test
    fun `test MockAttachmentStorage open as jar`() {
        val storage = MockAttachmentStorage()
        val key = importJar(storage)
        val attachment = storage.openAttachment(key)!!

        val jar = attachment.openAsJAR()

        assert(jar.nextEntry != null)
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
        val txt = IOUtils.toString(cl.getResourceAsStream("file1.txt"))
        assertEquals("some data", txt)
    }

    @Test
    fun `loading class AnotherDummyContract`() {
        val storage = MockAttachmentStorage()

        val att0 = importJar(storage)
        val att1 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file1.txt", "some data")))
        val att2 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")))

        val cl = AttachmentsClassLoader(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! }, FilteringClassLoader)
        val contractClass = Class.forName("contracts.isolated.AnotherDummyContract", true, cl)
        val contract = contractClass.newInstance() as Contract
        assertEquals(cl, contract.javaClass.classLoader)
        assertEquals(SecureHash.sha256("https://anotherdummy.org"), contract.legalContractReference)
    }


    @Test
    fun `verify that contract DummyContract is in classPath`() {
        val contractClass = Class.forName("contracts.DummyContract")
        val contract = contractClass.newInstance() as Contract

        assertNotNull(contract)
    }

    fun createContract2Cash(): Contract {
        val cl = ClassLoaderForTests()
        val contractClass = Class.forName("contracts.isolated.AnotherDummyContract", true, cl)
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

        val kryo = createKryo()
        kryo.classLoader = cl

        val state2 = bytes.deserialize(kryo)
        assert(state2.javaClass.classLoader is AttachmentsClassLoader)
        assertNotNull(state2)
    }

    // top level wrapper
    class Data(val contract: Contract)

    @Test
    fun `testing Kryo with ClassLoader (without top level class name)`() {
        val data = Data(createContract2Cash())

        assertNotNull(data.contract)

        val bytes = data.serialize()

        val storage = MockAttachmentStorage()

        val att0 = importJar(storage)
        val att1 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file1.txt", "some data")))
        val att2 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")))

        val cl = AttachmentsClassLoader(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! }, FilteringClassLoader)

        val kryo = createKryo()
        kryo.classLoader = cl

        val state2 = bytes.deserialize(kryo)
        assertEquals(cl, state2.contract.javaClass.classLoader)
        assertNotNull(state2)
    }

    @Test
    fun `test serialization of WireTransaction with statically loaded contract`() {
        val tx = DUMMY_PROGRAM_ID.generateInitial(MEGA_CORP.ref(0), 42, DUMMY_NOTARY)
        val wireTransaction = tx.toWireTransaction()
        val bytes = wireTransaction.serialize()
        val copiedWireTransaction = bytes.deserialize()

        assertEquals(1, copiedWireTransaction.outputs.size)
        assertEquals(42, (copiedWireTransaction.outputs[0] as DummyContract.State).magicNumber)
    }

    @Test
    fun `test serialization of WireTransaction with dynamically loaded contract`() {
        val child = ClassLoaderForTests()
        val contractClass = Class.forName("contracts.isolated.AnotherDummyContract", true, child)
        val contract = contractClass.newInstance() as DummyContractBackdoor
        val tx = contract.generateInitial(MEGA_CORP.ref(0), 42, DUMMY_NOTARY)
        val storage = MockAttachmentStorage()
        val kryo = createKryo()

        // todo - think about better way to push attachmentStorage down to serializer
        kryo.attachmentStorage = storage

        val attachmentRef = importJar(storage)

        tx.addAttachment(storage.openAttachment(attachmentRef)!!)

        val wireTransaction = tx.toWireTransaction()

        val bytes = wireTransaction.serialize(kryo)

        val kryo2 = createKryo()
        // use empty attachmentStorage
        kryo2.attachmentStorage = storage

        val copiedWireTransaction = bytes.deserialize(kryo2)

        assertEquals(1, copiedWireTransaction.outputs.size)
        val contract2 = copiedWireTransaction.outputs[0].contract as DummyContractBackdoor
        assertEquals(42, contract2.inspectState(copiedWireTransaction.outputs[0]))
    }

    @Test
    fun `test deserialize of WireTransaction where contract cannot be found`() {
        val child = ClassLoaderForTests()
        val contractClass = Class.forName("contracts.isolated.AnotherDummyContract", true, child)
        val contract = contractClass.newInstance() as DummyContractBackdoor
        val tx = contract.generateInitial(MEGA_CORP.ref(0), 42, DUMMY_NOTARY)
        val storage = MockAttachmentStorage()
        val kryo = createKryo()

        // todo - think about better way to push attachmentStorage down to serializer
        kryo.attachmentStorage = storage

        val attachmentRef = importJar(storage)

        tx.addAttachment(storage.openAttachment(attachmentRef)!!)

        val wireTransaction = tx.toWireTransaction()

        val bytes = wireTransaction.serialize(kryo)

        val kryo2 = createKryo()
        // use empty attachmentStorage
        kryo2.attachmentStorage = MockAttachmentStorage()

        val e = assertFailsWith(MissingAttachmentsException::class) {
            bytes.deserialize(kryo2)
        }
        assertEquals(attachmentRef, e.ids.single())
    }
}
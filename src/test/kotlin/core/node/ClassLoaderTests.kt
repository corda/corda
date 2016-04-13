package core.node

import com.esotericsoftware.kryo.KryoException
import contracts.DUMMY_PROGRAM_ID
import contracts.DummyContract
import core.*
import core.crypto.SecureHash
import core.node.services.AttachmentStorage
import core.serialization.attachmentStorage
import core.serialization.createKryo
import core.serialization.deserialize
import core.serialization.serialize
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
    fun generateInitial(owner: PartyReference, magicNumber: Int) : TransactionBuilder

    fun inspectState(state: ContractState) : Int
}

class ClassLoaderTests {
    val ISOLATED_CONTRACTS_JAR_PATH = ClassLoaderTests::class.java.getResource("isolated.jar")

    fun importJar(storage: AttachmentStorage) = ISOLATED_CONTRACTS_JAR_PATH.openStream().use { storage.importAttachment(it) }

    @Test
    fun `dynamically load AnotherDummyContract from isolated contracts jar`() {
        val child = URLClassLoader(arrayOf(ISOLATED_CONTRACTS_JAR_PATH))

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

        assert( jar.nextEntry != null )
    }

    @Test
    fun `test overlapping file exception`() {

        val storage = MockAttachmentStorage()

        val att0 = importJar(storage)
        val att1 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file.txt", "some data")) )
        val att2 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file.txt", "some other data")) )

        assertFailsWith( OverlappingAttachments::class ) {

            AttachmentsClassLoader.create(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! })

        }
    }

    @Test
    fun `basic`() {

        val storage = MockAttachmentStorage()

        val att0 = importJar(storage)
        val att1 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file1.txt", "some data")) )
        val att2 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")) )

        AttachmentsClassLoader.create(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! }).use {
            val txt = IOUtils.toString(it.getResourceAsStream("file1.txt"))
            assertEquals( "some data", txt )
        }
    }

    @Test
    fun `loading class AnotherDummyContract`() {
        val storage = MockAttachmentStorage()

        val att0 = importJar(storage)
        val att1 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file1.txt", "some data")) )
        val att2 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")) )

        AttachmentsClassLoader.create(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! }).use {

            val contractClass = Class.forName("contracts.isolated.AnotherDummyContract", true, it)
            val contract = contractClass.newInstance() as Contract

            assertEquals(SecureHash.sha256("https://anotherdummy.org"), contract.legalContractReference)
        }
    }


    /**
     * If this test starts failing it is either because you haven't build with gradle first
     * or because you have reimported gradle project in IDEA (revert change to .idea/modules.xml)
     */
    @Test
    fun `verify that contract AnotherDummyContract is not in classPath`() {
        assertFailsWith(ClassNotFoundException::class) {
            val contractClass = Class.forName("contracts.isolated.AnotherDummyContract")
            contractClass.newInstance() as Contract
        }
    }

    @Test
    fun `verify that contract DummyContract is in classPath`() {
        val contractClass = Class.forName("contracts.DummyContract")
        val contract = contractClass.newInstance() as Contract

        assertNotNull(contract)
    }

    fun createContract2Cash() : Contract {
        val child = URLClassLoader(arrayOf(ISOLATED_CONTRACTS_JAR_PATH))
        val contractClass = Class.forName("contracts.isolated.AnotherDummyContract", true, child)
        val contract = contractClass.newInstance() as Contract
        return contract
    }

    @Test
    fun `testing Kryo with ClassLoader (with top level class name)`() {
        val contract = createContract2Cash()

        val bytes = contract.serialize(includeClassName = true)

        val storage = MockAttachmentStorage()

        val att0 = importJar(storage)
        val att1 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file1.txt", "some data")) )
        val att2 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")) )

        val clsLoader = AttachmentsClassLoader.create(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! })

        val kryo = createKryo()
        kryo.classLoader = clsLoader

        val state2 = bytes.deserialize(kryo, true)

        assertNotNull(state2)
    }

    // top level wrapper
    class Data( val contract: Contract )

    @Test
    fun `testing Kryo with ClassLoader (without top level class name)`() {
        val data =  Data( createContract2Cash() )

        assertNotNull(data.contract)

        val bytes = data.serialize()

        val storage = MockAttachmentStorage()

        val att0 = importJar(storage)
        val att1 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file1.txt", "some data")) )
        val att2 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")) )

        val clsLoader = AttachmentsClassLoader.create(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! })

        val kryo = createKryo()
        kryo.classLoader = clsLoader

        val state2 = bytes.deserialize(kryo)

        assertNotNull(state2)
    }

    @Test
    fun `test serialization of WireTransaction with statically loaded contract`() {
        val tx = DUMMY_PROGRAM_ID.generateInitial(MEGA_CORP.ref(0), 42)

        val wireTransaction = tx.toWireTransaction()

        val bytes = wireTransaction.serialize()

        val copiedWireTransaction = bytes.deserialize()

        assertEquals(1, copiedWireTransaction.outputs.size)

        assertEquals(42, (copiedWireTransaction.outputs[0] as DummyContract.State).magicNumber)
    }

    @Test
    fun `test serialization of WireTransaction with dynamically loaded contract`() {
        val child = URLClassLoader(arrayOf(ISOLATED_CONTRACTS_JAR_PATH))

        val contractClass = Class.forName("contracts.isolated.AnotherDummyContract", true, child)
        val contract = contractClass.newInstance() as DummyContractBackdoor

        val tx = contract.generateInitial(MEGA_CORP.ref(0), 42)

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
        assertEquals(42, contract2.inspectState( copiedWireTransaction.outputs[0] ))
    }

    @Test
    fun `test deserialize of WireTransaction where contract cannot be found`() {
        val child = URLClassLoader(arrayOf(ISOLATED_CONTRACTS_JAR_PATH))

        val contractClass = Class.forName("contracts.isolated.AnotherDummyContract", true, child)
        val contract = contractClass.newInstance() as DummyContractBackdoor

        val tx = contract.generateInitial(MEGA_CORP.ref(0), 42)

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

        assertFailsWith(KryoException::class) {
            bytes.deserialize(kryo2)
        }
    }
}
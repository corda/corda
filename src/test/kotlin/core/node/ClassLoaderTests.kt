package core.node

import com.esotericsoftware.kryo.KryoException
import contracts.DUMMY_PROGRAM_ID
import contracts.DummyContract
import core.*
import core.crypto.SecureHash
import core.serialization.attachmentStorage
import core.serialization.createKryo
import core.serialization.deserialize
import core.serialization.serialize
import core.testutils.MEGA_CORP
import org.apache.commons.io.IOUtils
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.net.URL
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

    val ISOLATED_CONTRACTS_JAR_PATH = "contracts/isolated/build/libs/isolated.jar"

    @Test
    fun `dynamically load AnotherDummyContract from isolated contracts jar`() {
        var child = URLClassLoader(arrayOf(URL("file", "", ISOLATED_CONTRACTS_JAR_PATH)))

        var contractClass = Class.forName("contracts.isolated.AnotherDummyContract", true, child)
        var contract = contractClass.newInstance() as Contract

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
        val key = storage.importAttachment( FileInputStream(ISOLATED_CONTRACTS_JAR_PATH) )
        val attachment = storage.openAttachment(key)!!

        val jar = attachment.openAsJAR()

        assert( jar.nextEntry != null )
    }

    @Test
    fun `test overlapping file exception`() {

        var storage = MockAttachmentStorage()

        var att0 = storage.importAttachment( FileInputStream(ISOLATED_CONTRACTS_JAR_PATH) )
        var att1 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file.txt", "some data")) )
        var att2 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file.txt", "some other data")) )

        assertFailsWith( OverlappingAttachments::class ) {

            AttachmentsClassLoader.create(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! })

        }
    }

    @Test
    fun `basic`() {

        var storage = MockAttachmentStorage()

        var att0 = storage.importAttachment( FileInputStream(ISOLATED_CONTRACTS_JAR_PATH) )
        var att1 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file1.txt", "some data")) )
        var att2 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")) )

        AttachmentsClassLoader.create(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! }).use {
            var txt = IOUtils.toString(it.getResourceAsStream("file1.txt"))
            assertEquals( "some data", txt )
        }
    }

    @Test
    fun `loading class AnotherDummyContract`() {
        var storage = MockAttachmentStorage()

        var att0 = storage.importAttachment( FileInputStream(ISOLATED_CONTRACTS_JAR_PATH) )
        var att1 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file1.txt", "some data")) )
        var att2 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")) )

        AttachmentsClassLoader.create(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! }).use {

            var contractClass = Class.forName("contracts.isolated.AnotherDummyContract", true, it)
            var contract = contractClass.newInstance() as Contract

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
            var contractClass = Class.forName("contracts.isolated.AnotherDummyContract")
            contractClass.newInstance() as Contract
        }
    }

    @Test
    fun `verify that contract DummyContract is in classPath`() {
        var contractClass = Class.forName("contracts.DummyContract")
        var contract = contractClass.newInstance() as Contract

        assertNotNull(contract)
    }

    fun createContract2Cash() : Contract {
        var child = URLClassLoader(arrayOf(URL("file", "", ISOLATED_CONTRACTS_JAR_PATH)))

        var contractClass = Class.forName("contracts.isolated.AnotherDummyContract", true, child)
        var contract = contractClass.newInstance() as Contract
        return contract
    }

    @Test
    fun `testing Kryo with ClassLoader (with top level class name)`() {
        val contract = createContract2Cash()

        val bytes = contract.serialize(includeClassName = true)

        var storage = MockAttachmentStorage()

        var att0 = storage.importAttachment( FileInputStream(ISOLATED_CONTRACTS_JAR_PATH) )
        var att1 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file1.txt", "some data")) )
        var att2 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")) )

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

        var storage = MockAttachmentStorage()

        var att0 = storage.importAttachment( FileInputStream(ISOLATED_CONTRACTS_JAR_PATH) )
        var att1 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file1.txt", "some data")) )
        var att2 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")) )

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
        var child = URLClassLoader(arrayOf(URL("file", "", ISOLATED_CONTRACTS_JAR_PATH)))

        var contractClass = Class.forName("contracts.isolated.AnotherDummyContract", true, child)
        var contract = contractClass.newInstance() as DummyContractBackdoor

        val tx = contract.generateInitial(MEGA_CORP.ref(0), 42)

        var storage = MockAttachmentStorage()

        var kryo = createKryo()

        // todo - think about better way to push attachmentStorage down to serializer
        kryo.attachmentStorage = storage

        var attachmentRef = storage.importAttachment( FileInputStream(ISOLATED_CONTRACTS_JAR_PATH) )

        tx.addAttachment(storage.openAttachment(attachmentRef)!!)

        val wireTransaction = tx.toWireTransaction()

        val bytes = wireTransaction.serialize(kryo)

        kryo = createKryo()

        // use empty attachmentStorage
        kryo.attachmentStorage = storage

        val copiedWireTransaction = bytes.deserialize(kryo)

        assertEquals(1, copiedWireTransaction.outputs.size)

        var contract2 = copiedWireTransaction.outputs[0].contract as DummyContractBackdoor
        assertEquals(42, contract2.inspectState( copiedWireTransaction.outputs[0] ))
    }

    @Test
    fun `test deserialize of WireTransaction where contract cannot be found`() {
        var child = URLClassLoader(arrayOf(URL("file", "", ISOLATED_CONTRACTS_JAR_PATH)))

        var contractClass = Class.forName("contracts.isolated.AnotherDummyContract", true, child)
        var contract = contractClass.newInstance() as DummyContractBackdoor

        val tx = contract.generateInitial(MEGA_CORP.ref(0), 42)

        var storage = MockAttachmentStorage()

        var kryo = createKryo()

        // todo - think about better way to push attachmentStorage down to serializer
        kryo.attachmentStorage = storage

        var attachmentRef = storage.importAttachment(FileInputStream(ISOLATED_CONTRACTS_JAR_PATH))

        tx.addAttachment(storage.openAttachment(attachmentRef)!!)

        val wireTransaction = tx.toWireTransaction()

        val bytes = wireTransaction.serialize(kryo)

        kryo = createKryo()
        // use empty attachmentStorage
        kryo.attachmentStorage = MockAttachmentStorage()

        assertFailsWith(KryoException::class) {
            bytes.deserialize(kryo)
        }
    }
}
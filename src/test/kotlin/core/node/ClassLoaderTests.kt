package core.node

import core.Contract
import core.MockAttachmentStorage
import core.crypto.SecureHash
import core.serialization.createKryo
import core.serialization.deserialize
import core.serialization.serialize
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

            var cl = ClassLoader.create(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! })

        }
    }

    @Test
    fun `basic`() {

        var storage = MockAttachmentStorage()

        var att0 = storage.importAttachment( FileInputStream(ISOLATED_CONTRACTS_JAR_PATH) )
        var att1 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file1.txt", "some data")) )
        var att2 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")) )

        ClassLoader.create( arrayOf( att0, att1, att2 ).map { storage.openAttachment(it)!! } ).use {
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

        ClassLoader.create( arrayOf( att0, att1, att2 ).map { storage.openAttachment(it)!! } ).use {

            var contractClass = Class.forName("contracts.isolated.AnotherDummyContract", true, it)
            var contract = contractClass.newInstance() as Contract

            assertEquals(SecureHash.sha256("https://anotherdummy.org"), contract.legalContractReference)
        }
    }

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

        val clsLoader = ClassLoader.create( arrayOf( att0, att1, att2 ).map { storage.openAttachment(it)!! } )

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

        val bytes = data.serialize()

        var storage = MockAttachmentStorage()

        var att0 = storage.importAttachment( FileInputStream(ISOLATED_CONTRACTS_JAR_PATH) )
        var att1 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file1.txt", "some data")) )
        var att2 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")) )

        val clsLoader = ClassLoader.create( arrayOf( att0, att1, att2 ).map { storage.openAttachment(it)!! } )

        val kryo = createKryo()
        kryo.classLoader = clsLoader

        val state2 = bytes.deserialize(kryo)

        assertNotNull(state2)
    }

    @Test
    fun `white list serialization`() {

    }

}
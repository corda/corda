package core.node

import core.Contract
import core.MockAttachmentStorage
import core.crypto.SecureHash
import org.apache.commons.io.IOUtils
import org.junit.Ignore
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClassLoaderTests {

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
        val key = storage.importAttachment( FileInputStream("contracts/build/libs/contracts.jar") )
        val attachment = storage.openAttachment(key)!!

        val jar = attachment.openAsJAR()

        assert( jar.nextEntry != null )
    }

    @Test
    fun `test overlapping file exception`() {

        var storage = MockAttachmentStorage()

        var att0 = storage.importAttachment( FileInputStream("contracts/build/libs/contracts.jar") )
        var att1 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file.txt", "some data")) )
        var att2 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file.txt", "some other data")) )

        assertFailsWith( OverlappingAttachments::class ) {

            var cl = ClassLoader.create(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! })

        }
    }

    @Test
    fun `basic`() {

        var storage = MockAttachmentStorage()

        var att0 = storage.importAttachment( FileInputStream("contracts/build/libs/contracts.jar") )
        var att1 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file1.txt", "some data")) )
        var att2 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")) )

        ClassLoader.create( arrayOf( att0, att1, att2 ).map { storage.openAttachment(it)!! } ).use {
            var txt = IOUtils.toString(it.getResourceAsStream("file1.txt"))
            assertEquals( "some data", txt )
        }
    }

    @Test
    fun `loading class Cash`() {
        var storage = MockAttachmentStorage()

        var att0 = storage.importAttachment( FileInputStream("contracts/build/libs/contracts.jar") )
        var att1 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file1.txt", "some data")) )
        var att2 = storage.importAttachment( ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")) )

        ClassLoader.create( arrayOf( att0, att1, att2 ).map { storage.openAttachment(it)!! } ).use {

            var contractClass = Class.forName("contracts.Cash", true, it)
            var contract = contractClass.newInstance() as Contract

            assertEquals(SecureHash.sha256("https://www.big-book-of-banking-law.gov/cash-claims.html"), contract.legalContractReference)
        }
    }

    @Ignore
    @Test
    fun `testing Kryo with ClassLoader`() {
        assert(false) // todo
    }

}
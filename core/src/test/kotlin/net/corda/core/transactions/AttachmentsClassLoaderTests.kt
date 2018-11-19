package net.corda.core.transactions

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.internal.declaredField
import net.corda.core.serialization.internal.AttachmentsClassLoader
import net.corda.testing.internal.fakeAttachment
import net.corda.testing.services.MockAttachmentStorage
import org.apache.commons.io.IOUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.URL
import kotlin.test.assertFailsWith

class AttachmentsClassLoaderTests {

    companion object {
        val ISOLATED_CONTRACTS_JAR_PATH: URL = AttachmentsClassLoaderTests::class.java.getResource("isolated.jar")
        private const val ISOLATED_CONTRACT_CLASS_NAME = "net.corda.finance.contracts.isolated.AnotherDummyContract"

        private fun readAttachment(attachment: Attachment, filepath: String): ByteArray {
            ByteArrayOutputStream().use {
                attachment.extractFile(filepath, it)
                return it.toByteArray()
            }
        }
    }

    val storage = MockAttachmentStorage()

    @Test
    fun `Loading AnotherDummyContract without using the AttachmentsClassLoader fails`() {
        assertFailsWith<ClassNotFoundException> {
            Class.forName(ISOLATED_CONTRACT_CLASS_NAME)
        }
    }

    @Test
    fun `Dynamically load AnotherDummyContract from isolated contracts jar using the AttachmentsClassLoader`() {
        val isolatedId = storage.importAttachment(ISOLATED_CONTRACTS_JAR_PATH.openStream(), "app", "isolated.jar")

        val classloader = AttachmentsClassLoader(listOf(storage.openAttachment(isolatedId)!!))
        val contractClass = Class.forName(ISOLATED_CONTRACT_CLASS_NAME, true, classloader)
        val contract = contractClass.newInstance() as Contract
        assertEquals("helloworld", contract.declaredField<Any?>("magicString").value)
    }

    @Test
    fun `Load text resources from AttachmentsClassLoader`() {
        val att1 = storage.importAttachment(fakeAttachment("file1.txt", "some data").inputStream(), "app", "file1.jar")
        val att2 = storage.importAttachment(fakeAttachment("file2.txt", "some other data").inputStream(), "app", "file2.jar")

        val cl = AttachmentsClassLoader(arrayOf(att1, att2).map { storage.openAttachment(it)!! })
        val txt = IOUtils.toString(cl.getResourceAsStream("file1.txt"), Charsets.UTF_8.name())
        assertEquals("some data", txt)

        val txt1 = IOUtils.toString(cl.getResourceAsStream("file2.txt"), Charsets.UTF_8.name())
        assertEquals("some other data", txt1)
    }

    @Test
    fun `Test overlapping file exception`() {
        val att1 = storage.importAttachment(fakeAttachment("file1.txt", "some data").inputStream(), "app", "file1.jar")
        val att2 = storage.importAttachment(fakeAttachment("file1.txt", "some other data").inputStream(), "app", "file2.jar")

        assertFailsWith(TransactionVerificationException.OverlappingAttachmentsException::class) {
            AttachmentsClassLoader(arrayOf(att1, att2).map { storage.openAttachment(it)!! })
        }
    }

    @Test
    fun `Test valid overlapping file condition`() {
        val att1 = storage.importAttachment(fakeAttachment("file1.txt", "same data").inputStream(), "app", "file1.jar")
        val att2 = storage.importAttachment(fakeAttachment("file1.txt", "same data").inputStream(), "app", "file2.jar")

        val cl = AttachmentsClassLoader(arrayOf(att1, att2).map { storage.openAttachment(it)!! })
        val txt = IOUtils.toString(cl.getResourceAsStream("file1.txt"), Charsets.UTF_8.name())
        assertEquals("same data", txt)
    }

    @Test
    fun `No overlapping exception thrown on certain META-INF files`() {
        listOf("meta-inf/manifest.mf", "meta-inf/license", "meta-inf/test.dsa", "meta-inf/test.sf").forEach { path ->
            val att1 = storage.importAttachment(fakeAttachment(path, "some data").inputStream(), "app", "file1.jar")
            val att2 = storage.importAttachment(fakeAttachment(path, "some other data").inputStream(), "app", "file2.jar")

            AttachmentsClassLoader(arrayOf(att1, att2).map { storage.openAttachment(it)!! })
        }
    }

    @Test
    fun `Check platform independent path handling in attachment jars`() {
        val storage = MockAttachmentStorage()

        val att1 = storage.importAttachment(fakeAttachment("/folder1/foldera/file1.txt", "some data").inputStream(), "app", "file1.jar")
        val att2 = storage.importAttachment(fakeAttachment("\\folder1\\folderb\\file2.txt", "some other data").inputStream(), "app", "file2.jar")

        val data1a = readAttachment(storage.openAttachment(att1)!!, "/folder1/foldera/file1.txt")
        assertArrayEquals("some data".toByteArray(), data1a)

        val data1b = readAttachment(storage.openAttachment(att1)!!, "\\folder1\\foldera\\file1.txt")
        assertArrayEquals("some data".toByteArray(), data1b)

        val data2a = readAttachment(storage.openAttachment(att2)!!, "\\folder1\\folderb\\file2.txt")
        assertArrayEquals("some other data".toByteArray(), data2a)

        val data2b = readAttachment(storage.openAttachment(att2)!!, "/folder1/folderb/file2.txt")
        assertArrayEquals("some other data".toByteArray(), data2b)
    }
}

/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.messaging

import protocols.FetchAttachmentsProtocol
import protocols.FetchDataProtocol
import core.Attachment
import core.crypto.SecureHash
import core.crypto.sha256
import core.node.MockNetwork
import core.node.services.NodeAttachmentService
import core.serialization.OpaqueBytes
import core.testutils.rootCauseExceptions
import core.utilities.BriefLogFormatter
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AttachmentTests {
    lateinit var network: MockNetwork

    init {
        BriefLogFormatter.init()
    }

    @Before
    fun setUp() {
        network = MockNetwork()
    }

    fun fakeAttachment(): ByteArray {
        val bs = ByteArrayOutputStream()
        val js = JarOutputStream(bs)
        js.putNextEntry(ZipEntry("file1.txt"))
        js.writer().append("Some useful content")
        js.closeEntry()
        js.close()
        return bs.toByteArray()
    }

    @Test
    fun `download and store`() {
        val (n0, n1) = network.createTwoNodes()

        // Insert an attachment into node zero's store directly.
        val id = n0.storage.attachments.importAttachment(ByteArrayInputStream(fakeAttachment()))

        // Get node one to run a protocol to fetch it and insert it.
        val f1 = n1.smm.add("tests.fetch1", FetchAttachmentsProtocol(setOf(id), n0.net.myAddress))
        network.runNetwork()
        assertEquals(0, f1.get().fromDisk.size)

        // Verify it was inserted into node one's store.
        val attachment = n1.storage.attachments.openAttachment(id)!!
        assertEquals(id, attachment.open().readBytes().sha256())

        // Shut down node zero and ensure node one can still resolve the attachment.
        n0.stop()

        val response: FetchDataProtocol.Result<Attachment> = n1.smm.add("tests.fetch1", FetchAttachmentsProtocol(setOf(id), n0.net.myAddress)).get()
        assertEquals(attachment, response.fromDisk[0])
    }

    @Test
    fun `missing`() {
        val (n0, n1) = network.createTwoNodes()

        // Get node one to fetch a non-existent attachment.
        val hash = SecureHash.randomSHA256()
        val f1 = n1.smm.add("tests.fetch2", FetchAttachmentsProtocol(setOf(hash), n0.net.myAddress))
        network.runNetwork()
        val e = assertFailsWith<FetchDataProtocol.HashNotFound> { rootCauseExceptions { f1.get() } }
        assertEquals(hash, e.requested)
    }

    @Test
    fun maliciousResponse() {
        // Make a node that doesn't do sanity checking at load time.
        val n0 = network.createNode(null) { path, config, mock, ts ->
            object : MockNetwork.MockNode(path, config, mock, ts) {
                override fun start(): MockNetwork.MockNode {
                    super.start()
                    (storage.attachments as NodeAttachmentService).checkAttachmentsOnLoad = false
                    return this
                }
            }
        }
        val n1 = network.createNode(n0.legallyIdentifableAddress)

        // Insert an attachment into node zero's store directly.
        val id = n0.storage.attachments.importAttachment(ByteArrayInputStream(fakeAttachment()))

        // Corrupt its store.
        val writer = Files.newByteChannel(network.filesystem.getPath("/nodes/0/attachments/$id"), StandardOpenOption.WRITE)
        writer.write(ByteBuffer.wrap(OpaqueBytes.of(99, 99, 99, 99).bits))
        writer.close()

        // Get n1 to fetch the attachment. Should receive corrupted bytes.
        val f1 = n1.smm.add("tests.fetch1", FetchAttachmentsProtocol(setOf(id), n0.net.myAddress))
        network.runNetwork()
        assertFailsWith<FetchDataProtocol.DownloadedVsRequestedDataMismatch> {
            rootCauseExceptions { f1.get() }
        }
    }
}
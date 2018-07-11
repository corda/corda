package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.internal.FetchAttachmentsFlow
import net.corda.core.internal.FetchDataFlow
import net.corda.core.internal.hash
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AttachmentTests {
    lateinit var mockNet: InternalMockNetwork

    @Before
    fun setUp() {
        mockNet = InternalMockNetwork(emptyList())
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    private fun fakeAttachment(): ByteArray {
        val bs = ByteArrayOutputStream()
        val js = JarOutputStream(bs)
        js.putNextEntry(ZipEntry("file1.txt"))
        js.writer().apply { append("Some useful content"); flush() }
        js.closeEntry()
        js.close()
        return bs.toByteArray()
    }

    @Test
    fun `download and store`() {
        val aliceNode = mockNet.createPartyNode(ALICE_NAME)
        val bobNode = mockNet.createPartyNode(BOB_NAME)
        val alice = aliceNode.info.singleIdentity()
        aliceNode.registerInitiatedFlow(FetchAttachmentsResponse::class.java)
        bobNode.registerInitiatedFlow(FetchAttachmentsResponse::class.java)
        // Insert an attachment into node zero's store directly.
        val id = aliceNode.database.transaction {
            aliceNode.attachments.importAttachment(fakeAttachment().inputStream(), "test", null)
        }

        // Get node one to run a flow to fetch it and insert it.
        mockNet.runNetwork()
        val bobFlow = bobNode.startAttachmentFlow(setOf(id), alice)
        mockNet.runNetwork()
        assertEquals(0, bobFlow.resultFuture.getOrThrow().fromDisk.size)

        // Verify it was inserted into node one's store.
        val attachment = bobNode.database.transaction {
            bobNode.attachments.openAttachment(id)!!
        }

        assertEquals(id, attachment.open().hash())

        // Shut down node zero and ensure node one can still resolve the attachment.
        aliceNode.dispose()

        val response: FetchDataFlow.Result<Attachment> = bobNode.startAttachmentFlow(setOf(id), alice).resultFuture.getOrThrow()
        assertEquals(attachment, response.fromDisk[0])
    }

    @Test
    fun missing() {
        val aliceNode = mockNet.createPartyNode(ALICE_NAME)
        val bobNode = mockNet.createPartyNode(BOB_NAME)
        aliceNode.registerInitiatedFlow(FetchAttachmentsResponse::class.java)
        bobNode.registerInitiatedFlow(FetchAttachmentsResponse::class.java)
        // Get node one to fetch a non-existent attachment.
        val hash = SecureHash.randomSHA256()
        val alice = aliceNode.info.singleIdentity()
        val bobFlow = bobNode.startAttachmentFlow(setOf(hash), alice)
        mockNet.runNetwork()
        val e = assertFailsWith<FetchDataFlow.HashNotFound> { bobFlow.resultFuture.getOrThrow() }
        assertEquals(hash, e.requested)
    }

    @Test
    fun maliciousResponse() {
        // Make a node that doesn't do sanity checking at load time.
        val aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME), nodeFactory = { args ->
            object : InternalMockNetwork.MockNode(args) {
                override fun start() = super.start().apply { attachments.checkAttachmentsOnLoad = false }
            }
        })
        val bobNode = mockNet.createNode(InternalMockNodeParameters(legalName = BOB_NAME))
        val alice = aliceNode.info.singleIdentity()
        aliceNode.registerInitiatedFlow(FetchAttachmentsResponse::class.java)
        bobNode.registerInitiatedFlow(FetchAttachmentsResponse::class.java)
        val attachment = fakeAttachment()
        // Insert an attachment into node zero's store directly.
        val id = aliceNode.database.transaction {
            aliceNode.attachments.importAttachment(attachment.inputStream(), "test", null)
        }

        // Corrupt its store.
        val corruptBytes = "arggghhhh".toByteArray()
        System.arraycopy(corruptBytes, 0, attachment, 0, corruptBytes.size)

        val corruptAttachment = NodeAttachmentService.DBAttachment(attId = id.toString(), content = attachment)
        aliceNode.database.transaction {
            session.update(corruptAttachment)
        }

        // Get n1 to fetch the attachment. Should receive corrupted bytes.
        mockNet.runNetwork()
        val bobFlow = bobNode.startAttachmentFlow(setOf(id), alice)
        mockNet.runNetwork()
        assertFailsWith<FetchDataFlow.DownloadedVsRequestedDataMismatch> { bobFlow.resultFuture.getOrThrow() }
    }

    private fun StartedNode<*>.startAttachmentFlow(hashes: Set<SecureHash>, otherSide: Party) = services.startFlow(InitiatingFetchAttachmentsFlow(otherSide, hashes))

    @InitiatingFlow
    private class InitiatingFetchAttachmentsFlow(val otherSide: Party, val hashes: Set<SecureHash>) : FlowLogic<FetchDataFlow.Result<Attachment>>() {
        @Suspendable
        override fun call(): FetchDataFlow.Result<Attachment> {
            val session = initiateFlow(otherSide)
            return subFlow(FetchAttachmentsFlow(hashes, session))
        }
    }

    @InitiatedBy(InitiatingFetchAttachmentsFlow::class)
    private class FetchAttachmentsResponse(val otherSideSession: FlowSession) : FlowLogic<Void?>() {
        @Suspendable
        override fun call() = subFlow(TestDataVendingFlow(otherSideSession))
    }
}

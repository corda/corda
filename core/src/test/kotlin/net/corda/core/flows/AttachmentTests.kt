package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assert
import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.matchers.flow.willReturn
import net.corda.core.flows.matchers.flow.willThrow
import net.corda.core.flows.mixins.WithMockNet
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.FetchAttachmentsFlow
import net.corda.core.internal.FetchDataFlow
import net.corda.core.internal.hash
import net.corda.node.internal.StartedNode
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import org.junit.AfterClass
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

class AttachmentTests : WithMockNet {
    companion object {
        val classMockNet = InternalMockNetwork()

        @JvmStatic
        @AfterClass
        fun cleanUp() = classMockNet.stopNodes()
    }

    override val mockNet = classMockNet

    // Test nodes
    private val aliceNode = makeNode(ALICE_NAME)
    private val bobNode = makeNode(BOB_NAME)
    private val alice = aliceNode.info.singleIdentity()

    @Test
    fun `download and store`() {
        // Insert an attachment into node zero's store directly.
        val id = aliceNode.importAttachment(fakeAttachment())

        // Get node one to run a flow to fetch it and insert it.
        assert.that(
            bobNode.startAttachmentFlow(id, alice),
                willReturn(noAttachments()))

        // Verify it was inserted into node one's store.
        val attachment = bobNode.getAttachmentWithId(id)
        assert.that(attachment, hashesTo(id))

        // Shut down node zero and ensure node one can still resolve the attachment.
        aliceNode.dispose()

        assert.that(
            bobNode.startAttachmentFlow(id, alice),
                willReturn(soleAttachment(attachment)))
    }

    @Test
    fun missing() {
        val hash: SecureHash = SecureHash.randomSHA256()

        // Get node one to fetch a non-existent attachment.
        assert.that(
            bobNode.startAttachmentFlow(hash, alice),
                willThrow(withRequestedHash(hash)))
    }

    fun withRequestedHash(expected: SecureHash) = has(
            "requested hash",
            FetchDataFlow.HashNotFound::requested,
            equalTo(expected))

    @Test
    fun maliciousResponse() {
        // Make a node that doesn't do sanity checking at load time.
        val badAliceNode = makeBadNode(ALICE_NAME)
        val badAlice = badAliceNode.info.singleIdentity()

        // Insert an attachment into node zero's store directly.
        val attachment = fakeAttachment()
        val id = badAliceNode.importAttachment(attachment)

        // Corrupt its store.
        val corruptBytes = "arggghhhh".toByteArray()
        System.arraycopy(corruptBytes, 0, attachment, 0, corruptBytes.size)

        val corruptAttachment = NodeAttachmentService.DBAttachment(attId = id.toString(), content = attachment)
        badAliceNode.updateAttachment(corruptAttachment)

        // Get n1 to fetch the attachment. Should receive corrupted bytes.
        assert.that(
            bobNode.startAttachmentFlow(id, badAlice),
                willThrow<FetchDataFlow.DownloadedVsRequestedDataMismatch>()
        )
    }

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

    //region Generators
    override fun makeNode(name: CordaX500Name) =
        mockNet.createPartyNode(randomise(name)).apply {
            registerInitiatedFlow(FetchAttachmentsResponse::class.java)
        }

    // Makes a node that doesn't do sanity checking at load time.
    private fun makeBadNode(name: CordaX500Name) = mockNet.createNode(
            InternalMockNodeParameters(legalName = randomise(name)),
            nodeFactory = { args ->
                object : InternalMockNetwork.MockNode(args) {
                    override fun start() = super.start().apply { attachments.checkAttachmentsOnLoad = false }
                }
            }).apply { registerInitiatedFlow(FetchAttachmentsResponse::class.java) }

    private fun fakeAttachment(): ByteArray =
        ByteArrayOutputStream().use { baos ->
            JarOutputStream(baos).use { jos ->
                jos.putNextEntry(ZipEntry("file1.txt"))
                jos.writer().apply {
                    append("Some useful content")
                    flush()
                }
                jos.closeEntry()
            }
            baos.toByteArray()
        }
    //endregion

    //region Operations
    private fun StartedNode<*>.importAttachment(attachment: ByteArray) =
        attachments.importAttachment(attachment.inputStream(), "test", null)
            .andRunNetwork()

    private fun StartedNode<*>.updateAttachment(attachment:  NodeAttachmentService.DBAttachment) = database.transaction {
        session.update(attachment)
    }.andRunNetwork()

    private fun StartedNode<*>.startAttachmentFlow(hash: SecureHash, otherSide: Party) = startFlowAndRunNetwork(
            InitiatingFetchAttachmentsFlow(otherSide, setOf(hash)))

    private fun StartedNode<*>.getAttachmentWithId(id: SecureHash) =
        attachments.openAttachment(id)!!
    //endregion

    //region Matchers
    private fun noAttachments() = has(FetchDataFlow.Result<Attachment>::fromDisk, isEmpty)
    private fun soleAttachment(attachment: Attachment) = has(FetchDataFlow.Result<Attachment>::fromDisk,
            hasSize(equalTo(1)) and
                    hasElement(attachment))

    private fun hashesTo(hash: SecureHash) = has<Attachment, SecureHash>(
        "hash",
        { it.open().hash() },
        equalTo(hash))
    //endregion

}

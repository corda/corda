package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.identity.Party
import net.corda.core.internal.FetchAttachmentsFlow
import net.corda.core.internal.FetchDataFlow
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.node.utilities.DatabaseTransactionManager
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AttachmentTests {
    lateinit var mockNet: MockNetwork

    @Before
    fun setUp() {
        mockNet = MockNetwork()
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
        mockNet.createNotaryNode()
        val aliceNode = mockNet.createPartyNode(ALICE.name)
        val bobNode = mockNet.createPartyNode(BOB.name)

        // Ensure that registration was successful before progressing any further
        mockNet.runNetwork()
        aliceNode.internals.ensureRegistered()

        aliceNode.internals.registerInitiatedFlow(FetchAttachmentsResponse::class.java)
        bobNode.internals.registerInitiatedFlow(FetchAttachmentsResponse::class.java)

        // Insert an attachment into node zero's store directly.
        val id = aliceNode.database.transaction {
            aliceNode.attachments.importAttachment(ByteArrayInputStream(fakeAttachment()))
        }

        // Get node one to run a flow to fetch it and insert it.
        mockNet.runNetwork()
        val bobFlow = bobNode.startAttachmentFlow(setOf(id), aliceNode.info.chooseIdentity())
        mockNet.runNetwork()
        assertEquals(0, bobFlow.resultFuture.getOrThrow().fromDisk.size)

        // Verify it was inserted into node one's store.
        val attachment = bobNode.database.transaction {
            bobNode.attachments.openAttachment(id)!!
        }

        assertEquals(id, attachment.open().readBytes().sha256())

        // Shut down node zero and ensure node one can still resolve the attachment.
        aliceNode.dispose()

        val response: FetchDataFlow.Result<Attachment> = bobNode.startAttachmentFlow(setOf(id), aliceNode.info.chooseIdentity()).resultFuture.getOrThrow()
        assertEquals(attachment, response.fromDisk[0])
    }

    @Test
    fun `missing`() {
        mockNet.createNotaryNode()
        val aliceNode = mockNet.createPartyNode(ALICE.name)
        val bobNode = mockNet.createPartyNode(BOB.name)

        // Ensure that registration was successful before progressing any further
        mockNet.runNetwork()
        aliceNode.internals.ensureRegistered()

        aliceNode.internals.registerInitiatedFlow(FetchAttachmentsResponse::class.java)
        bobNode.internals.registerInitiatedFlow(FetchAttachmentsResponse::class.java)

        // Get node one to fetch a non-existent attachment.
        val hash = SecureHash.randomSHA256()
        mockNet.runNetwork()
        val bobFlow = bobNode.startAttachmentFlow(setOf(hash), aliceNode.info.chooseIdentity())
        mockNet.runNetwork()
        val e = assertFailsWith<FetchDataFlow.HashNotFound> { bobFlow.resultFuture.getOrThrow() }
        assertEquals(hash, e.requested)
    }

    @Test
    fun `malicious response`() {
        // Make a node that doesn't do sanity checking at load time.
        val aliceNode = mockNet.createNotaryNode(legalName = ALICE.name, nodeFactory = object : MockNetwork.Factory<MockNetwork.MockNode> {
            override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                                id: Int, notaryIdentity: Pair<ServiceInfo, KeyPair>?,
                                entropyRoot: BigInteger): MockNetwork.MockNode {
                return object : MockNetwork.MockNode(config, network, networkMapAddr, id, notaryIdentity, entropyRoot) {
                    override fun start() = super.start().apply { attachments.checkAttachmentsOnLoad = false }
                }
            }
        }, validating = false)
        val bobNode = mockNet.createNode(legalName = BOB.name)

        // Ensure that registration was successful before progressing any further
        mockNet.runNetwork()
        aliceNode.internals.ensureRegistered()

        aliceNode.internals.registerInitiatedFlow(FetchAttachmentsResponse::class.java)
        bobNode.internals.registerInitiatedFlow(FetchAttachmentsResponse::class.java)

        val attachment = fakeAttachment()
        // Insert an attachment into node zero's store directly.
        val id = aliceNode.database.transaction {
            aliceNode.attachments.importAttachment(ByteArrayInputStream(attachment))
        }

        // Corrupt its store.
        val corruptBytes = "arggghhhh".toByteArray()
        System.arraycopy(corruptBytes, 0, attachment, 0, corruptBytes.size)

        val corruptAttachment = NodeAttachmentService.DBAttachment(attId = id.toString(), content = attachment)
        aliceNode.database.transaction {
            DatabaseTransactionManager.current().session.update(corruptAttachment)
        }

        // Get n1 to fetch the attachment. Should receive corrupted bytes.
        mockNet.runNetwork()
        val bobFlow = bobNode.startAttachmentFlow(setOf(id), aliceNode.info.chooseIdentity())
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

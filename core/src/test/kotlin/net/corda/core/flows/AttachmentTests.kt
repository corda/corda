package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.identity.Party
import net.corda.core.internal.FetchAttachmentsFlow
import net.corda.core.internal.FetchDataFlow
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.services.ServiceInfo
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.node.utilities.DatabaseTransactionManager
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

    fun fakeAttachment(): ByteArray {
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
        val nodes = mockNet.createSomeNodes(2)
        val n0 = nodes.partyNodes[0]
        val n1 = nodes.partyNodes[1]
        n0.registerInitiatedFlow(FetchAttachmentsResponse::class.java)
        n1.registerInitiatedFlow(FetchAttachmentsResponse::class.java)

        // Insert an attachment into node zero's store directly.
        val id = n0.database.transaction {
            n0.attachments.importAttachment(ByteArrayInputStream(fakeAttachment()))
        }

        // Get node one to run a flow to fetch it and insert it.
        mockNet.runNetwork()
        val f1 = n1.startAttachmentFlow(setOf(id), n0.info.legalIdentity)
        mockNet.runNetwork()
        assertEquals(0, f1.resultFuture.getOrThrow().fromDisk.size)

        // Verify it was inserted into node one's store.
        val attachment = n1.database.transaction {
            n1.attachments.openAttachment(id)!!
        }

        assertEquals(id, attachment.open().readBytes().sha256())

        // Shut down node zero and ensure node one can still resolve the attachment.
        n0.stop()

        val response: FetchDataFlow.Result<Attachment> = n1.startAttachmentFlow(setOf(id), n0.info.legalIdentity).resultFuture.getOrThrow()
        assertEquals(attachment, response.fromDisk[0])
    }

    @Test
    fun `missing`() {
        val nodes = mockNet.createSomeNodes(2)
        val n0 = nodes.partyNodes[0]
        val n1 = nodes.partyNodes[1]
        n0.registerInitiatedFlow(FetchAttachmentsResponse::class.java)
        n1.registerInitiatedFlow(FetchAttachmentsResponse::class.java)

        // Get node one to fetch a non-existent attachment.
        val hash = SecureHash.randomSHA256()
        mockNet.runNetwork()
        val f1 = n1.startAttachmentFlow(setOf(hash), n0.info.legalIdentity)
        mockNet.runNetwork()
        val e = assertFailsWith<FetchDataFlow.HashNotFound> { f1.resultFuture.getOrThrow() }
        assertEquals(hash, e.requested)
    }

    @Test
    fun `malicious response`() {
        // Make a node that doesn't do sanity checking at load time.
        val n0 = mockNet.createNode(nodeFactory = object : MockNetwork.Factory<MockNetwork.MockNode> {
            override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                                advertisedServices: Set<ServiceInfo>, id: Int,
                                overrideServices: Map<ServiceInfo, KeyPair>?,
                                entropyRoot: BigInteger): MockNetwork.MockNode {
                return object : MockNetwork.MockNode(config, network, networkMapAddr, advertisedServices, id, overrideServices, entropyRoot) {
                    override fun start() {
                        super.start()
                        attachments.checkAttachmentsOnLoad = false
                    }
                }
            }
        }, advertisedServices = *arrayOf(ServiceInfo(NetworkMapService.type), ServiceInfo(SimpleNotaryService.type)))
        val n1 = mockNet.createNode(n0.network.myAddress)

        n0.registerInitiatedFlow(FetchAttachmentsResponse::class.java)
        n1.registerInitiatedFlow(FetchAttachmentsResponse::class.java)

        val attachment = fakeAttachment()
        // Insert an attachment into node zero's store directly.
        val id = n0.database.transaction {
            n0.attachments.importAttachment(ByteArrayInputStream(attachment))
        }

        // Corrupt its store.
        val corruptBytes = "arggghhhh".toByteArray()
        System.arraycopy(corruptBytes, 0, attachment, 0, corruptBytes.size)

        val corruptAttachment = NodeAttachmentService.DBAttachment(attId = id.toString(), content = attachment)
        n0.database.transaction {
            DatabaseTransactionManager.current().session.update(corruptAttachment)
        }

        // Get n1 to fetch the attachment. Should receive corrupted bytes.
        mockNet.runNetwork()
        val f1 = n1.startAttachmentFlow(setOf(id), n0.info.legalIdentity)
        mockNet.runNetwork()
        assertFailsWith<FetchDataFlow.DownloadedVsRequestedDataMismatch> { f1.resultFuture.getOrThrow() }
    }

    private fun MockNetwork.MockNode.startAttachmentFlow(hashes: Set<SecureHash>, otherSide: Party) = services.startFlow(InitiatingFetchAttachmentsFlow(otherSide, hashes))

    @InitiatingFlow
    private class InitiatingFetchAttachmentsFlow(val otherSide: Party, val hashes: Set<SecureHash>) : FlowLogic<FetchDataFlow.Result<Attachment>>() {
        @Suspendable
        override fun call(): FetchDataFlow.Result<Attachment> = subFlow(FetchAttachmentsFlow(hashes, otherSide))
    }

    @InitiatedBy(InitiatingFetchAttachmentsFlow::class)
    private class FetchAttachmentsResponse(val otherSide: Party) : FlowLogic<Void?>() {
        @Suspendable
        override fun call() = subFlow(TestDataVendingFlow(otherSide))
    }
}

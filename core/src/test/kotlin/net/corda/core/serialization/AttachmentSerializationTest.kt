package net.corda.core.serialization

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.TestDataVendingFlow
import net.corda.core.internal.FetchAttachmentsFlow
import net.corda.core.internal.FetchDataFlow
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.internal.InitiatedFlowFactory
import net.corda.node.internal.StartedNode
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.node.utilities.DatabaseTransactionManager
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets.UTF_8
import java.security.KeyPair
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals

private fun createAttachmentData(content: String) = ByteArrayOutputStream().apply {
    ZipOutputStream(this).use {
        with(it) {
            putNextEntry(ZipEntry("content"))
            write(content.toByteArray(UTF_8))
        }
    }
}.toByteArray()

private fun Attachment.extractContent() = ByteArrayOutputStream().apply { extractFile("content", this) }.toString(UTF_8.name())

private fun StartedNode<*>.saveAttachment(content: String) = database.transaction {
    attachments.importAttachment(createAttachmentData(content).inputStream())
}

private fun StartedNode<*>.hackAttachment(attachmentId: SecureHash, content: String) = database.transaction {
    updateAttachment(attachmentId, createAttachmentData(content))
}

/**
 * @see NodeAttachmentService.importAttachment
 */
private fun updateAttachment(attachmentId: SecureHash, data: ByteArray) {
    val session = DatabaseTransactionManager.current().session
    val attachment = session.get<NodeAttachmentService.DBAttachment>(NodeAttachmentService.DBAttachment::class.java, attachmentId.toString())
    attachment?.let {
        attachment.content = data
        session.save(attachment)
    }
}

class AttachmentSerializationTest {
    private lateinit var mockNet: MockNetwork
    private lateinit var server: StartedNode<MockNetwork.MockNode>
    private lateinit var client: StartedNode<MockNetwork.MockNode>

    @Before
    fun setUp() {
        mockNet = MockNetwork()
        server = mockNet.createNode()
        client = mockNet.createNode()
        client.internals.disableDBCloseOnStop() // Otherwise the in-memory database may disappear (taking the checkpoint with it) while we reboot the client.
        mockNet.runNetwork()
        server.internals.ensureRegistered()
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    private class ServerLogic(private val clientSession: FlowSession, private val sendData: Boolean) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            if (sendData) {
                subFlow(TestDataVendingFlow(clientSession))
            }
            clientSession.receive<String>().unwrap { assertEquals("ping one", it) }
            clientSession.sendAndReceive<String>("pong").unwrap { assertEquals("ping two", it) }
        }
    }

    private class ClientResult(internal val attachmentContent: String)

    @InitiatingFlow
    private abstract class ClientLogic(server: StartedNode<*>) : FlowLogic<ClientResult>() {
        internal val server = server.info.chooseIdentity()

        @Suspendable
        internal fun communicate(serverSession: FlowSession) {
            serverSession.sendAndReceive<String>("ping one").unwrap { assertEquals("pong", it) }
            serverSession.send("ping two")
        }

        @Suspendable
        override fun call() = ClientResult(getAttachmentContent())

        @Suspendable // This annotation is required by the instrumentation verifier.
        internal abstract fun getAttachmentContent(): String
    }

    private class CustomAttachment(override val id: SecureHash, internal val customContent: String) : Attachment {
        override fun open() = throw UnsupportedOperationException("Not implemented.")
        override val signers get() = throw UnsupportedOperationException()
    }

    private class CustomAttachmentLogic(server: StartedNode<*>, private val attachmentId: SecureHash, private val customContent: String) : ClientLogic(server) {
        @Suspendable
        override fun getAttachmentContent(): String {
            val customAttachment = CustomAttachment(attachmentId, customContent)
            val session = initiateFlow(server)
            communicate(session)
            return customAttachment.customContent
        }
    }

    private class OpenAttachmentLogic(server: StartedNode<*>, private val attachmentId: SecureHash) : ClientLogic(server) {
        @Suspendable
        override fun getAttachmentContent(): String {
            val localAttachment = serviceHub.attachments.openAttachment(attachmentId)!!
            val session = initiateFlow(server)
            communicate(session)
            return localAttachment.extractContent()
        }
    }

    private class FetchAttachmentLogic(server: StartedNode<*>, private val attachmentId: SecureHash) : ClientLogic(server) {
        @Suspendable
        override fun getAttachmentContent(): String {
            val serverSession = initiateFlow(server)
            val (downloadedAttachment) = subFlow(FetchAttachmentsFlow(setOf(attachmentId), serverSession)).downloaded
            serverSession.send(FetchDataFlow.Request.End)
            communicate(serverSession)
            return downloadedAttachment.extractContent()
        }
    }

    private fun launchFlow(clientLogic: ClientLogic, rounds: Int, sendData: Boolean = false) {
        server.internals.internalRegisterFlowFactory(
                ClientLogic::class.java,
                InitiatedFlowFactory.Core { ServerLogic(it, sendData) },
                ServerLogic::class.java,
                track = false)
        client.services.startFlow(clientLogic)
        mockNet.runNetwork(rounds)
    }

    private fun rebootClientAndGetAttachmentContent(checkAttachmentsOnLoad: Boolean = true): String {
        client.dispose()
        client = mockNet.createNode(client.internals.id, object : MockNetwork.Factory<MockNetwork.MockNode> {
            override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                                advertisedServices: Set<ServiceInfo>, id: Int, notaryIdentity: Pair<ServiceInfo, KeyPair>?,
                                entropyRoot: BigInteger): MockNetwork.MockNode {
                return object : MockNetwork.MockNode(config, network, networkMapAddr, advertisedServices, id, notaryIdentity, entropyRoot) {
                    override fun start() = super.start().apply { attachments.checkAttachmentsOnLoad = checkAttachmentsOnLoad }
                }
            }
        })
        return (client.smm.allStateMachines[0].stateMachine.resultFuture.apply { mockNet.runNetwork() }.getOrThrow() as ClientResult).attachmentContent
    }

    @Test
    fun `custom (and non-persisted) attachment should be saved in checkpoint`() {
        val attachmentId = SecureHash.sha256("any old data")
        launchFlow(CustomAttachmentLogic(server, attachmentId, "custom"), 1)
        assertEquals("custom", rebootClientAndGetAttachmentContent())
    }

    @Test
    fun `custom attachment should be saved in checkpoint even if its data was persisted`() {
        val attachmentId = client.saveAttachment("genuine")
        launchFlow(CustomAttachmentLogic(server, attachmentId, "custom"), 1)
        client.hackAttachment(attachmentId, "hacked") // Should not be reloaded, checkAttachmentsOnLoad may cause next line to blow up if client attempts it.
        assertEquals("custom", rebootClientAndGetAttachmentContent())
    }

    @Test
    fun `only the hash of a regular attachment should be saved in checkpoint`() {
        val attachmentId = client.saveAttachment("genuine")
        client.attachments.checkAttachmentsOnLoad = false // Cached by AttachmentImpl.
        launchFlow(OpenAttachmentLogic(server, attachmentId), 1)
        client.hackAttachment(attachmentId, "hacked")
        assertEquals("hacked", rebootClientAndGetAttachmentContent(false)) // Pass in false to allow non-genuine data to be loaded.
    }

    @Test
    fun `only the hash of a FetchAttachmentsFlow attachment should be saved in checkpoint`() {
        val attachmentId = server.saveAttachment("genuine")
        launchFlow(FetchAttachmentLogic(server, attachmentId), 2, sendData = true)
        client.hackAttachment(attachmentId, "hacked")
        assertEquals("hacked", rebootClientAndGetAttachmentContent(false))
    }
}

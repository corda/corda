package net.corda.core.serialization

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.getOrThrow
import net.corda.core.identity.Party
import net.corda.core.messaging.RPCOps
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.services.ServiceInfo
import net.corda.core.utilities.unwrap
import net.corda.flows.FetchAttachmentsFlow
import net.corda.node.internal.InitiatedFlowFactory
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.node.services.persistence.schemas.requery.AttachmentEntity
import net.corda.node.services.statemachine.SessionInit
import net.corda.node.utilities.transaction
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

private fun MockNetwork.MockNode.saveAttachment(content: String) = database.transaction {
    attachments.importAttachment(createAttachmentData(content).inputStream())
}
private fun MockNetwork.MockNode.hackAttachment(attachmentId: SecureHash, content: String) = database.transaction {
    attachments.updateAttachment(attachmentId, createAttachmentData(content))
}

/**
 * @see NodeAttachmentService.importAttachment
 */
private fun NodeAttachmentService.updateAttachment(attachmentId: SecureHash, data: ByteArray) {
    with(session) {
        withTransaction {
            update(AttachmentEntity().apply {
                attId = attachmentId
                content = data
            })
        }
    }
}

class AttachmentSerializationTest {
    private lateinit var mockNet: MockNetwork
    private lateinit var server: MockNetwork.MockNode
    private lateinit var client: MockNetwork.MockNode

    @Before
    fun setUp() {
        mockNet = MockNetwork()
        server = mockNet.createNode(advertisedServices = ServiceInfo(NetworkMapService.type))
        client = mockNet.createNode(server.network.myAddress)
        client.disableDBCloseOnStop() // Otherwise the in-memory database may disappear (taking the checkpoint with it) while we reboot the client.
        mockNet.runNetwork()
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    private class ServerLogic(private val client: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            receive<String>(client).unwrap { assertEquals("ping one", it) }
            sendAndReceive<String>(client, "pong").unwrap { assertEquals("ping two", it) }
        }
    }

    private class ClientResult(internal val attachmentContent: String)

    @InitiatingFlow
    private abstract class ClientLogic(server: MockNetwork.MockNode) : FlowLogic<ClientResult>() {
        internal val server = server.info.legalIdentity

        @Suspendable
        internal fun communicate() {
            sendAndReceive<String>(server, "ping one").unwrap { assertEquals("pong", it) }
            send(server, "ping two")
        }

        @Suspendable
        override fun call() = ClientResult(getAttachmentContent())

        @Suspendable // This annotation is required by the instrumentation verifier.
        internal abstract fun getAttachmentContent(): String
    }

    private class CustomAttachment(override val id: SecureHash, internal val customContent: String) : Attachment {
        override fun open() = throw UnsupportedOperationException("Not implemented.")
    }

    private class CustomAttachmentLogic(server: MockNetwork.MockNode, private val attachmentId: SecureHash, private val customContent: String) : ClientLogic(server) {
        @Suspendable
        override fun getAttachmentContent(): String {
            val customAttachment = CustomAttachment(attachmentId, customContent)
            communicate()
            return customAttachment.customContent
        }
    }

    private class OpenAttachmentLogic(server: MockNetwork.MockNode, private val attachmentId: SecureHash) : ClientLogic(server) {
        @Suspendable
        override fun getAttachmentContent(): String {
            val localAttachment = serviceHub.attachments.openAttachment(attachmentId)!!
            communicate()
            return localAttachment.extractContent()
        }
    }

    private class FetchAttachmentLogic(server: MockNetwork.MockNode, private val attachmentId: SecureHash) : ClientLogic(server) {
        @Suspendable
        override fun getAttachmentContent(): String {
            val (downloadedAttachment) = subFlow(FetchAttachmentsFlow(setOf(attachmentId), server)).downloaded
            communicate()
            return downloadedAttachment.extractContent()
        }
    }

    private fun launchFlow(clientLogic: ClientLogic, rounds: Int) {
        server.internalRegisterFlowFactory(ClientLogic::class.java, object : InitiatedFlowFactory<ServerLogic> {
            override fun createFlow(platformVersion: Int, otherParty: Party, sessionInit: SessionInit): ServerLogic {
                return ServerLogic(otherParty)
            }
        }, ServerLogic::class.java, track = false)
        client.services.startFlow(clientLogic)
        mockNet.runNetwork(rounds)
    }

    private fun rebootClientAndGetAttachmentContent(checkAttachmentsOnLoad: Boolean = true): String {
        client.stop()
        client = mockNet.createNode(server.network.myAddress, client.id, object : MockNetwork.Factory {
            override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?, advertisedServices: Set<ServiceInfo>, id: Int, overrideServices: Map<ServiceInfo, KeyPair>?, entropyRoot: BigInteger): MockNetwork.MockNode {
                return object : MockNetwork.MockNode(config, network, networkMapAddr, advertisedServices, id, overrideServices, entropyRoot) {
                    override fun startMessagingService(rpcOps: RPCOps) {
                        attachments.checkAttachmentsOnLoad = checkAttachmentsOnLoad
                        super.startMessagingService(rpcOps)
                    }
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
        launchFlow(FetchAttachmentLogic(server, attachmentId), 2)
        client.hackAttachment(attachmentId, "hacked")
        assertEquals("hacked", rebootClientAndGetAttachmentContent(false))
    }
}

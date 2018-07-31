/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.serialization

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.TestDataVendingFlow
import net.corda.core.identity.Party
import net.corda.core.internal.FetchAttachmentsFlow
import net.corda.core.internal.FetchDataFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.internal.InitiatedFlowFactory
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.nodeapi.internal.persistence.currentDBSession
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
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

private fun
        Attachment.extractContent() = ByteArrayOutputStream().apply { extractFile("content", this) }.toString(UTF_8.name())

@Suppress("deprecation")
private fun TestStartedNode.saveAttachment(content: String) = database.transaction {
    attachments.importAttachment(createAttachmentData(content).inputStream())
}

private fun TestStartedNode.hackAttachment(attachmentId: SecureHash, content: String) = database.transaction {
    updateAttachment(attachmentId, createAttachmentData(content))
}

/**
 * @see NodeAttachmentService.importAttachment
 */
private fun updateAttachment(attachmentId: SecureHash, data: ByteArray) {
    val session = currentDBSession()
    val attachment = session.get<NodeAttachmentService.DBAttachment>(NodeAttachmentService.DBAttachment::class.java, attachmentId.toString())
    attachment?.let {
        attachment.content = data
        session.save(attachment)
    }
}

class AttachmentSerializationTest {
    private lateinit var mockNet: InternalMockNetwork
    private lateinit var server: TestStartedNode
    private lateinit var client: TestStartedNode
    private lateinit var serverIdentity: Party

    @Before
    fun setUp() {
        mockNet = InternalMockNetwork()
        server = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
        client = mockNet.createNode(InternalMockNodeParameters(legalName = BOB_NAME))
        client.internals.disableDBCloseOnStop() // Otherwise the in-memory database may disappear (taking the checkpoint with it) while we reboot the client.
        mockNet.runNetwork()
        serverIdentity = server.info.singleIdentity()
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
    private abstract class ClientLogic(val serverIdentity: Party) : FlowLogic<ClientResult>() {
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
        override val size get() = throw UnsupportedOperationException()
    }

    private class CustomAttachmentLogic(serverIdentity: Party, private val attachmentId: SecureHash, private val customContent: String) : ClientLogic(serverIdentity) {
        @Suspendable
        override fun getAttachmentContent(): String {
            val customAttachment = CustomAttachment(attachmentId, customContent)
            val session = initiateFlow(serverIdentity)
            communicate(session)
            return customAttachment.customContent
        }
    }

    private class OpenAttachmentLogic(serverIdentity: Party, private val attachmentId: SecureHash) : ClientLogic(serverIdentity) {
        @Suspendable
        override fun getAttachmentContent(): String {
            val localAttachment = serviceHub.attachments.openAttachment(attachmentId)!!
            val session = initiateFlow(serverIdentity)
            communicate(session)
            return localAttachment.extractContent()
        }
    }

    private class FetchAttachmentLogic(serverIdentity: Party, private val attachmentId: SecureHash) : ClientLogic(serverIdentity) {
        @Suspendable
        override fun getAttachmentContent(): String {
            val serverSession = initiateFlow(serverIdentity)
            val (downloadedAttachment) = subFlow(FetchAttachmentsFlow(setOf(attachmentId), serverSession)).downloaded
            serverSession.send(FetchDataFlow.Request.End)
            communicate(serverSession)
            return downloadedAttachment.extractContent()
        }
    }

    private fun launchFlow(clientLogic: ClientLogic, rounds: Int, sendData: Boolean = false) {
        server.registerFlowFactory(
                ClientLogic::class.java,
                InitiatedFlowFactory.Core { ServerLogic(it, sendData) },
                ServerLogic::class.java,
                track = false)
        client.services.startFlow(clientLogic)
        mockNet.runNetwork(rounds)
    }

    private fun rebootClientAndGetAttachmentContent(checkAttachmentsOnLoad: Boolean = true): String {
        client = mockNet.restartNode(client) { args, _ ->
            object : InternalMockNetwork.MockNode(args) {
                override fun start() = super.start().apply { attachments.checkAttachmentsOnLoad = checkAttachmentsOnLoad }
            }
        }
        return (client.smm.allStateMachines[0].stateMachine.resultFuture.apply { mockNet.runNetwork() }.getOrThrow() as ClientResult).attachmentContent
    }

    @Test
    fun `custom (and non-persisted) attachment should be saved in checkpoint`() {
        val attachmentId = SecureHash.sha256("any old data")
        launchFlow(CustomAttachmentLogic(serverIdentity, attachmentId, "custom"), 1)
        assertEquals("custom", rebootClientAndGetAttachmentContent())
    }

    @Test
    fun `custom attachment should be saved in checkpoint even if its data was persisted`() {
        val attachmentId = client.saveAttachment("genuine")
        launchFlow(CustomAttachmentLogic(serverIdentity, attachmentId, "custom"), 1)
        client.hackAttachment(attachmentId, "hacked") // Should not be reloaded, checkAttachmentsOnLoad may cause next line to blow up if client attempts it.
        assertEquals("custom", rebootClientAndGetAttachmentContent())
    }

    @Test
    fun `only the hash of a regular attachment should be saved in checkpoint`() {
        val attachmentId = client.saveAttachment("genuine")
        client.attachments.checkAttachmentsOnLoad = false // Cached by AttachmentImpl.
        launchFlow(OpenAttachmentLogic(serverIdentity, attachmentId), 1)
        client.hackAttachment(attachmentId, "hacked")
        assertEquals("hacked", rebootClientAndGetAttachmentContent(false)) // Pass in false to allow non-genuine data to be loaded.
    }

    @Test
    fun `only the hash of a FetchAttachmentsFlow attachment should be saved in checkpoint`() {
        val attachmentId = server.saveAttachment("genuine")
        launchFlow(FetchAttachmentLogic(serverIdentity, attachmentId), 2, sendData = true)
        client.hackAttachment(attachmentId, "hacked")
        assertEquals("hacked", rebootClientAndGetAttachmentContent(false))
    }
}

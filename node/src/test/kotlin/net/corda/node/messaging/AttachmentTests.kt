package net.corda.node.messaging

import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.getOrThrow
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.services.ServiceInfo
import net.corda.flows.FetchAttachmentsFlow
import net.corda.flows.FetchDataFlow
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.database.RequeryConfiguration
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.persistence.schemas.requery.AttachmentEntity
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.node.utilities.transaction
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.makeTestDataSourceProperties
import org.jetbrains.exposed.sql.Database
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.math.BigInteger
import java.security.KeyPair
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AttachmentTests {
    lateinit var mockNet: MockNetwork
    lateinit var dataSource: Closeable
    lateinit var database: Database
    lateinit var configuration: RequeryConfiguration

    @Before
    fun setUp() {
        mockNet = MockNetwork()

        val dataSourceProperties = makeTestDataSourceProperties()

        configuration = RequeryConfiguration(dataSourceProperties)
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
        val (n0, n1) = mockNet.createTwoNodes()

        // Insert an attachment into node zero's store directly.
        val id = n0.database.transaction {
            n0.attachments.importAttachment(ByteArrayInputStream(fakeAttachment()))
        }

        // Get node one to run a flow to fetch it and insert it.
        mockNet.runNetwork()
        val f1 = n1.services.startFlow(FetchAttachmentsFlow(setOf(id), n0.info.legalIdentity))
        mockNet.runNetwork()
        assertEquals(0, f1.resultFuture.getOrThrow().fromDisk.size)

        // Verify it was inserted into node one's store.
        val attachment = n1.database.transaction {
            n1.attachments.openAttachment(id)!!
        }

        assertEquals(id, attachment.open().readBytes().sha256())

        // Shut down node zero and ensure node one can still resolve the attachment.
        n0.stop()

        val response: FetchDataFlow.Result<Attachment> = n1.services.startFlow(FetchAttachmentsFlow(setOf(id), n0.info.legalIdentity)).resultFuture.getOrThrow()
        assertEquals(attachment, response.fromDisk[0])
    }

    @Test
    fun `missing`() {
        val (n0, n1) = mockNet.createTwoNodes()

        // Get node one to fetch a non-existent attachment.
        val hash = SecureHash.randomSHA256()
        mockNet.runNetwork()
        val f1 = n1.services.startFlow(FetchAttachmentsFlow(setOf(hash), n0.info.legalIdentity))
        mockNet.runNetwork()
        val e = assertFailsWith<FetchDataFlow.HashNotFound> { f1.resultFuture.getOrThrow() }
        assertEquals(hash, e.requested)
    }

    @Test
    fun `malicious response`() {
        // Make a node that doesn't do sanity checking at load time.
        val n0 = mockNet.createNode(null, -1, object : MockNetwork.Factory {
            override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                                advertisedServices: Set<ServiceInfo>, id: Int,
                                overrideServices: Map<ServiceInfo, KeyPair>?,
                                entropyRoot: BigInteger): MockNetwork.MockNode {
                return object : MockNetwork.MockNode(config, network, networkMapAddr, advertisedServices, id, overrideServices, entropyRoot) {
                    override fun start(): MockNetwork.MockNode {
                        super.start()
                        attachments.checkAttachmentsOnLoad = false
                        return this
                    }
                }
            }
        }, true, null, null, ServiceInfo(NetworkMapService.type), ServiceInfo(SimpleNotaryService.type))
        val n1 = mockNet.createNode(n0.network.myAddress)

        val attachment = fakeAttachment()
        // Insert an attachment into node zero's store directly.
        val id = n0.database.transaction {
            n0.attachments.importAttachment(ByteArrayInputStream(attachment))
        }

        // Corrupt its store.
        val corruptBytes = "arggghhhh".toByteArray()
        System.arraycopy(corruptBytes, 0, attachment, 0, corruptBytes.size)

        val corruptAttachment = AttachmentEntity()
        corruptAttachment.attId = id
        corruptAttachment.content = attachment
        n0.database.transaction {
            n0.attachments.session.update(corruptAttachment)
        }


        // Get n1 to fetch the attachment. Should receive corrupted bytes.
        mockNet.runNetwork()
        val f1 = n1.services.startFlow(FetchAttachmentsFlow(setOf(id), n0.info.legalIdentity))
        mockNet.runNetwork()
        assertFailsWith<FetchDataFlow.DownloadedVsRequestedDataMismatch> { f1.resultFuture.getOrThrow() }
    }
}

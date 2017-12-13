package net.corda.node.services.persistence

import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.internal.config.User
import net.corda.testing.ALICE_NAME
import net.corda.testing.driver.driver
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.CompletableFuture.supplyAsync
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NodeAttachmentServiceTest {

    lateinit var mockNet: MockNetwork

    @Before
    fun setUp() {
        mockNet = MockNetwork()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    private fun largeAttachment(): File {
        val file = File.createTempFile("prefix", "suffix")
        file.deleteOnExit()

        ZipOutputStream(FileOutputStream(file)).use {
            val zip = it
            zip.setLevel(Deflater.NO_COMPRESSION)
            zip.putNextEntry(ZipEntry("content"))
            repeat(300, {
                zip.write(kotlin.ByteArray(1024 * 1024))
                zip.flush()
            })
        }

        return file
    }

    @Test
    fun `import large attachment`() {
        val aliceNode = mockNet.createPartyNode(ALICE_NAME)
        aliceNode.database.transaction {
            aliceNode.attachments.importAttachment(FileInputStream(largeAttachment()))
        }
    }

//    @Test
//    fun `import large attachment driver`() {
//        driver {
//            val node = startNode(rpcUsers = listOf(User("A", "A", setOf("InvokeRpc.uploadAttachment"))), maximumHeapSize = "200m").get()
//            val senderThread = supplyAsync {
//                node.rpcClientToNode().start("A", "A").use {
//                    it.proxy.uploadAttachment(FileInputStream(largeAttachment()))
//                }
//            }
//
//            senderThread.getOrThrow()
//        }
//    }
}
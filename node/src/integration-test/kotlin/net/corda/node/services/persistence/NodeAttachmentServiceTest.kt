package net.corda.node.services.persistence

import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.internal.config.User
import net.corda.testing.driver.driver
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.CompletableFuture.supplyAsync
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NodeAttachmentServiceTest {

    private fun largeAttachment(): File {
        val file = File.createTempFile("NodeAttachmentServiceTest", null)
        file.deleteOnExit()

        ZipOutputStream(FileOutputStream(file)).use {
            val zip = it
            zip.setLevel(Deflater.NO_COMPRESSION)
            zip.putNextEntry(ZipEntry("content"))
            // create 600m zip file
            repeat(6, {
                zip.write(kotlin.ByteArray(1024 * 1024 * 100))
                zip.flush()
            })
        }

        return file
    }

    @Test
    fun `import large attachment`() {
        driver {
            val node = startNode(rpcUsers = listOf(User("A", "A", setOf("InvokeRpc.uploadAttachment"))), maximumHeapSize = "512m").get()
            val senderThread = supplyAsync {
                node.rpcClientToNode().start("A", "A").use {
                    it.proxy.uploadAttachment(FileInputStream(largeAttachment()))
                }
            }
            senderThread.getOrThrow()
        }
    }
}
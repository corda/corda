/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node

import com.google.common.net.HostAndPort
import core.messaging.LegallyIdentifiableNode
import core.messaging.MessagingService
import core.node.services.ArtemisMessagingService
import core.node.servlets.AttachmentDownloadServlet
import core.node.servlets.DataUploadServlet
import core.utilities.loggerFor
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import java.io.RandomAccessFile
import java.lang.management.ManagementFactory
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class ConfigurationException(message: String) : Exception(message)

// TODO: Split this into a regression testing environment

/**
 * A Node manages a standalone server that takes part in the P2P network. It creates the services found in [ServiceHub],
 * loads important data off disk and starts listening for connections.
 *
 * @param dir A [Path] to a location on disk where working files can be found or stored.
 * @param p2pAddr The host and port that this server will use. It can't find out its own external hostname, so you
 *                have to specify that yourself.
 * @param configuration This is typically loaded from a .properties file
 * @param timestamperAddress If null, this node will become a timestamping node, otherwise, it will use that one.
 */
class Node(dir: Path, val p2pAddr: HostAndPort, configuration: NodeConfiguration,
           timestamperAddress: LegallyIdentifiableNode?) : AbstractNode(dir, configuration, timestamperAddress) {
    companion object {
        /** The port that is used by default if none is specified. As you know, 31337 is the most elite number. */
        val DEFAULT_PORT = 31337
    }

    override val log = loggerFor<Node>()

    lateinit var webServer: Server

    // Avoid the lock being garbage collected. We don't really need to release it as the OS will do so for us
    // when our process shuts down, but we try in stop() anyway just to be nice.
    private var nodeFileLock: FileLock? = null

    override fun makeMessagingService(): MessagingService = ArtemisMessagingService(dir, p2pAddr)

    private fun initWebServer(): Server {
        val port = p2pAddr.port + 1   // TODO: Move this into the node config file.
        val server = Server(port)
        val handler = ServletContextHandler()
        handler.setAttribute("node", this)
        handler.addServlet(DataUploadServlet::class.java, "/upload/*")
        handler.addServlet(AttachmentDownloadServlet::class.java, "/attachments/*")
        server.handler = handler
        server.start()
        return server
    }

    override fun start(): Node {
        alreadyRunningNodeCheck()
        super.start()
        webServer = initWebServer()
        // Start up the MQ service.
        (net as ArtemisMessagingService).start()
        return this
    }

    override fun stop() {
        webServer.stop()
        super.stop()
        nodeFileLock!!.release()
    }

    private fun alreadyRunningNodeCheck() {
        // Write out our process ID (which may or may not resemble a UNIX process id - to us it's just a string) to a
        // file that we'll do our best to delete on exit. But if we don't, it'll be overwritten next time. If it already
        // exists, we try to take the file lock first before replacing it and if that fails it means we're being started
        // twice with the same directory: that's a user error and we should bail out.
        val pidPath = dir.resolve("process-id")
        val file = pidPath.toFile()
        if (file.exists()) {
            val f = RandomAccessFile(file, "rw")
            val l = f.channel.tryLock()
            if (l == null) {
                println("It appears there is already a node running with the specified data directory $dir")
                println("Shut that other node down and try again. It may have process ID ${file.readText()}")
                System.exit(1)
            }
            nodeFileLock = l
        }
        val ourProcessID: String = ManagementFactory.getRuntimeMXBean().name.split("@")[0]
        Files.write(pidPath, ourProcessID.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        pidPath.toFile().deleteOnExit()
        if (nodeFileLock == null)
            nodeFileLock = RandomAccessFile(file, "rw").channel.lock()
    }
}
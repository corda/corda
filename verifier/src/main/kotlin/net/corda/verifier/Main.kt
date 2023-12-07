package net.corda.verifier

import net.corda.core.utilities.loggerFor
import org.slf4j.bridge.SLF4JBridgeHandler
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.system.exitProcess

object Main {
    private val log = loggerFor<Main>()

    @JvmStatic
    fun main(args: Array<String>) {
        val port = args[0].toInt()
        val loggingLevel = args[0]
        val baseDirectory = Path.of("").toAbsolutePath()

        initLogging(baseDirectory, loggingLevel)

        log.info("External verifier started; PID ${ProcessHandle.current().pid()}")
        log.info("Node base directory: $baseDirectory")

        try {
            val socket = Socket("localhost", port)
            log.info("Connected to node on port $port")
            val fromNode = DataInputStream(socket.getInputStream())
            val toNode = DataOutputStream(socket.getOutputStream())
            ExternalVerifier(baseDirectory, fromNode, toNode).run()
        } catch (t: Throwable) {
            log.error("Unexpected error which has terminated the verifier", t)
            exitProcess(1)
        }
    }

    private fun initLogging(baseDirectory: Path, loggingLevel: String) {
        System.setProperty("logPath", (baseDirectory / "logs").toString())
        System.setProperty("defaultLogLevel", loggingLevel)

        SLF4JBridgeHandler.removeHandlersForRootLogger() // The default j.u.l config adds a ConsoleHandler.
        SLF4JBridgeHandler.install()
    }
}

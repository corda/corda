package net.corda.verifier

import net.corda.core.utilities.loggerFor
import org.slf4j.bridge.SLF4JBridgeHandler
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.SocketChannel
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.system.exitProcess

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val socketFile = args[0]
        val loggingLevel = args[1]
        val baseDirectory = Path.of("").toAbsolutePath()

        initLogging(baseDirectory, loggingLevel)
        val log = loggerFor<Main>()

        log.info("External verifier started; PID ${ProcessHandle.current().pid()}")
        log.info("Node base directory: $baseDirectory")

        try {
            val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
            channel.connect(UnixDomainSocketAddress.of(socketFile))
            log.info("Connected to node on UNIX domain file $socketFile")
            ExternalVerifier(baseDirectory, channel).run()
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

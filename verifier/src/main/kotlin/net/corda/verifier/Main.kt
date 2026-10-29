package net.corda.verifier

import net.corda.core.internal.verification.ContractGeneration
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
        val generation = ContractGeneration.valueOf(args[2])
        val baseDirectory = Path.of("").toAbsolutePath()

        initLogging(baseDirectory, loggingLevel, generation)
        val log = loggerFor<Main>()

        log.info("External verifier started for $generation transactions; PID ${ProcessHandle.current().pid()}")
        log.info("Node base directory: $baseDirectory")
        val kotlinVersion = KotlinVersion.CURRENT
        if (kotlinVersion.major != generation.kotlinVersion.major || kotlinVersion.minor != generation.kotlinVersion.minor) {
            log.error("This external verifier is running Kotlin $kotlinVersion, which is not compatible with $generation")
            exitProcess(1)
        }

        try {
            val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
            channel.connect(UnixDomainSocketAddress.of(socketFile))
            log.info("Connected to node on UNIX domain file $socketFile")
            ExternalVerifier(channel, generation).run()
        } catch (t: Throwable) {
            log.error("Unexpected error which has terminated the verifier", t)
            exitProcess(1)
        }
    }

    private fun initLogging(baseDirectory: Path, loggingLevel: String, generation: ContractGeneration) {
        System.setProperty("logPath", (baseDirectory / "logs").toString())
        System.setProperty("defaultLogLevel", loggingLevel)
        // Each external verifier has its own log file. The original 4.11 verifier keeps its existing name.
        if (generation != ContractGeneration.CORDA_4_11) {
            System.setProperty("verifierLogNameSuffix", "-${generation.kotlinVersionString}")
        }

        SLF4JBridgeHandler.removeHandlersForRootLogger() // The default j.u.l config adds a ConsoleHandler.
        SLF4JBridgeHandler.install()
    }
}

package net.corda.demobench.pty

import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.UIUtil
import com.jediterm.terminal.ui.settings.SettingsProvider
import com.pty4j.PtyProcess
import net.corda.core.crypto.commonName
import net.corda.core.utilities.loggerFor
import org.bouncycastle.asn1.x500.X500Name
import java.awt.Dimension
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class R3Pty(val name: X500Name, settings: SettingsProvider, dimension: Dimension, val onExit: (Int) -> Unit) : AutoCloseable {
    private companion object {
        val log = loggerFor<R3Pty>()
    }

    private val executor = Executors.newSingleThreadExecutor()

    val terminal = JediTermWidget(dimension, settings)

    val isConnected: Boolean get() = terminal.ttyConnector?.isConnected ?: false

    override fun close() {
        log.info("Closing terminal '{}'", name)
        executor.shutdown()
        terminal.close()
    }

    private fun createTtyConnector(command: Array<String>, environment: Map<String, String>, workingDir: String?): PtyProcessTtyConnector {
        val process = PtyProcess.exec(command, environment, workingDir)

        try {
            return PtyProcessTtyConnector(name.commonName, process, UTF_8)
        } catch (e: Exception) {
            process.destroyForcibly()
            process.waitFor(30, TimeUnit.SECONDS)
            throw e
        }
    }

    @Throws(IOException::class)
    fun run(args: Array<String>, envs: Map<String, String>, workingDir: String?) {
        check(!terminal.isSessionRunning, { "${terminal.sessionName} is already running" })

        val environment = envs.toMutableMap()
        if (!UIUtil.isWindows) {
            environment["TERM"] = "xterm"

            // This environment variable is specific to MacOSX.
            environment.remove("TERM_PROGRAM")
        }

        val connector = createTtyConnector(args, environment, workingDir)

        executor.submit {
            val exitValue = connector.waitFor()
            log.info("Terminal has exited (value={})", exitValue)
            onExit(exitValue)
        }

        val session = terminal.createTerminalSession(connector)
        session.start()
    }

    @Throws(InterruptedException::class)
    fun waitFor(): Int? = terminal.ttyConnector?.waitFor()

}

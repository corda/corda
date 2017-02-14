package net.corda.demobench.pty

import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.ui.*
import com.jediterm.terminal.ui.settings.SettingsProvider
import com.pty4j.PtyProcess
import net.corda.demobench.loggerFor

import java.awt.*
import java.nio.charset.StandardCharsets.UTF_8
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class R3Pty(val name: String, settings: SettingsProvider, dimension: Dimension, val onExit: () -> Unit) : AutoCloseable {
    private companion object {
        val log = loggerFor<R3Pty>()
    }

    private val executor = Executors.newSingleThreadExecutor()

    val terminal = JediTermWidget(dimension, settings)

    override fun close() {
        log.info("Closing terminal '{}'", name)
        executor.shutdown()
        terminal.close()
    }

    private fun createTtyConnector(command: Array<String>, environment: Map<String, String>, workingDir: String?): TtyConnector {
        try {
            val process = PtyProcess.exec(command, environment, workingDir)

            try {
                return PtyProcessTtyConnector(name, process, UTF_8)
            } catch (e: Exception) {
                process.destroyForcibly()
                process.waitFor(30, TimeUnit.SECONDS)
                throw e
            }
        } catch (e: Exception) {
            throw IllegalStateException(e.message, e)
        }
    }

    fun run(args: Array<String>, envs: Map<String, String>, workingDir: String?) {
        if (terminal.isSessionRunning) {
            throw IllegalStateException(terminal.sessionName + " is already running")
        }

        val environment = HashMap<String, String>(envs)
        if (!UIUtil.isWindows) {
            environment.put("TERM", "xterm")
        }

        val connector = createTtyConnector(args, environment, workingDir)

        executor.submit {
            val exitValue = connector.waitFor()
            log.info("Terminal has exited (value={})", exitValue)
            onExit()
        }

        val session = terminal.createTerminalSession(connector)
        session.start()
    }

}

/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.demobench.pty

import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.UIUtil
import com.jediterm.terminal.ui.settings.SettingsProvider
import com.pty4j.PtyProcess
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.contextLogger
import java.awt.Dimension
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

class R3Pty(val name: CordaX500Name, settings: SettingsProvider, dimension: Dimension, val onExit: (Int) -> Unit) : AutoCloseable {
    private companion object {
        private val log = contextLogger()
    }

    private val executor = Executors.newSingleThreadExecutor()

    val terminal = JediTermWidget(dimension, settings)

    val isConnected: Boolean get() = terminal.ttyConnector?.isConnected == true

    override fun close() {
        log.info("Closing terminal '{}'", name)
        executor.shutdown()
        terminal.close()
    }

    private fun createTtyConnector(command: Array<String>, environment: Map<String, String>, workingDir: String?): PtyProcessTtyConnector {
        val process = PtyProcess.exec(command, environment, workingDir)

        try {
            return PtyProcessTtyConnector(name.organisation, process, UTF_8)
        } catch (e: Exception) {
            process.destroyForcibly()
            process.waitFor(30, SECONDS)
            throw e
        }
    }

    @Throws(IOException::class)
    fun run(args: Array<String>, envs: Map<String, String>, workingDir: String?) {
        check(!terminal.isSessionRunning) { "${terminal.sessionName} is already running" }

        val environment = envs.toMutableMap()
        if (!UIUtil.isWindows) {
            environment["TERM"] = "xterm"
            // This, in combination with running on a Mac JetBrains JRE, enables emoji in Mac demobench.
            environment["TERM_PROGRAM"] = "JediTerm"
        }

        val connector = createTtyConnector(args, environment, workingDir)

        executor.submit {
            val exitValue = connector.waitFor()
            log.info("Terminal has exited (value={})", exitValue)
            onExit(exitValue)
        }

        terminal.createTerminalSession(connector).apply { start() }
    }

    @Suppress("unused")
    @Throws(InterruptedException::class)
    fun waitFor(): Int? = terminal.ttyConnector?.waitFor()

}

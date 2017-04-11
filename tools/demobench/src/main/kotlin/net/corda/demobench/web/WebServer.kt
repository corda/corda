package net.corda.demobench.web

import net.corda.core.utilities.loggerFor
import net.corda.demobench.model.NodeConfig
import net.corda.demobench.readErrorLines
import java.io.IOException
import java.util.concurrent.Executors

class WebServer internal constructor(private val webServerController: WebServerController) : AutoCloseable {
    private companion object {
        val log = loggerFor<WebServer>()
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var process: Process? = null

    @Throws(IOException::class)
    fun open(config: NodeConfig, onExit: (NodeConfig) -> Unit) {
        val nodeDir = config.nodeDir.toFile()

        if (!nodeDir.isDirectory) {
            log.warn("Working directory '{}' does not exist.", nodeDir.absolutePath)
            onExit(config)
            return
        }

        try {
            val p = webServerController.process()
                    .directory(nodeDir)
                    .start()
            process = p

            log.info("Launched Web Server for '{}'", config.legalName)

            // Close these streams because no-one is using them.
            safeClose(p.outputStream)
            safeClose(p.inputStream)

            executor.submit {
                val exitValue = p.waitFor()
                val errors = p.readErrorLines()
                process = null

                if (errors.isEmpty()) {
                    log.info("Web Server for '{}' has exited (value={})", config.legalName, exitValue)
                } else {
                    log.error("Web Server for '{}' has exited (value={}, {})", config.legalName, exitValue, errors)
                }

                onExit(config)
            }
        } catch (e: IOException) {
            log.error("Failed to launch Web Server for '{}': {}", config.legalName, e.message)
            onExit(config)
            throw e
        }
    }

    override fun close() {
        executor.shutdown()
        process?.destroy()
    }

    private fun safeClose(c: AutoCloseable) {
        try {
            c.close()
        } catch (e: Exception) {
            log.error("Failed to close stream: '{}'", e.message)
        }
    }

}

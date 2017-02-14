package net.corda.demobench.model

import net.corda.demobench.loggerFor
import java.util.concurrent.Executors

class WebServer(val webServerController: WebServerController) : AutoCloseable {
    private companion object {
        val log = loggerFor<WebServer>()
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var process: Process? = null

    fun open(config: NodeConfig, onExit: (NodeConfig) -> Unit) {
        val nodeDir = config.nodeDir.toFile()

        if (!nodeDir.isDirectory) {
            log.warn("Working directory '{}' does not exist.", nodeDir.absolutePath)
            onExit(config)
            return
        }

        val p = webServerController.process()
            .directory(nodeDir)
            .start()
        process = p

        log.info("Launched Web Server for '{}'", config.legalName)

        // Close these streams because no-one is using them.
        safeClose(p.outputStream)
        safeClose(p.inputStream)
        safeClose(p.errorStream)

        executor.submit {
            val exitValue = p.waitFor()
            process = null

            log.info("Web Server for '{}' has exited (value={})", config.legalName, exitValue)
            onExit(config)
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
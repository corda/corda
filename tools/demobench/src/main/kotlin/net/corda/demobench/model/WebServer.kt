package net.corda.demobench.model

import org.slf4j.LoggerFactory
import java.util.concurrent.Executors

class WebServer(val webServerController: WebServerController) : AutoCloseable {
    private val log = LoggerFactory.getLogger(WebServer::class.java)

    private val executor = Executors.newSingleThreadExecutor()
    private var process: Process? = null

    fun open(config: NodeConfig, onExit: (NodeConfig) -> Unit) {
        val nodeDir = config.nodeDir.toFile()

        if (!nodeDir.isDirectory) {
            log.warn("Working directory '{}' does not exist.", nodeDir.absolutePath)
            onExit(config)
            return
        }

        val p = webServerController.execute(config.nodeDir)
        process = p

        log.info("Launched Web Server for '{}'", config.legalName)

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

}

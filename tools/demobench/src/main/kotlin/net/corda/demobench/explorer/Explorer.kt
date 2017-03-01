package net.corda.demobench.explorer

import java.io.IOException
import java.util.concurrent.Executors
import net.corda.demobench.loggerFor
import net.corda.demobench.model.NodeConfig
import net.corda.demobench.model.forceDirectory

class Explorer(private val explorerController: ExplorerController) : AutoCloseable {
    private companion object {
        val log = loggerFor<Explorer>()
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var process: Process? = null

    @Throws(IOException::class)
    fun open(config: NodeConfig, onExit: (NodeConfig) -> Unit) {
        val explorerDir = config.explorerDir.toFile()

        if (!explorerDir.forceDirectory()) {
            log.warn("Failed to create working directory '{}'", explorerDir.absolutePath)
            onExit(config)
            return
        }

        try {
            val p = explorerController.process(
                    "--host=localhost",
                    "--port=${config.artemisPort}",
                    "--username=${config.users[0].user}",
                    "--password=${config.users[0].password}",
                    "--certificatesDir=${config.ssl.certificatesDirectory}",
                    "--keyStorePassword=${config.ssl.keyStorePassword}",
                    "--trustStorePassword=${config.ssl.trustStorePassword}")
                    .directory(explorerDir)
                    .start()
            process = p

            log.info("Launched Node Explorer for '{}'", config.legalName)

            // Close these streams because no-one is using them.
            safeClose(p.outputStream)
            safeClose(p.inputStream)
            safeClose(p.errorStream)

            executor.submit {
                val exitValue = p.waitFor()
                process = null

                log.info("Node Explorer for '{}' has exited (value={})", config.legalName, exitValue)
                onExit(config)
            }
        } catch (e: IOException) {
            log.error("Failed to launch Node Explorer for '{}': {}", config.legalName, e.message)
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

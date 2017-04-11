package net.corda.demobench.explorer

import net.corda.core.utilities.loggerFor
import net.corda.demobench.model.NodeConfig
import net.corda.demobench.model.forceDirectory
import net.corda.demobench.readErrorLines
import java.io.IOException
import java.util.concurrent.Executors

class Explorer internal constructor(private val explorerController: ExplorerController) : AutoCloseable {
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
            val user = config.users.elementAt(0)
            val p = explorerController.process(
                    "--host=localhost",
                    "--port=${config.rpcPort}",
                    "--username=${user.username}",
                    "--password=${user.password}")
                    .directory(explorerDir)
                    .start()
            process = p

            log.info("Launched Node Explorer for '{}'", config.legalName)

            // Close these streams because no-one is using them.
            safeClose(p.outputStream)
            safeClose(p.inputStream)

            executor.submit {
                val exitValue = p.waitFor()
                val errors = p.readErrorLines()
                process = null

                if (errors.isEmpty()) {
                    log.info("Node Explorer for '{}' has exited (value={})", config.legalName, exitValue)
                } else {
                    log.error("Node Explorer for '{}' has exited (value={}, {})", config.legalName, exitValue, errors)
                }

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

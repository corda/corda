package net.corda.demobench.model

import org.slf4j.LoggerFactory
import java.util.concurrent.Executors

class Explorer(val explorerController: ExplorerController) : AutoCloseable {
    private val log = LoggerFactory.getLogger(Explorer::class.java)

    private val executor = Executors.newSingleThreadExecutor()
    private var process: Process? = null

    fun open(config: NodeConfig, onExit: (NodeConfig) -> Unit) {
        val p = explorerController.execute(
              "--host=localhost",
              "--port=${config.artemisPort}",
              "--username=${config.user["user"]}",
              "--password=${config.user["password"]}",
              "--certificatesDir=${config.ssl.certificatesDirectory}",
              "--keyStorePassword=${config.ssl.keyStorePassword}",
              "--trustStorePassword=${config.ssl.trustStorePassword}"
        )
        process = p

        executor.submit {
            val exitValue = p.waitFor()
            process = null

            log.info("Node Explorer for '{}' has exited (value={})", config.legalName, exitValue)
            onExit(config)
        }
    }

    override fun close() {
        executor.shutdown()
        process?.destroy()
    }

}
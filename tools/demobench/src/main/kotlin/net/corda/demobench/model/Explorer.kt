package net.corda.demobench.model

import org.slf4j.LoggerFactory
import java.util.concurrent.Executors


class Explorer(val explorerController: ExplorerController) : AutoCloseable {
    private val log = LoggerFactory.getLogger(Explorer::class.java)

    private val executor = Executors.newSingleThreadExecutor()
    private var process: Process? = null

    fun open(config: NodeConfig, onExit: (c: NodeConfig) -> Unit) {
        val p = explorerController.execute(
              "--host=localhost",
              "--port=%d".format(config.artemisPort),
              "--username=%s".format(config.user["user"]),
              "--password=%s".format(config.user["password"])
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
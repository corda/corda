package net.corda.demobench.model

import com.jediterm.terminal.ui.UIUtil
import com.typesafe.config.ConfigRenderOptions
import java.lang.management.ManagementFactory
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import net.corda.demobench.pty.R3Pty
import tornadofx.Controller

class NodeController : Controller() {
    private val FIRST_PORT = 10000

    private val workDir = Paths.get("work", localDir).toAbsolutePath()

    private val javaExe = if (UIUtil.isWindows) "java.exe" else "java"
    private val javaPath = Paths.get(System.getProperty("java.home"), "bin", javaExe)
    private val cordaPath = Paths.get("corda", "corda.jar").toAbsolutePath()
    private val command = arrayOf(javaPath.toString(), "-jar", cordaPath.toString())

    private val renderOptions = ConfigRenderOptions.defaults().setOriginComments(false)

    private val nodes = ConcurrentHashMap<String, NodeConfig>()
    private val port = AtomicInteger(FIRST_PORT)

    fun validate(nodeData: NodeData): NodeConfig? {
        val config = NodeConfig(
            nodeData.legalName.value,
            nodeData.nearestCity.value,
            nodeData.p2pPort.value,
            nodeData.artemisPort.value,
            nodeData.webPort.value
        )

        if (nodes.putIfAbsent(config.key, config) != null) {
            return null
        }

        return config
    }

    val nextPort: Int
        get() { return port.andIncrement }

    fun runCorda(pty: R3Pty, config: NodeConfig): Boolean {
        val nodeDir = workDir.resolve(config.key).toFile()

        if (nodeDir.mkdirs()) {
            try {
                // Write this nodes configuration file into its working directory.
                val confFile = nodeDir.resolve("node.conf")
                val fileData = config.toFileConfig
                confFile.writeText(fileData.root().render(renderOptions))

                pty.run(command, System.getenv(), nodeDir.toString())
                return true
            } catch (e: Exception) {
                log.severe("Failed to launch Corda:" + e)
                return false
            }
        } else {
            return false
        }
    }

    private val localDir: String
        get() = SimpleDateFormat("yyyyMMddHHmmss")
                    .format(Date(ManagementFactory.getRuntimeMXBean().startTime))

    init {
        log.info("Working directory: " + workDir)
        log.info("Java executable: " + javaPath)
    }
}

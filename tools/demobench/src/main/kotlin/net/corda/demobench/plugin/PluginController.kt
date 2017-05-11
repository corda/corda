package net.corda.demobench.plugin

import net.corda.demobench.model.HasPlugins
import net.corda.demobench.model.JVMConfig
import net.corda.demobench.model.NodeConfig
import tornadofx.*
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

class PluginController : Controller() {

    private val jvm by inject<JVMConfig>()
    private val pluginDir: Path = jvm.applicationDir.resolve("plugins")
    private val bankOfCorda = pluginDir.resolve("bank-of-corda.jar").toFile()

    /**
     * Install any built-in plugins that this node requires.
     */
    @Throws(IOException::class)
    fun populate(config: NodeConfig) {
        // Nodes cannot issue cash unless they contain the "Bank of Corda" plugin.
        if (config.isCashIssuer && bankOfCorda.isFile) {
            bankOfCorda.copyTo(config.pluginDir.resolve(bankOfCorda.name).toFile(), overwrite = true)
            log.info("Installed 'Bank of Corda' plugin")
        }
    }

    /**
     * Generates a stream of a node's non-built-it plugins.
     */
    @Throws(IOException::class)
    fun userPluginsFor(config: HasPlugins): Stream<Path> = walkPlugins(config.pluginDir)
            .filter { bankOfCorda.name != it.fileName.toString() }

    private fun walkPlugins(pluginDir: Path): Stream<Path> {
        return if (Files.isDirectory(pluginDir))
            Files.walk(pluginDir, 1).filter(Path::isPlugin)
        else
            Stream.empty()
    }

}

fun Path.isPlugin(): Boolean = Files.isReadable(this) && this.fileName.toString().endsWith(".jar")
fun Path.inPluginsDir(): Boolean = (this.parent != null) && this.parent.endsWith("plugins/")

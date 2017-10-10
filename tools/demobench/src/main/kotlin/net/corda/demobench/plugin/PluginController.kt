package net.corda.demobench.plugin

import net.corda.core.internal.copyToDirectory
import net.corda.core.internal.createDirectories
import net.corda.core.internal.exists
import net.corda.demobench.model.HasPlugins
import net.corda.demobench.model.JVMConfig
import net.corda.demobench.model.NodeConfigWrapper
import tornadofx.*
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.stream.Stream

class PluginController : Controller() {

    private val jvm by inject<JVMConfig>()
    private val pluginDir: Path = jvm.applicationDir.resolve("plugins")
    private val bankOfCorda: Path = pluginDir.resolve("bank-of-corda.jar")
    private val finance: Path = pluginDir.resolve("corda-finance.jar")

    /**
     * Install any built-in plugins that this node requires.
     */
    @Throws(IOException::class)
    fun populate(config: NodeConfigWrapper) {
        config.pluginDir.createDirectories()
        if (finance.exists()) {
            finance.copyToDirectory(config.pluginDir, StandardCopyOption.REPLACE_EXISTING)
            log.info("Installed 'Finance' plugin")
        }
        // Nodes cannot issue cash unless they contain the "Bank of Corda" plugin.
        if (config.nodeConfig.issuableCurrencies.isNotEmpty() && bankOfCorda.exists()) {
            bankOfCorda.copyToDirectory(config.pluginDir, StandardCopyOption.REPLACE_EXISTING)
            log.info("Installed 'Bank of Corda' plugin")
        }
    }

    /**
     * Generates a stream of a node's non-built-in plugins.
     */
    @Throws(IOException::class)
    fun userPluginsFor(config: HasPlugins): Stream<Path> = walkPlugins(config.pluginDir)
            .filter { !bankOfCorda.endsWith(it.fileName) }
            .filter { !finance.endsWith(it.fileName) }

    private fun walkPlugins(pluginDir: Path): Stream<Path> {
        return if (Files.isDirectory(pluginDir))
            Files.walk(pluginDir, 1).filter(Path::isPlugin)
        else
            Stream.empty()
    }

}

fun Path.isPlugin(): Boolean = Files.isReadable(this) && this.fileName.toString().endsWith(".jar")
fun Path.inPluginsDir(): Boolean = (this.parent != null) && this.parent.endsWith("plugins/")

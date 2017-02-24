package net.corda.demobench.plugin

import java.io.IOException
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.stream.Stream
import kotlinx.support.jdk8.collections.stream
import kotlinx.support.jdk8.streams.toList
import net.corda.core.node.CordaPluginRegistry
import net.corda.demobench.model.JVMConfig
import net.corda.demobench.model.NodeConfig
import tornadofx.*

class PluginController : Controller() {

    private val jvm by inject<JVMConfig>()
    private val pluginDir = jvm.applicationDir.resolve("plugins")
    private val bankOfCorda = pluginDir.resolve("bank-of-corda.jar").toFile()

    /**
     * Install any built-in plugins that the node requires.
     */
    @Throws(IOException::class)
    fun populate(config: NodeConfig) {
        // Nodes cannot issue cash unless they contain the "Bank of Corda" plugin.
        if (config.isCashIssuer && bankOfCorda.isFile) {
            bankOfCorda.copyTo(config.pluginDir.resolve(bankOfCorda.name).toFile(), overwrite=true)
            log.info("Installed 'Bank of Corda' plugin")
        }
    }

    /**
     * Extract the set of user permissions that this plugin's flows require.
     */
    fun permissionsFor(config: NodeConfig) = walkPlugins(config.pluginDir)
        .map { plugin -> classLoaderFor(plugin) }
        .flatMap { cl -> cl.use(URLClassLoader::flowsFor).stream() }
        .map { flow -> "StartFlow.$flow" }
        .toList()

    /**
     * Generates a stream of a node's non-built-it plugins.
     */
    fun userPluginsFor(config: NodeConfig): Stream<Path> = walkPlugins(config.pluginDir)
            .filter { bankOfCorda.name != it.fileName.toString() }

    /**
     * Removes the "plugins" directory associated with this node.
     */
    fun deletePluginsFor(config: NodeConfig): Boolean = config.pluginDir.toFile().deleteRecursively()

    private fun walkPlugins(pluginDir: Path): Stream<Path> {
        return if (Files.isDirectory(pluginDir))
            Files.walk(pluginDir, 1).filter { it.isPlugin() }
        else
            Stream.empty()
    }

    private fun classLoaderFor(jarPath: Path) = URLClassLoader(arrayOf(jarPath.toUri().toURL()), javaClass.classLoader)

}

private fun URLClassLoader.flowsFor(): List<String> = ServiceLoader.load(CordaPluginRegistry::class.java, this)
    .flatMap { plugin ->
        val registry = constructorFor(plugin).newInstance() as CordaPluginRegistry
        registry.requiredFlows.keys
    }

private fun constructorFor(type: Any) = type.javaClass.constructors.filter { it.parameterCount == 0 }.single()

fun Path.isPlugin(): Boolean = Files.isReadable(this) && this.fileName.toString().endsWith(".jar")
fun Path.inPluginsDir(): Boolean = (this.parent != null) && this.parent.endsWith("plugins/")

package net.corda.demobench.plugin

import net.corda.core.internal.*
import net.corda.demobench.model.HasCordapps
import net.corda.demobench.model.JVMConfig
import net.corda.demobench.model.NodeConfig
import net.corda.demobench.model.NodeConfigWrapper
import tornadofx.*
import java.io.IOException
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import kotlin.streams.toList

// TODO This class needs to be revisited. It seems to operate on outdated concepts.
class CordappController : Controller() {
    companion object {
        const val FINANCE_CONTRACTS_CORDAPP_FILENAME = "corda-finance-contracts"
        const val FINANCE_WORKFLOWS_CORDAPP_FILENAME = "corda-finance-workflows"
    }

    private val jvm by inject<JVMConfig>()
    private val cordappDir: Path = jvm.applicationDir / NodeConfig.CORDAPP_DIR_NAME
    private val cordappJars = setOf(FINANCE_CONTRACTS_CORDAPP_FILENAME, FINANCE_WORKFLOWS_CORDAPP_FILENAME)
            .map { cordappDir / "$it.jar" }

    /**
     * Install any built-in cordapps that this node requires.
     */
    @Throws(IOException::class)
    fun populate(config: NodeConfigWrapper) {
        if (!config.cordappsDir.exists()) {
            config.cordappsDir.createDirectories()
        }
        cordappJars.forEach { cordappJar ->
            if (cordappJar.exists()) {
                cordappJar.copyToDirectory(config.cordappsDir, REPLACE_EXISTING)
                log.info("Installed cordapp: $cordappJar")
            }
        }
    }

    /**
     * Generates a stream of a node's non-built-in cordapps.
     */
    @Throws(IOException::class)
    fun useCordappsFor(config: HasCordapps): List<Path> {
        if (!config.cordappsDir.isDirectory()) return emptyList()
        return config.cordappsDir.walk(1) { paths ->
            paths.filter(Path::isCordapp)
                 .filter { cordappJars.any { !it.endsWith(it.fileName) } }
                 .toList()
        }
    }
}

val Path.isCordapp: Boolean get() = isReadable && fileName.toString().endsWith(".jar")
val Path.inCordappsDir: Boolean get() = (parent != null) && parent.endsWith("cordapps/")

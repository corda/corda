package net.corda.node.utilities

import com.ea.agentloader.AgentLoader
import net.corda.core.internal.exists
import net.corda.core.internal.isRegularFile
import net.corda.core.internal.toPath
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * Helper class for loading JVM agents dynamically
 */
object JVMAgentRegistry {

    /**
     * Names and options of loaded agents
     */
    val loadedAgents = ConcurrentHashMap<String, String>()

    /**
     * Load and attach agent located at given [jar], unless [loadedAgents]
     * indicate that one of its instance has been already loaded.
     */
    fun attach(agentName: String, options: String, jar: Path) {
        loadedAgents.computeIfAbsent(agentName.toLowerCase()) {
            AgentLoader.loadAgent(jar.toString(), options)
            options
        }
    }

    /**
     * Attempt finding location of jar for given agent by first searching into
     * "drivers" directory of [nodeBaseDirectory] and then falling back to
     * classpath. Returns null if no match is found.
     */
    fun resolveAgentJar(jarFileName: String, driversDir: Path): Path? {
        require(jarFileName.endsWith(".jar")) { "jarFileName does not have .jar suffix" }

        val path = Paths.get(driversDir.toString(), jarFileName)
        return if (path.exists() && path.isRegularFile()) {
            path
        } else {
            (this::class.java.classLoader as? URLClassLoader)
                    ?.urLs
                    ?.map(URL::toPath)
                    ?.firstOrNull { it.fileName.toString() == jarFileName }
        }
    }
}
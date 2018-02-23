package net.corda.launcher

import java.io.FileInputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.collections.HashSet

// Expose Corda bootstrapping settings from property file
object Settings {

    // JavaPackager reset cwd to the "/apps" subfolder, so its location is in the parent directory
    private val LAUNCHER_PATH = Paths.get("..")

    // Launcher property file
    private val CORDA_RUNTIME_SETTINGS = LAUNCHER_PATH.resolve("runtime.properties")

    // The application working directory
    val WORKING_DIR: Path = System.getenv("CORDA_LAUNCHER_CWD")?.let {Paths.get(it)} ?: LAUNCHER_PATH

    // Application classpath
    val CLASSPATH: List<URL>

    // Plugin directories (all contained jar files are added to classpath)
    val PLUGINS: List<Path>

    // Path of the "lib" subdirectory in bundle
    private val LIBPATH: Path

    init {
        val settings = Properties().apply {
            load(FileInputStream(CORDA_RUNTIME_SETTINGS.toFile()))
        }

        LIBPATH = Paths.get(settings.getProperty("libpath") ?: ".")
        CLASSPATH = parseClasspath(settings)
        PLUGINS = parsePlugins(settings)
    }

    private fun parseClasspath(config: Properties): List<URL> {
        val libDir = LAUNCHER_PATH.resolve(LIBPATH).toAbsolutePath()
        val cp = config.getProperty("classpath") ?:
                throw Error("Missing 'classpath' property from config")

        return cp.split(':').map {
            libDir.resolve(it).toUri().toURL()
        }
    }

    private fun parsePlugins(config: Properties): List<Path> {
        val ext = config.getProperty("plugins")

        return ext?.let {
            it.split(':').map { Paths.get(it) }
        } ?: emptyList()
    }
}

package net.corda.launcher

import java.io.FileInputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.collections.HashSet

object Settings {

    val CORDA_RUNTIME_SETTINGS = "../runtime.properties"
    val WORKING_DIR: Path
    val CLASSPATH: List<URL>
    val PLUGINS: List<Path>
    val LIBPATH: Path

    init {
        WORKING_DIR = Paths.get(System.getenv("CORDA_LAUNCHER_CWD") ?: "..")

        val settings = Properties().apply {
            load(FileInputStream(CORDA_RUNTIME_SETTINGS))
        }

        CLASSPATH = parseClasspath(settings)
        PLUGINS = parsePlugins(settings)
        LIBPATH = Paths.get(settings.getProperty("libpath") ?: ".")
    }

    private fun parseClasspath(config: Properties): List<URL> {
        val libDir = Paths.get("..").resolve(LIBPATH).toAbsolutePath()
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

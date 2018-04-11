@file:JvmName("Launcher")

package net.corda.launcher

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(args: Array<String>) {

    if(args.isEmpty()) {
        println("Usage: launcher <main-class-name> [args]")
        exitProcess(0)
    }

    // TODO: --base-directory is specific of the Node app, it should be controllable by a config property
    val nodeBaseDir = Paths.get(Settings.WORKING_DIR)
            .resolve(getBaseDirectory(args) ?: ".")
            .toAbsolutePath()

    val appClassLoader = setupClassLoader(nodeBaseDir)

    val appMain = try {
        appClassLoader
                .loadClass(args[0])
                .getMethod("main", Array<String>::class.java)
    } catch (e: Exception) {
        System.err.println("Error looking for method 'main' in class ${args[0]}:")
        e.printStackTrace()
        exitProcess(1)
    }

    // Propagate current working directory via system property, to patch it after javapackager
    System.setProperty("corda-launcher.cwd", Settings.WORKING_DIR)
    System.setProperty("user.dir", Settings.WORKING_DIR)

    try {
        appMain.invoke(null, args.sliceArray(1..args.lastIndex))
    } catch (e: Exception) {
        e.printStackTrace()
        exitProcess(1)
    }
}

private fun setupClassLoader(nodeBaseDir: Path): ClassLoader {
    val sysClassLoader = ClassLoader.getSystemClassLoader()

    val appClassLoader = (sysClassLoader as? Loader) ?: {
        println("WARNING: failed to override system classloader")
        Loader(sysClassLoader)
    } ()

    // Lookup plugins and extend classpath
    val pluginURLs = Settings.PLUGINS.flatMap {
        val entry = nodeBaseDir.resolve(it)
        if (Files.isDirectory(entry)) {
            entry.jarFiles()
        } else {
            setOf(entry)
        }
    }.map { it.toUri().toURL() }

    appClassLoader.augmentClasspath(pluginURLs)

    // For logging
    System.setProperty("corda-launcher.appclassloader.urls", appClassLoader.urLs.joinToString(":"))

    return appClassLoader
}

private fun getBaseDirectory(args: Array<String>): String? {
    val idx = args.indexOf("--base-directory")
    return if (idx != -1 && idx < args.lastIndex) {
        args[idx + 1]
    } else null
}

private fun Path.jarFiles(): List<Path> {
    return Files.newDirectoryStream(this).filter { it.toString().endsWith(".jar") }
}

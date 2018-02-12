@file:JvmName("Launcher")

package net.corda.launcher

import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {

    val sysClassLoader = ClassLoader.getSystemClassLoader()

    val appClassLoader = (sysClassLoader as? Loader) ?: {
        println("WARNING: failed to overried system classloader")
        Loader(sysClassLoader)
    } ()

    if(args.isEmpty()) {
        println("Usage: launcher <main-class-name>")
        exitProcess(0)
    }

    // Resolve plugins directory and extend classpath
    val nodeBaseDir = Settings.WORKING_DIR
            .resolve(getBaseDirectory(args) ?: ".")
            .toAbsolutePath()

    val pluginURLs = Settings.PLUGINS.flatMap {
        val entry = nodeBaseDir.resolve(it)
        if (Files.isDirectory(entry)) {
            entry.jarFiles()
        } else {
            setOf(entry)
        }
    }.map { it.toUri().toURL() }

    appClassLoader.augmentClasspath(pluginURLs)

    // Propagate current working directory, as workaround for javapackager
    // corrupting it
    System.setProperty("corda.launcher.cwd", nodeBaseDir.toString())
    System.setProperty("user.dir", nodeBaseDir.toString())

    try {
        appClassLoader
                .loadClass(args[0])
                .getMethod("main", Array<String>::class.java)
                .invoke(null, args.sliceArray(1..args.lastIndex))
    } catch (e: Exception) {
        e.printStackTrace()
        exitProcess(1)
    }
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

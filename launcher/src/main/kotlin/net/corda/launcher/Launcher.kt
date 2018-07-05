@file:JvmName("Launcher")

package net.corda.launcher

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: launcher <main-class-name> [args]")
        exitProcess(0)
    }

    // TODO: --base-directory is specific of the Node app, it should be controllable by a config property
    // we must use this directory for loading classpath components
    //but it must be resolved relative to the CWD the user has launched the script from as they may use a relative path
    val nodeBaseDirFromArgs = Paths.get(Settings.WORKING_DIR)
            .resolve(getBaseDirectoryFromArgs(args) ?: ".")
            .toAbsolutePath()

    val appClassLoader = setupClassLoader(nodeBaseDirFromArgs)

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

    // To distinguish how Corda was started in order to create more detailed message for any discrepancies between Capsule and javapackager (tarball)
    // e.g. if JDBC driver was not found, remove once Corda started by Capsule is no longer in use
    System.setProperty("corda-distribution.tarball", "true")

    val argsWithBaseDir = fixBaseDirArg(args, nodeBaseDirFromArgs)

    try {
        appMain.invoke(null, argsWithBaseDir.sliceArray(1..argsWithBaseDir.lastIndex))
    } catch (e: Exception) {
        e.printStackTrace()
        exitProcess(1)
    }
}

@Suppress("unchecked")
private fun fixBaseDirArg(args: Array<String>, nodeBaseDirFromArgs: Path): Array<String> {
    val baseDirIdx = args.indexOf("--base-directory")
    return if (baseDirIdx != -1) {
        // Replace the arg that follows, i.e. --base-directory X
        // TODO This will not work for --base-directory=X
        args[baseDirIdx + 1] = nodeBaseDirFromArgs.toString()
        args
    } else {
        args + listOf("--base-directory", nodeBaseDirFromArgs.toString())
    }
}

private fun setupClassLoader(nodeBaseDir: Path): ClassLoader {
    val sysClassLoader = ClassLoader.getSystemClassLoader()

    val appClassLoader = (sysClassLoader as? Loader) ?: {
        println("WARNING: failed to override system classloader")
        Loader(sysClassLoader)
    }()

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

private fun getBaseDirectoryFromArgs(args: Array<String>): String? {
    val idx = args.indexOf("--base-directory")
    return if (idx != -1 && idx < args.lastIndex) {
        args[idx + 1]
    } else null
}


private fun Path.jarFiles(): List<Path> {
    return Files.newDirectoryStream(this).filter { it.toString().endsWith(".jar") }
}
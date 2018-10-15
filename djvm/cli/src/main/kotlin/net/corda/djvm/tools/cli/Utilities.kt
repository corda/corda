@file:JvmName("Utilities")
package net.corda.djvm.tools.cli

import io.github.classgraph.ClassGraph
import java.lang.reflect.Modifier.isAbstract
import java.lang.reflect.Modifier.isStatic
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * Get the expanded file name of each path in the provided array.
 */
fun Array<Path>?.getFiles(map: (Path) -> Path = { it }) = (this ?: emptyArray()).map {
    val pathString = it.toString()
    val path = map(it)
    when {
        '/' in pathString || '\\' in pathString ->
            throw Exception("Please provide a pathless file name")
        pathString.endsWith(".java", true) -> path
        else -> Paths.get("$path.java")
    }
}

/**
 * Get the string representation of each expanded file name in the provided array.
 */
fun Array<Path>?.getFileNames(map: (Path) -> Path = { it }) = this.getFiles(map).map {
    it.toString()
}.toTypedArray()

/**
 * Execute inlined action if the collection is empty.
 */
inline fun <T> List<T>.onEmpty(action: () -> Unit): List<T> {
    if (!this.any()) {
        action()
    }
    return this
}

/**
 * Execute inlined action if the array is empty.
 */
inline fun <reified T> Array<T>?.onEmpty(action: () -> Unit): Array<T> {
    return (this ?: emptyArray()).toList().onEmpty(action).toTypedArray()
}

/**
 * Derive the set of [StandardOpenOption]'s to use for a file operation.
 */
fun openOptions(force: Boolean) = if (force) {
    arrayOf(StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
} else {
    arrayOf(StandardOpenOption.CREATE_NEW)
}

/**
 * Get the path of where any generated code will be placed. Create the directory if it does not exist.
 */
fun createCodePath(): Path {
    return Paths.get("tmp", "net", "corda", "djvm").let {
        Files.createDirectories(it)
    }
}

/**
 * Return the base name of a file (i.e., its name without extension)
 */
val Path.baseName: String
    get() = this.fileName.toString()
            .replaceAfterLast('.', "")
            .removeSuffix(".")

/**
 * The path of the executing JAR.
 */
val jarPath: String = object {}.javaClass.protectionDomain.codeSource.location.toURI().path


/**
 * The path of the current working directory.
 */
val workingDirectory: Path = Paths.get(System.getProperty("user.dir"))

/**
 * The class path for the current execution context.
 */
val userClassPath: String = System.getProperty("java.class.path")

/**
 * Get a reference of each concrete class that implements interface or class [T].
 */
inline fun <reified T> find(scanSpec: String = "net/corda/djvm"): List<Class<*>> {
    return ClassGraph()
            .whitelistPaths(scanSpec)
            .enableAllInfo()
            .scan()
            .use { it.getClassesImplementing(T::class.java.name).loadClasses(T::class.java) }
            .filter { !isAbstract(it.modifiers) && !isStatic(it.modifiers) }
}

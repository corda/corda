@file:JvmName("Utilities")
package net.corda.gradle.jarfilter

import org.junit.AssumptionViolatedException
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URLClassLoader
import java.nio.file.StandardCopyOption.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors.*
import java.util.zip.ZipFile
import kotlin.reflect.KClass

const val DEFAULT_MESSAGE = "<default-value>"
const val MESSAGE = "Goodbye, Cruel World!"
const val NUMBER = 111
const val BIG_NUMBER = 9999L

private val classLoader: ClassLoader = object {}.javaClass.classLoader

// The AssumptionViolatedException must be caught by the JUnit test runner,
// which means that it must not be thrown when this class loads.
private val testGradleUserHomeValue: String? = System.getProperty("test.gradle.user.home")
private val testGradleUserHome: String get() = testGradleUserHomeValue
    ?: throw AssumptionViolatedException("System property 'test.gradle.user.home' not set.")

fun getGradleArgsForTasks(vararg taskNames: String): MutableList<String> = getBasicArgsForTasks(*taskNames).apply { add("--info") }
fun getBasicArgsForTasks(vararg taskNames: String): MutableList<String> = mutableListOf(*taskNames, "--stacktrace", "-g", testGradleUserHome)

@Throws(IOException::class)
fun copyResourceTo(resourceName: String, target: Path) {
    classLoader.getResourceAsStream(resourceName).use { source ->
        Files.copy(source, target, REPLACE_EXISTING)
    }
}

@Throws(IOException::class)
fun copyResourceTo(resourceName: String, target: File) = copyResourceTo(resourceName, target.toPath())

@Throws(IOException::class)
fun TemporaryFolder.installResources(vararg resourceNames: String) {
    resourceNames.forEach { installResource(it) }
}

@Throws(IOException::class)
fun TemporaryFolder.installResource(resourceName: String): File = newFile(resourceName.fileName).let { file ->
    copyResourceTo(resourceName, file)
    file
}

private val String.fileName: String get() = substring(1 + lastIndexOf('/'))

val String.toPackageFormat: String get() = replace('/', '.')
fun pathsOf(vararg types: KClass<*>): Set<String> = types.map { it.java.name.toPathFormat }.toSet()

fun TemporaryFolder.pathOf(vararg elements: String): Path = Paths.get(root.absolutePath, *elements)

fun arrayOfJunk(size: Int) = ByteArray(size).apply {
    for (i in 0 until size) {
        this[i] = (i and 0xFF).toByte()
    }
}

@Throws(MalformedURLException::class)
fun classLoaderFor(jar: Path) = URLClassLoader(arrayOf(jar.toUri().toURL()), classLoader)

@Suppress("UNCHECKED_CAST")
@Throws(ClassNotFoundException::class)
fun <T> ClassLoader.load(className: String)
            = Class.forName(className, true, this) as Class<T>

fun Path.getClassNames(prefix: String): List<String> {
    val resourcePrefix = prefix.toPathFormat
    return ZipFile(toFile()).stream()
        .filter { it.name.startsWith(resourcePrefix) && it.name.endsWith(".class") }
        .map { it.name.removeSuffix(".class").toPackageFormat }
        .collect(toList<String>())
}

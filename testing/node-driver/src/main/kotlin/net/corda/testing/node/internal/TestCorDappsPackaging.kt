package net.corda.testing.node.internal

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import net.corda.core.internal.*
import net.corda.node.internal.cordapp.createTestManifest
import java.io.File
import java.io.OutputStream
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// TODO sollecitom, perhaps create a TestCorDappPackager class, rather than extension functions

internal fun Iterable<JarEntryInfo>.packageToCorDapp(path: Path, name: String, version: String, vendor: String, title: String = name, willResourceBeAddedBeToCorDapp: (String, URL) -> Boolean = { _, _ -> true }) {

    var hasContent = false
    try {
        hasContent = packageToCorDapp(path.outputStream(), name, version, vendor, title, willResourceBeAddedBeToCorDapp)
    } finally {
        if (!hasContent) {
            path.deleteIfExists()
        }
    }
}

internal fun Iterable<JarEntryInfo>.packageToCorDapp(outputStream: OutputStream, name: String, version: String, vendor: String, title: String = name, willResourceBeAddedBeToCorDapp: (String, URL) -> Boolean = { _, _ -> true }): Boolean {

    val manifest = createTestManifest(name, title, version, vendor)
    return JarOutputStream(outputStream, manifest).use { jos -> zip(jos, willResourceBeAddedBeToCorDapp) }
}

internal fun Class<*>.jarEntryInfo(): JarEntryInfo {

    return JarEntryInfo.ClassJarEntryInfo(this)
}

fun allClassesForPackage(targetPackage: String): Set<Class<*>> {

    val scanResult = FastClasspathScanner(targetPackage).strictWhitelist().scan()
    return scanResult.namesOfAllClasses.filter { className -> className.startsWith(targetPackage) }.map(scanResult::classNameToClassRef).toSet()
}

private fun String.packageToPath() = replace(".", File.separator)

private fun Iterable<JarEntryInfo>.zip(outputStream: ZipOutputStream, willResourceBeAddedBeToCorDapp: (String, URL) -> Boolean): Boolean {

    val entries = filter { (fullyQualifiedName, url) -> willResourceBeAddedBeToCorDapp(fullyQualifiedName, url) }
    if (entries.isNotEmpty()) {
        zip(outputStream, entries)
    }
    return entries.isNotEmpty()
}

private fun zip(outputStream: ZipOutputStream, allInfo: Iterable<JarEntryInfo>) {

    val time = FileTime.from(Instant.now())
    allInfo.distinctBy { it.url }.forEach { info ->

        val path = info.url.toPath()
        val entry = ZipEntry(info.entryName).setCreationTime(time).setLastAccessTime(time).setLastModifiedTime(time)
        outputStream.putNextEntry(entry)
        if (path.isRegularFile()) {
            path.copyTo(outputStream)
        }
        outputStream.closeEntry()
    }
}

internal sealed class JarEntryInfo(val fullyQualifiedName: String, val url: URL) {

    abstract val entryName: String

    class ClassJarEntryInfo(val clazz: Class<*>) : JarEntryInfo(clazz.name, clazz.classFileURL()) {

        override val entryName = "${fullyQualifiedName.packageToPath()}$fileExtensionSeparator$classFileExtension"
    }

    class ResourceJarEntryInfo(fullyQualifiedName: String, url: URL) : JarEntryInfo(fullyQualifiedName, url) {

        override val entryName: String
            get() {
                val extensionIndex = fullyQualifiedName.lastIndexOf(fileExtensionSeparator)
                return "${fullyQualifiedName.substring(0 until extensionIndex).packageToPath()}${fullyQualifiedName.substring(extensionIndex)}"
            }
    }

    operator fun component1(): String = fullyQualifiedName

    operator fun component2(): URL = url

    private companion object {

        private const val classFileExtension = "class"
        private const val fileExtensionSeparator = "."
        private const val whitespace = " "
        private const val whitespaceReplacement = "%20"

        private fun Class<*>.classFileURL(): URL {

            require(protectionDomain?.codeSource?.location != null) { "Invalid class $name for test CorDapp. Classes without protection domain cannot be referenced. This typically happens for Java / Kotlin types." }
            return URI.create("${protectionDomain.codeSource.location}/${name.packageToPath()}$fileExtensionSeparator$classFileExtension".escaped()).toURL()
        }

        private fun String.escaped(): String = this.replace(whitespace, whitespaceReplacement)
    }
}
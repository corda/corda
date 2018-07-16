package net.corda.testing.node.internal

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import net.corda.core.internal.*
import java.io.File
import java.io.OutputStream
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// TODO sollecitom, perhaps create a TestCorDappPackager class, rather than extension functions

// TODO sollecitom
internal fun Iterable<TestCordappBuilder.JarEntryInfo>.packageToCorDapp(path: Path, name: String, version: String, vendor: String, title: String = name, willResourceBeAddedBeToCorDapp: (String, URL) -> Boolean = { _, _ -> true }) {

    var hasContent = false
    try {
        hasContent = packageToCorDapp(path.outputStream(), name, version, vendor, title, willResourceBeAddedBeToCorDapp)
    } finally {
        if (!hasContent) {
            path.deleteIfExists()
        }
    }
}

// TODO sollecitom - try and remove this ClassLoader argument (it's only used to figure out the out folder)
internal fun Iterable<TestCordappBuilder.JarEntryInfo>.packageToCorDapp(outputStream: OutputStream, name: String, version: String, vendor: String, title: String = name, willResourceBeAddedBeToCorDapp: (String, URL) -> Boolean = { _, _ -> true }): Boolean {

    val manifest = createTestManifest(name, title, version, vendor)
    return JarOutputStream(outputStream, manifest).use { jos -> zip(jos, willResourceBeAddedBeToCorDapp) }
}

// TODO sollecitom
fun Package.allClasses(): Set<Class<*>> {

    return allClassesForPackage(name)
}

// TODO sollecitom
fun allClassesForPackage(targetPackage: String): Set<Class<*>> {

    val scanResult = FastClasspathScanner(targetPackage).scan()
    return scanResult.namesOfAllClasses.filter { it.startsWith(targetPackage) }.map(scanResult::classNameToClassRef).toSet()
}

// TODO sollecitom
fun String.packageToPath() = replace(".", File.separator)

// TODO sollecitom
private fun Iterable<TestCordappBuilder.JarEntryInfo>.zip(outputStream: ZipOutputStream, willResourceBeAddedBeToCorDapp: (String, URL) -> Boolean): Boolean {

    val entries = filter { (fullyQualifiedName, url) -> willResourceBeAddedBeToCorDapp(fullyQualifiedName, url) }
    if (entries.isNotEmpty()) {
        zip(outputStream, entries)
    }
    return entries.isNotEmpty()
}

// TODO sollecitom
private fun zip(outputStream: ZipOutputStream, allInfo: Iterable<TestCordappBuilder.JarEntryInfo>) {

    val time = FileTime.from(Instant.now())
    allInfo.distinctBy { it.url }.forEach { info ->

        val path = info.url.toPath()
        val entryPath = "${info.fullyQualifiedName.packageToPath()}.class"
        val entry = ZipEntry(entryPath).setCreationTime(time).setLastAccessTime(time).setLastModifiedTime(time)
        outputStream.putNextEntry(entry)
        if (path.isRegularFile()) {
            path.copyTo(outputStream)
        }
        outputStream.closeEntry()
    }
}

// TODO sollecitom
internal fun Class<*>.jarEntryInfo(): TestCordappBuilder.JarEntryInfo {

    return TestCordappBuilder.JarEntryInfo.ClassJarEntryInfo(this)
}

// TODO sollecitom
fun Class<*>.classFileURL(): URL {

    require(protectionDomain?.codeSource?.location != null) { "Invalid class $name for test CorDapp. Classes without protection domain cannot be referenced. This typically happens for Java / Kotlin types." }
    // TODO sollecitom refactor the whitespace fix not to hardcode strings
    return URI.create("${protectionDomain.codeSource.location}/${name.packageToPath()}.class".replace(" ", "%20")).toURL()
}

// TODO sollecitom move to utils
private fun createTestManifest(name: String, title: String, version: String, vendor: String): Manifest {

    val manifest = Manifest()

    // Mandatory manifest attribute. If not present, all other entries are silently skipped.
    manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"

    manifest["Name"] = name

    manifest["Specification-Title"] = title
    manifest["Specification-Version"] = version
    manifest["Specification-Vendor"] = vendor

    manifest["Implementation-Title"] = title
    manifest["Implementation-Version"] = version
    manifest["Implementation-Vendor"] = vendor

    return manifest
}

// TODO sollecitom move to utils
private operator fun Manifest.set(key: String, value: String) {

    mainAttributes.putValue(key, value)
}
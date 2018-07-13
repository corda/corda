package net.corda.testing.node.internal

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import net.corda.core.internal.copyTo
import net.corda.core.internal.isRegularFile
import net.corda.core.internal.outputStream
import net.corda.core.internal.toPath
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
fun Iterable<Class<*>>.packageToCorDapp(path: Path, name: String, version: String, vendor: String, title: String = name) {

    packageToCorDapp(path.outputStream(), name, version, vendor, title)
}

// TODO sollecitom - try and remove this ClassLoader argument (it's only used to figure out the out folder)
fun Iterable<Class<*>>.packageToCorDapp(outputStream: OutputStream, name: String, version: String, vendor: String, title: String = name) {

    val manifest = createTestManifest(name, title, version, vendor)
    JarOutputStream(outputStream, manifest).use(::zip)
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
private fun Iterable<Class<*>>.zip(outputStream: ZipOutputStream) {

    zip(outputStream, map(Class<*>::jarInfo))
}

// TODO sollecitom - use Maybe here
private fun zip(outputStream: ZipOutputStream, allInfo: Iterable<ClassJarInfo>) {

    val illegal = allInfo.map { it.clazz }.filter { it.protectionDomain?.codeSource?.location == null }
    if (illegal.isNotEmpty()) {
        throw IllegalArgumentException("Some classes do not have a location, typically because they are part of Java or Kotlin. Offending types were: ${illegal.joinToString(", ", "[", "]") { it.simpleName }}")
    }
    val time = FileTime.from(Instant.now())
    allInfo.distinctBy { it.url }.forEach { info ->

        val path = info.url.toPath()
        val packagePath = info.clazz.classFilesDirectoryURL().toPath()
        // TODO sollecitom try just `info.clazz.`package`.name.packageToPath()` here :)
        val entryPath = info.clazz.`package`.name.packageToPath() + File.separator + packagePath.relativize(path).toString()
        // TODO sollecitom investigate this replacement
//        val entryPath = info.clazz.`package`.name.packageToPath() + File.separator +packagePath.relativize(path).toString().replace('\\', '/')
        val entry = ZipEntry(entryPath).setCreationTime(time).setLastAccessTime(time).setLastModifiedTime(time)
        outputStream.putNextEntry(entry)
        if (path.isRegularFile()) {
            path.copyTo(outputStream)
        }
        outputStream.closeEntry()
    }
}

// TODO sollecitom
private fun Class<*>.jarInfo(): ClassJarInfo {

    return ClassJarInfo(this, classFileURL())
}

// TODO sollecitom
private fun Class<*>.classFileURL(): URL {

    return URI.create("${protectionDomain.codeSource.location}/${name.packageToPath()}.class").toURL()
}

// TODO sollecitom
private fun Class<*>.classFilesDirectoryURL(): URL {

    return `package`.classFilesDirectoryURL(classLoader)
}

// TODO sollecitom
private fun Package.classFilesDirectoryURL(classLoader: ClassLoader): URL {

    // TODO sollecitom can this return more than one URL? Investigate
    return classLoader.getResources(name.packageToPath()).toList().single()
}

// TODO sollecitom
private fun String.packageToPath() = replace(".", File.separator)

data class ClassJarInfo(val clazz: Class<*>, val url: URL)

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
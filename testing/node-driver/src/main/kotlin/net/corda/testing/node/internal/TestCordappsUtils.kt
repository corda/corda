package net.corda.testing.node.internal

import io.github.classgraph.ClassGraph
import net.corda.core.internal.outputStream
import net.corda.node.internal.cordapp.createTestManifest
import net.corda.testing.node.TestCordapp
import java.io.BufferedOutputStream
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.reflect.KClass

@JvmField
val FINANCE_CORDAPP: TestCordappImpl = cordappForPackages("net.corda.finance")

/** Creates a [TestCordappImpl] for each package. */
fun cordappsForPackages(vararg packageNames: String): List<TestCordappImpl> = cordappsForPackages(packageNames.asList())

fun cordappsForPackages(packageNames: Iterable<String>): List<TestCordappImpl> {
    return simplifyScanPackages(packageNames).map { cordappForPackages(it) }
}

/** Creates a single [TestCordappImpl] containing all the given packges. */
fun cordappForPackages(vararg packageNames: String): TestCordappImpl {
    return TestCordapp.Factory.fromPackages(*packageNames) as TestCordappImpl
}

fun cordappForClasses(vararg classes: Class<*>): TestCordappImpl = cordappForPackages().withClasses(*classes)

fun getCallerClass(directCallerClass: KClass<*>): Class<*>? {
    val stackTrace = Throwable().stackTrace
    val index = stackTrace.indexOfLast { it.className == directCallerClass.java.name }
    if (index == -1) return null
    return try {
        Class.forName(stackTrace[index + 1].className)
    } catch (e: ClassNotFoundException) {
        null
    }
}

fun getCallerPackage(directCallerClass: KClass<*>): String? = getCallerClass(directCallerClass)?.`package`?.name

/**
 * Squashes child packages if the parent is present. Example: ["com.foo", "com.foo.bar"] into just ["com.foo"].
 */
fun simplifyScanPackages(scanPackages: Iterable<String>): Set<String> {
    return scanPackages.sorted().fold(emptySet()) { soFar, packageName ->
        when {
            soFar.isEmpty() -> setOf(packageName)
            packageName.startsWith("${soFar.last()}.") -> soFar
            else -> soFar + packageName
        }
    }
}

fun TestCordappImpl.packageAsJar(file: Path) {
    // Don't mention "classes" in the error message as that feature is only available internally
    require(packages.isNotEmpty() || classes.isNotEmpty()) { "At least one package must be specified" }

    val scanResult = ClassGraph()
            .whitelistPackages(*packages.toTypedArray())
            .whitelistClasses(*classes.map { it.name }.toTypedArray())
            .scan()

    scanResult.use {
        val manifest = createTestManifest(name, title, version, vendor, targetVersion, cordappVersion)
        JarOutputStream(file.outputStream()).use { jos ->
            val time = FileTime.from(Instant.EPOCH)
            val manifestEntry = ZipEntry(JarFile.MANIFEST_NAME).setCreationTime(time).setLastAccessTime(time).setLastModifiedTime(time)
            jos.putNextEntry(manifestEntry)
            manifest.write(BufferedOutputStream(jos))
            jos.closeEntry()

            // The same resource may be found in different locations (this will happen when running from gradle) so just
            // pick the first one found.
            scanResult.allResources.asMap().forEach { path, resourceList ->
                val entry = ZipEntry(path).setCreationTime(time).setLastAccessTime(time).setLastModifiedTime(time)
                jos.putNextEntry(entry)
                resourceList[0].open().use { it.copyTo(jos) }
                jos.closeEntry()
            }
        }
    }
}

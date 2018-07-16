package net.corda.testing.node.internal

import net.corda.core.internal.div
import net.corda.testing.driver.TestCorDapp
import java.io.File
import java.net.URL
import java.nio.file.Path

internal class TestCordappBuilder private constructor(override val name: String, override val version: String, override val vendor: String, override val title: String, private val willResourceBeAddedToCorDapp: (String, URL) -> Boolean, private val jarEntries: Set<JarEntryInfo>) : TestCorDapp.Builder {

    constructor(name: String, version: String, vendor: String, title: String, classes: Set<Class<*>>, willResourceBeAddedToCorDapp: (String, URL) -> Boolean) : this(name, version, vendor, title, willResourceBeAddedToCorDapp, jarEntriesFromClasses(classes))

    companion object {
        // TODO sollecitom check for Gradle and add to `productionPathSegments` // "main/${info.clazz.packageName.packageToPath()}"
        private val productionPathSegments = setOf("out${File.separator}production${File.separator}classes")
        private val excludedCordaPackages = setOf("net.corda.core", "net.corda.node", "net.corda.finance")

        fun filterTestCorDappClass(fullyQualifiedName: String, url: URL): Boolean {

            return isTestResource(url) || !isInExcludedCordaPackage(fullyQualifiedName)
        }

        private fun isTestResource(url: URL): Boolean {

            return productionPathSegments.none { url.toString().contains(it) }
        }

        private fun isInExcludedCordaPackage(packageName: String): Boolean {

            return excludedCordaPackages.any { packageName.startsWith(it) }
        }

        private fun jarEntriesFromClasses(classes: Set<Class<*>>): Set<JarEntryInfo> {

            val illegal = classes.filter { it.protectionDomain?.codeSource?.location == null }
            if (illegal.isNotEmpty()) {
                throw IllegalArgumentException("Some classes do not have a location, typically because they are part of Java or Kotlin. Offending types were: ${illegal.joinToString(", ", "[", "]") { it.simpleName }}")
            }
            return classes.map(Class<*>::jarEntryInfo).toSet()
        }
    }

    override val classes: Set<Class<*>> = jarEntries.filterIsInstance(JarEntryInfo.ClassJarEntryInfo::class.java).map(JarEntryInfo.ClassJarEntryInfo::clazz).toSet()

    override val resources: Set<URL> = jarEntries.map(JarEntryInfo::url).toSet()

    override fun withName(name: String): TestCorDapp.Builder = TestCordappBuilder(name, version, vendor, title, classes, willResourceBeAddedToCorDapp)

    override fun withTitle(title: String): TestCorDapp.Builder = TestCordappBuilder(name, version, vendor, title, classes, willResourceBeAddedToCorDapp)

    override fun withVersion(version: String): TestCorDapp.Builder = TestCordappBuilder(name, version, vendor, title, classes, willResourceBeAddedToCorDapp)

    override fun withVendor(vendor: String): TestCorDapp.Builder = TestCordappBuilder(name, version, vendor, title, classes, willResourceBeAddedToCorDapp)

    override fun withClasses(classes: Set<Class<*>>): TestCorDapp.Builder = TestCordappBuilder(name, version, vendor, title, classes, willResourceBeAddedToCorDapp)

    override fun plusPackages(pckgs: Set<String>): TestCorDapp.Builder {
        // TODO sollecitom perhaps inject the TestCorDappHelper or reference it
        return withClasses(pckgs.map { allClassesForPackage(it) }.fold(classes) { all, packageClasses -> all + packageClasses })
    }

    override fun minusPackages(pckgs: Set<String>): TestCorDapp.Builder {
        // TODO sollecitom perhaps inject the TestCorDappHelper or reference it
        return withClasses(pckgs.map { allClassesForPackage(it) }.fold(classes) { all, packageClasses -> all - packageClasses })
    }

    override fun packageAsJarWithPath(jarFilePath: Path) = jarEntries.packageToCorDapp(jarFilePath, name, version, vendor, title, willResourceBeAddedToCorDapp)

    override fun packageAsJarInDirectory(parentDirectory: Path) = packageAsJarWithPath(parentDirectory / defaultJarName())

    // TODO sollecitom change so that whitespaces are avoided inside the JAR's name
    private fun defaultJarName(): String = "$name.jar"

    sealed class JarEntryInfo(val fullyQualifiedName: String, val url: URL) {

        class ClassJarEntryInfo(val clazz: Class<*>) : JarEntryInfo(clazz.name, clazz.classFileURL())

        class ResourceJarEntryInfo(fullyQualifiedName: String, url: URL) : JarEntryInfo(fullyQualifiedName, url)

        operator fun component1(): String = fullyQualifiedName

        operator fun component2(): URL = url
    }
}
package net.corda.testing.node.internal

import net.corda.core.internal.div
import net.corda.testing.driver.TestCorDapp
import java.io.File
import java.net.URL
import java.nio.file.Path

internal class MutableTestCorDapp private constructor(override val name: String, override val version: String, override val vendor: String, override val title: String, private val willResourceBeAddedToCorDapp: (String, URL) -> Boolean, private val jarEntries: Set<JarEntryInfo>) : TestCorDapp.Mutable {

    constructor(name: String, version: String, vendor: String, title: String, classes: Set<Class<*>>, willResourceBeAddedToCorDapp: (String, URL) -> Boolean) : this(name, version, vendor, title, willResourceBeAddedToCorDapp, jarEntriesFromClasses(classes))

    companion object {

        private const val jarExtension = ".jar"
        private const val whitespace = " "
        private const val whitespaceReplacement = "_"
        private val productionPathSegments = setOf<(String) -> String>({ "out${File.separator}production${File.separator}classes" }, { fullyQualifiedName -> "main${File.separator}${fullyQualifiedName.packageToJarPath()}" })
        private val excludedCordaPackages = setOf("net.corda.core", "net.corda.node")

        fun filterTestCorDappClass(fullyQualifiedName: String, url: URL): Boolean {

            return isTestResource(fullyQualifiedName, url) || !isInExcludedCordaPackage(fullyQualifiedName)
        }

        private fun isTestResource(fullyQualifiedName: String, url: URL): Boolean {

            return productionPathSegments.map { it.invoke(fullyQualifiedName) }.none { url.toString().contains(it) }
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

    override fun withName(name: String) = MutableTestCorDapp(name, version, vendor, title, classes, willResourceBeAddedToCorDapp)

    override fun withTitle(title: String) = MutableTestCorDapp(name, version, vendor, title, classes, willResourceBeAddedToCorDapp)

    override fun withVersion(version: String) = MutableTestCorDapp(name, version, vendor, title, classes, willResourceBeAddedToCorDapp)

    override fun withVendor(vendor: String) = MutableTestCorDapp(name, version, vendor, title, classes, willResourceBeAddedToCorDapp)

    override fun withClasses(classes: Set<Class<*>>) = MutableTestCorDapp(name, version, vendor, title, classes, willResourceBeAddedToCorDapp)

    override fun plusPackages(pckgs: Set<String>) = withClasses(pckgs.map { allClassesForPackage(it) }.fold(classes) { all, packageClasses -> all + packageClasses })

    override fun minusPackages(pckgs: Set<String>) = withClasses(pckgs.map { allClassesForPackage(it) }.fold(classes) { all, packageClasses -> all - packageClasses })

    override fun plusResource(fullyQualifiedName: String, url: URL): TestCorDapp.Mutable = MutableTestCorDapp(name, version, vendor, title, willResourceBeAddedToCorDapp, jarEntries + JarEntryInfo.ResourceJarEntryInfo(fullyQualifiedName, url))

    override fun minusResource(fullyQualifiedName: String, url: URL): TestCorDapp.Mutable = MutableTestCorDapp(name, version, vendor, title, willResourceBeAddedToCorDapp, jarEntries - JarEntryInfo.ResourceJarEntryInfo(fullyQualifiedName, url))

    override fun packageAsJarWithPath(jarFilePath: Path) = jarEntries.packageToCorDapp(jarFilePath, name, version, vendor, title, willResourceBeAddedToCorDapp)

    override fun packageAsJarInDirectory(parentDirectory: Path): Path = (parentDirectory / defaultJarName()).also { packageAsJarWithPath(it) }

    private fun defaultJarName(): String = "${name}_$version$jarExtension".replace(whitespace, whitespaceReplacement)
}
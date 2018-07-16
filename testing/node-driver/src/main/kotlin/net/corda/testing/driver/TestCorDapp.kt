package net.corda.testing.driver

import net.corda.testing.node.internal.TestCordappBuilder
import java.net.URL
import java.nio.file.Path

interface TestCorDapp {

    val name: String
    val title: String
    val version: String
    val vendor: String

    val classes: Set<Class<*>>

    val resources: Set<URL>

    fun packageAsJarInDirectory(parentDirectory: Path)

    fun packageAsJarWithPath(jarFilePath: Path)

    companion object {

        // TODO sollecitom change this for apparently it doesn't play well with usage from Java...
        fun builder(name: String, version: String, vendor: String = "R3", title: String = name, classes: Set<Class<*>> = emptySet(), willResourceBeAddedBeToCorDapp: (fullyQualifiedName: String, url: URL) -> Boolean = TestCordappBuilder.Companion::filterTestCorDappClass): TestCorDapp.Builder {

            return TestCordappBuilder(name, version, vendor, title, classes, willResourceBeAddedBeToCorDapp)
        }
    }

    // TODO sollecitom add support for resource files
    interface Builder : TestCorDapp {

        fun withName(name: String): TestCorDapp.Builder

        fun withTitle(title: String): TestCorDapp.Builder

        fun withVersion(version: String): TestCorDapp.Builder

        fun withVendor(vendor: String): TestCorDapp.Builder

        fun withClasses(classes: Set<Class<*>>): TestCorDapp.Builder

        fun plusPackages(pckgs: Set<String>): TestCorDapp.Builder

        fun minusPackages(pckgs: Set<String>): TestCorDapp.Builder

        fun plusPackage(pckg: String): TestCorDapp.Builder = plusPackages(setOf(pckg))

        fun minusPackage(pckg: String): TestCorDapp.Builder = minusPackages(setOf(pckg))

        fun plusPackage(pckg: Package): TestCorDapp.Builder = plusPackages(pckg.name)

        fun minusPackage(pckg: Package): TestCorDapp.Builder = minusPackages(pckg.name)

        operator fun plus(clazz: Class<*>): TestCorDapp.Builder = withClasses(classes + clazz)

        operator fun minus(clazz: Class<*>): TestCorDapp.Builder = withClasses(classes - clazz)

        fun plusPackages(pckg: String, vararg pckgs: String): TestCorDapp.Builder = plusPackages(setOf(pckg, *pckgs))

        fun plusPackages(pckg: Package, vararg pckgs: Package): TestCorDapp.Builder = minusPackages(setOf(pckg, *pckgs).map { it.name }.toSet())

        fun minusPackages(pckg: String, vararg pckgs: String): TestCorDapp.Builder = minusPackages(setOf(pckg, *pckgs))

        fun minusPackages(pckg: Package, vararg pckgs: Package): TestCorDapp.Builder = minusPackages(setOf(pckg, *pckgs).map { it.name }.toSet())
    }
}
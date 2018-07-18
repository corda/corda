package net.corda.testing.driver

import net.corda.testing.node.internal.MutableTestCorDapp
import java.net.URL
import java.nio.file.Path

// TODO add support for CorDapps' config files
interface TestCorDapp {

    val name: String
    val title: String
    val version: String
    val vendor: String

    val classes: Set<Class<*>>

    val resources: Set<URL>

    fun packageAsJarInDirectory(parentDirectory: Path)

    fun packageAsJarWithPath(jarFilePath: Path)

    class Factory {
        companion object {

            @JvmStatic
            fun create(name: String, version: String, vendor: String = "R3", title: String = name, classes: Set<Class<*>> = emptySet(), willResourceBeAddedBeToCorDapp: (fullyQualifiedName: String, url: URL) -> Boolean = MutableTestCorDapp.Companion::filterTestCorDappClass): TestCorDapp.Mutable {

                return MutableTestCorDapp(name, version, vendor, title, classes, willResourceBeAddedBeToCorDapp)
            }
        }
    }

    interface Mutable : TestCorDapp {

        fun withName(name: String): TestCorDapp.Mutable

        fun withTitle(title: String): TestCorDapp.Mutable

        fun withVersion(version: String): TestCorDapp.Mutable

        fun withVendor(vendor: String): TestCorDapp.Mutable

        fun withClasses(classes: Set<Class<*>>): TestCorDapp.Mutable

        fun plusPackages(pckgs: Set<String>): TestCorDapp.Mutable

        fun minusPackages(pckgs: Set<String>): TestCorDapp.Mutable

        fun plusPackage(pckg: String): TestCorDapp.Mutable = plusPackages(setOf(pckg))

        fun minusPackage(pckg: String): TestCorDapp.Mutable = minusPackages(setOf(pckg))

        fun plusPackage(pckg: Package): TestCorDapp.Mutable = plusPackages(pckg.name)

        fun minusPackage(pckg: Package): TestCorDapp.Mutable = minusPackages(pckg.name)

        operator fun plus(clazz: Class<*>): TestCorDapp.Mutable = withClasses(classes + clazz)

        operator fun minus(clazz: Class<*>): TestCorDapp.Mutable = withClasses(classes - clazz)

        fun plusPackages(pckg: String, vararg pckgs: String): TestCorDapp.Mutable = plusPackages(setOf(pckg, *pckgs))

        fun plusPackages(pckg: Package, vararg pckgs: Package): TestCorDapp.Mutable = minusPackages(setOf(pckg, *pckgs).map { it.name }.toSet())

        fun minusPackages(pckg: String, vararg pckgs: String): TestCorDapp.Mutable = minusPackages(setOf(pckg, *pckgs))

        fun minusPackages(pckg: Package, vararg pckgs: Package): TestCorDapp.Mutable = minusPackages(setOf(pckg, *pckgs).map { it.name }.toSet())

        fun plusResource(fullyQualifiedName: String, url: URL): TestCorDapp.Mutable

        fun minusResource(fullyQualifiedName: String, url: URL): TestCorDapp.Mutable
    }
}
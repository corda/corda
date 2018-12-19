package net.corda.testing.node.internal

import net.corda.testing.node.TestCordapp
import kotlin.reflect.KClass

/**
 * Reference to the finance CorDapp in this repo. The metadata is taken directly from finance/build.gradle, including the fact that the jar
 * is signed. If you need an unsigned jar then use `cordappWithPackages("net.corda.finance")`.
 */
// TODO We can't use net.corda.finance as it's only containined by the finance:isolated module
@JvmField
val FINANCE_CORDAPP: TestCordappImpl = findCordapp("net.corda.finance.internal")

fun cordappsForPackages(vararg packageNames: String): Set<CustomCordapp> = cordappsForPackages(packageNames.asList())

fun cordappsForPackages(packageNames: Iterable<String>): Set<CustomCordapp> {
    return simplifyScanPackages(packageNames).mapTo(HashSet()) { cordappWithPackages(it) }
}

/**
 * Create a *custom* CorDapp which contains all the classes and resoures located in the given packages. The CorDapp's metadata will be the
 * default values as defined in the [CustomCordapp] c'tor. Use the `copy` to change them. This means the metadata will *not* be the one defined
 * in the original CorDapp(s) that the given packages may represent. If this is not what you want then use [findCordapp] instead.
 */
fun cordappWithPackages(vararg packageNames: String): CustomCordapp = CustomCordapp(packages = simplifyScanPackages(packageNames.asList()))

/** Create a *custom* CorDapp which contains just the given classes. */
// TODO Rename to cordappWithClasses
fun cordappForClasses(vararg classes: Class<*>): CustomCordapp = CustomCordapp(packages = emptySet(), classes = classes.toSet())

/**
 * Find the single CorDapp jar on the current classpath which contains the given package. This is a convenience method for
 * [TestCordapp.Factory.findCordapp] but returns the internal [TestCordappImpl].
 */
fun findCordapp(packageName: String, config: Map<String, Any> = emptyMap()): TestCordappImpl {
    return TestCordapp.Factory.findCordapp(packageName, config) as TestCordappImpl
}

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

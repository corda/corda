package net.corda.testing.node

import net.corda.core.DoNotImplement
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.testing.node.internal.TestCordappImpl
import net.corda.testing.node.internal.simplifyScanPackages

/**
 * Represents information about a CorDapp. Used to generate CorDapp JARs in tests.
 */
@DoNotImplement
interface TestCordapp {
    /** Returns the name, defaults to "test-cordapp" if not specified. */
    val name: String

    /** Returns the title, defaults to "test-title" if not specified. */
    val title: String

    /** Returns the version string, defaults to "1.0" if not specified. */
    val version: String

    /** Returns the vendor string, defaults to "Corda" if not specified. */
    val vendor: String

    /** Returns the target platform version, defaults to the current platform version if not specified. */
    val targetVersion: Int

    /** Returns the set of package names scanned for this test CorDapp. */
    val packages: Set<String>

    /** Return a copy of this [TestCordapp] but with the specified name. */
    fun withName(name: String): TestCordapp

    /** Return a copy of this [TestCordapp] but with the specified title. */
    fun withTitle(title: String): TestCordapp

    /** Return a copy of this [TestCordapp] but with the specified version string. */
    fun withVersion(version: String): TestCordapp

    /** Return a copy of this [TestCordapp] but with the specified vendor string. */
    fun withVendor(vendor: String): TestCordapp

    /** Return a copy of this [TestCordapp] but with the specified target platform version. */
    fun withTargetVersion(targetVersion: Int): TestCordapp

    class Factory {
        companion object {
            @JvmStatic
            fun fromPackages(vararg packageNames: String): TestCordapp = fromPackages(packageNames.asList())

            /**
             * Create a [TestCordapp] object by scanning the given packages. The meta data on the CorDapp will be the
             * default values, which can be specified with the wither methods.
             */
            @JvmStatic
            fun fromPackages(packageNames: Collection<String>): TestCordapp {
                return TestCordappImpl(
                        name = "test-cordapp",
                        version = "1.0",
                        vendor = "Corda",
                        title = "test-title",
                        targetVersion = PLATFORM_VERSION,
                        packages = simplifyScanPackages(packageNames),
                        classes = emptySet()
                )
            }
        }
    }
}

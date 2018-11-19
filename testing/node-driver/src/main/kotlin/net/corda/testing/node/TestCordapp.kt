package net.corda.testing.node

import net.corda.core.DoNotImplement
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.testing.node.internal.TestCordappImpl
import net.corda.testing.node.internal.simplifyScanPackages
import java.nio.file.Path

/**
 * Represents information about a CorDapp. Used to generate CorDapp JARs in tests.
 */
@DoNotImplement
interface TestCordapp {
    /** Returns the name, defaults to "test-name" if not specified. */
    val name: String

    /** Returns the title, defaults to "test-title" if not specified. */
    val title: String

    /** Returns the version string, defaults to "1.0" if not specified. */
    val version: String

    /** Returns the vendor string, defaults to "test-vendor" if not specified. */
    val vendor: String

    /** Returns the target platform version, defaults to the current platform version if not specified. */
    val targetVersion: Int

    val implementationVersion: String

    /** Returns the config for this CorDapp, defaults to empty if not specified. */
    val config: Map<String, Any>

    /** Returns the set of package names scanned for this test CorDapp. */
    val packages: Set<String>

    /** Returns whether the CorDapp should be jar signed. */
    val signJar: Boolean

    /** Returns the contract version, default to 1 if not specified. */
    val cordaContractVersion: Int

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

    /** Returns a copy of this [TestCordapp] but with the specified CorDapp config. */
    fun withConfig(config: Map<String, Any>): TestCordapp

    /** Returns a signed copy of this [TestCordapp].
     *  Optionally can pass in the location of an existing java key store to use */
    fun signJar(keyStorePath: Path? = null): TestCordappImpl

    fun withCordaContractVersion(version: Int): TestCordappImpl

    fun withImplementationVersion(version: String): TestCordapp


    class Factory {
        companion object {
            /**
             * Create a [TestCordapp] object by scanning the given packages. The meta data on the CorDapp will be the
             * default values, which can be changed with the wither methods.
             */
            @JvmStatic
            fun fromPackages(vararg packageNames: String): TestCordapp = fromPackages(packageNames.asList())

            /**
             * Create a [TestCordapp] object by scanning the given packages. The meta data on the CorDapp will be the
             * default values, which can be changed with the wither methods.
             */
            @JvmStatic
            fun fromPackages(packageNames: Collection<String>, implementationVersion: String = "1.0"): TestCordapp {
                return TestCordappImpl(
                        name = "test-name",
                        version = "1.0",
                        vendor = "test-vendor",
                        title = "test-title",
                        targetVersion = PLATFORM_VERSION,
                        implementationVersion = implementationVersion,
                        config = emptyMap(),
                        packages = simplifyScanPackages(packageNames),
                        classes = emptySet()
                )
            }
        }
    }
}

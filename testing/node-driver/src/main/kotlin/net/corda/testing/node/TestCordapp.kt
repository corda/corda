package net.corda.testing.node

import net.corda.core.DoNotImplement
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.node.internal.ScanPackageTestCordapp
import net.corda.testing.node.internal.UriTestCordapp
import java.net.URI
import java.nio.file.Path

/**
 * Encapsulates a CorDapp that exists on the current classpath, which can be pulled in for testing. Use [TestCordapp.findCordapp]
 * to locate an existing CorDapp.
 *
 * This is a replacement API to [DriverParameters.extraCordappPackagesToScan] and [MockNetwork.cordappPackages] as they create custom jars
 * which do not preserve any CorDapp metadata.
 *
 * @see DriverParameters.cordappsForAllNodes
 * @see NodeParameters.additionalCordapps
 * @see MockNetworkParameters.cordappsForAllNodes
 * @see MockNodeParameters.additionalCordapps
 */
@DoNotImplement
abstract class TestCordapp {
    /** Returns the config for on this CorDapp, defaults to empty if not specified. */
    abstract val config: Map<String, Any>

    /** Returns a copy of this [TestCordapp] but with the specified CorDapp config. */
    abstract fun withConfig(config: Map<String, Any>): TestCordapp

    /**
     * Returns a copy of this [TestCordapp] signed with a development signing key. The same signing key will be used for all signed
     * [TestCordapp]s. If the CorDapp jar is already signed, then the new jar created will its signing key replaced by the development key.
     */
    abstract fun asSigned(): TestCordapp

    companion object {
        /**
         * Scans the current classpath to find the CorDapp that contains the given package. All the CorDapp's metdata present in its
         * MANIFEST are inherited. If more than one location containing the package is found then an exception is thrown. An exception
         * is also thrown if no CorDapp is found.
         *
         * @param scanPackage The package name used to find the CorDapp. This does not need to be the root package of the CorDapp.
         */
        @JvmStatic
        fun findCordapp(scanPackage: String): TestCordapp = ScanPackageTestCordapp(scanPackage)

        /**
         * [URI] location to a CorDapp jar. This may be a path on the local file system or a URL to an external resource.
         *
         * A [Path] can be converted into a [URI] with [Path.toUri].
         */
        @JvmStatic
        fun of(uri: URI): TestCordapp = UriTestCordapp(uri)
    }
}

package net.corda.testing.node

import net.corda.core.DoNotImplement
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.node.internal.TestCordappImpl

/**
 * Encapsulates a CorDapp that exists on the current classpath, which can be pulled in for testing. Use [TestCordapp.Factory.findCordapp]
 * to locate an existing CorDapp.
 *
 * @see DriverParameters.cordappsForAllNodes
 * @see NodeParameters.additionalCordapps
 * @see MockNetworkParameters.cordappsForAllNodes
 * @see MockNodeParameters.additionalCordapps
 */
@DoNotImplement
interface TestCordapp {
    /** The package used to find the CorDapp. This may not be the root package of the CorDapp. */
    val scanPackage: String

    /** Returns the config to be applied on this CorDapp, defaults to empty if not specified. */
    val config: Map<String, Any>

    class Factory {
        companion object {
            /**
             * Scans the current classpath to find the CorDapp that contains the given package. All the CorDapp's metdata present in its
             * MANIFEST are inherited. If more than one location containing the package is found then an exception is thrown. An exception
             * is also thrown if no CorDapp is found.
             *
             * @param scanPackage The package name used to find the CorDapp. This does not need to be the root package.
             * @param config config to be applied with the CorDapp.
             */
            @JvmStatic
            @JvmOverloads
            fun findCordapp(scanPackage: String, config: Map<String, Any> = emptyMap()): TestCordapp {
                return TestCordappImpl(scanPackage = scanPackage, config = config)
            }
        }
    }
}

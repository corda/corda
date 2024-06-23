package net.corda.testing.driver.junit.jupiter

import net.corda.core.identity.CordaX500Name
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.NotaryHandle
import org.junit.jupiter.api.extension.InvocationInterceptor
import java.time.Duration

/**
 * Interface for a Junit Jupiter Extension to
 * start Corda nodes for integration tests.
 */
interface CordaDriverJunitJupiter: InvocationInterceptor {
    /**
     * List of [NodeParameters] elements, where each of them
     * configures a single Corda node.
     */
    val parametersForNodes: List<NodeParameters>

    /**
     * List of the nodes' [CordaX500Name]s
     */
    val cordaX500Names: List<CordaX500Name>
    /**
     * Driver parameters such as Cordapps to load,
     * network parameters ...
     */
    val driverParameters: DriverParameters
    /**
     * Maximum time to wait for the Corda future of
     * the Corda start node process to finish until
     * an error is thrown. Null value allowed which
     * is treated as no time limitation.
     */
    val maxTimeForNodeToBeStarted: Duration?
    /**
     * If true, driver files are stored in a temporary
     * directory which will be deleted after the test
     * has been executed.
     */
    val useTempDriverDirectory: Boolean
    /**
     * Get a list of all Corda node handles, if they are available
     * or null if not.
     */
    fun getCordaNodeHandlesOrNull(): List<NodeHandle>?
    /**
     * Get a list of all Corda node handles, if they are available
     * or throw an exception if not.
     */
    fun getCordaNodeHandlesOrThrow(): List<NodeHandle>
    /**
     * Get default notary handle or null
     * if not available.
     */
    fun getDefaultCordaNotaryHandleOrNull(): NotaryHandle?
    /**
     * Get default notary handle or throw
     * an exception if not.
     */
    fun getDefaultCordaNotaryHandleOrThrow(): NotaryHandle
}

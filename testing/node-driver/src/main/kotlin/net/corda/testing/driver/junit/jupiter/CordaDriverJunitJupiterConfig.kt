package net.corda.testing.driver.junit.jupiter

import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import java.time.Duration

/**
 * A class to store the configuration for the [CordaDriverJunitJupiterImpl] driver.
 * @param parametersForNodes List of [NodeParameters] elements, where each of them
 * configures a single Corda node.
 * @param driverParameters Driver parameters such as Cordapps to load,
 * network parameters ...
 * @param maxTimeForNodeToBeStarted Maximum time to wait for the Corda future of
 * the Corda start node process to finish until
 * an error is thrown. Null value allowed which
 * is treated as no time limitation.
 * @param useTempDriverDirectory If true, driver files are stored in a temporary
 * directory which will be deleted after the test
 * has been executed.
 */
class CordaDriverJunitJupiterConfig(
    val parametersForNodes: List<NodeParameters>,
    val driverParameters: DriverParameters,
    val maxTimeForNodeToBeStarted: Duration? = null,
    val useTempDriverDirectory: Boolean = true
) {
    /**
     * Build an [CordaDriverJunitJupiterImpl] instance from this [CordaDriverJunitJupiterConfig] instance.
     */
    fun buildDriver(): CordaDriverJunitJupiterImpl = CordaDriverJunitJupiterImpl(this)
}

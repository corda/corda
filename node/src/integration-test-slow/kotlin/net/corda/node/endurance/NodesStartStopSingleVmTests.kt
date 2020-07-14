package net.corda.node.endurance

import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class NodesStartStopSingleVmTests(@Suppress("unused") private val iteration: Int) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "iteration = {0}")
        fun iterations(): Iterable<Array<Int>> {
            return (1..60).map { arrayOf(it) }
        }
    }

    @Test(timeout = 300_000)
    fun nodesStartStop() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME)
            val bob = startNode(providedName = BOB_NAME)
            alice.getOrThrow()
            bob.getOrThrow()
        }
    }
}
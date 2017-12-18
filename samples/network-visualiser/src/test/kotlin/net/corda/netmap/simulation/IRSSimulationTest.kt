package net.corda.netmap.simulation

import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.LogHelper
import org.junit.Test

class IRSSimulationTest {
    // TODO: These tests should be a lot more complete.
    @Test
    fun `runs to completion`() {
        LogHelper.setLevel("+messages") // FIXME: Don't manipulate static state in tests.
        val sim = IRSSimulation(false, false, null)
        val future = sim.start()
        while (!future.isDone) {
            sim.iterate()
        }
        future.getOrThrow()
    }
}

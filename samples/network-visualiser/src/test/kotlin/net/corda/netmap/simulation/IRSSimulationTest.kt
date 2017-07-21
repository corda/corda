package net.corda.netmap.simulation

import net.corda.core.internal.concurrent.getOrThrow
import net.corda.testing.LogHelper
import org.junit.Test

class IRSSimulationTest {
    // TODO: These tests should be a lot more complete.

    @Test fun `runs to completion`() {
        LogHelper.setLevel("+messages")
        val sim = IRSSimulation(false, false, null)
        val future = sim.start()
        while (!future.isDone()) sim.iterate()
        future.getOrThrow()
    }
}

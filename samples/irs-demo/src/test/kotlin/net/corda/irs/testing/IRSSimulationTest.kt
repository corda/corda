package net.corda.irs.testing

import com.google.common.base.Throwables
import net.corda.core.utilities.LogHelper
import net.corda.simulation.IRSSimulation
import org.junit.Test

class IRSSimulationTest {
    // TODO: These tests should be a lot more complete.

    @Test fun `runs to completion`() {
        LogHelper.setLevel("+messages")
        val sim = IRSSimulation(false, false, null)
        val future = sim.start()
        while (!future.isDone) sim.iterate()
        try {
            future.get()
        } catch(e: Throwable) {
            throw Throwables.getRootCause(e)
        }
    }
}

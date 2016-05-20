package core.testing

import com.google.common.base.Throwables
import core.utilities.BriefLogFormatter
import node.internal.testing.IRSSimulation
import org.junit.Test

class IRSSimulationTest {
    // TODO: These tests should be a lot more complete.

    @Test fun `runs to completion`() {
        BriefLogFormatter.initVerbose("messaging")
        val sim = IRSSimulation(false, null)
        val future = sim.start()
        while (!future.isDone) sim.iterate()
        try {
            future.get()
        } catch(e: Throwable) {
            throw Throwables.getRootCause(e)
        }
    }
}

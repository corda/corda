package core.testing

import com.google.common.base.Throwables
import core.utilities.BriefLogFormatter
import org.junit.Test

/**
 * This test doesn't check anything except that the simulation finishes and there are no exceptions at any point.
 * The details of the IRS contract are verified in other unit tests.
 */
class IRSSimulationTest {
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
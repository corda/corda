package com.r3corda.core.testing

import com.google.common.base.Throwables
import com.r3corda.core.utilities.BriefLogFormatter
import com.r3corda.node.internal.testing.IRSSimulation
import org.junit.Test

class IRSSimulationTest {
    // TODO: These tests should be a lot more complete.

    @Test fun `runs to completion`() {
        BriefLogFormatter.initVerbose("messaging")
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

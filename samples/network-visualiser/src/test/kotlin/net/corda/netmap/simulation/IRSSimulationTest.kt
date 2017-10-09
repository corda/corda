package net.corda.netmap.simulation

import net.corda.core.utilities.getOrThrow
import net.corda.testing.LogHelper
import net.corda.testing.setCordappPackages
import net.corda.testing.unsetCordappPackages
import org.junit.After
import org.junit.Before
import org.junit.Test

class IRSSimulationTest {
    // TODO: These tests should be a lot more complete.

    @Before
    fun setup() {
        setCordappPackages("net.corda.irs.contract")
    }

    @After
    fun tearDown() {
        unsetCordappPackages()
    }

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

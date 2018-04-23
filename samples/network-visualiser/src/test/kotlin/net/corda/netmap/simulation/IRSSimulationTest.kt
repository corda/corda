/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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

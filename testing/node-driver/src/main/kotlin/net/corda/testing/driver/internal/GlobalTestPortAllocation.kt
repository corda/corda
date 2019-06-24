package net.corda.testing.driver.internal

import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.PortAllocation.SharedMemoryIncremental.Companion.INSTANCE

fun incrementalPortAllocation(): PortAllocation {
    return INSTANCE
}


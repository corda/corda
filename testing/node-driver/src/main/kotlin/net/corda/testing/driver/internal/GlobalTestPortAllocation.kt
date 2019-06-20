package net.corda.testing.driver.internal

import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.SharedMemoryPortAllocation

fun incrementalPortAllocation(ignored: Int): PortAllocation {
    return SharedMemoryPortAllocation.INSTANCE
}


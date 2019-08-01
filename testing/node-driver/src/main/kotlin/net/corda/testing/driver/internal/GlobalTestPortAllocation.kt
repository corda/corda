package net.corda.testing.driver.internal

import net.corda.testing.driver.PortAllocation

fun incrementalPortAllocation(): PortAllocation {
    return PortAllocation.defaultAllocator
}


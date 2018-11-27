package net.corda.testing.driver.internal

import net.corda.testing.driver.PortAllocation

object GlobalPortAllocation : PortAllocation.Incremental(startingPort = startingPort) {

    const val enablingEnvVar = "CORDA_TEST_GLOBAL_PORT_ALLOCATION_ENABLED"
    const val startingPortEnvVariable = "CORDA_TEST_GLOBAL_PORT_ALLOCATION_STARTING_PORT"
    const val startingPortDefaultValue = 5000
}

fun incrementalPortAllocation(startingPortIfNoEnv: Int): PortAllocation {

    return when {
        System.getenv(GlobalPortAllocation.enablingEnvVar)?.toBoolean() == true -> GlobalPortAllocation
        else -> PortAllocation.Incremental(startingPortIfNoEnv)
    }
}

private val startingPort: Int = System.getenv(GlobalPortAllocation.startingPortEnvVariable)?.toIntOrNull() ?: GlobalPortAllocation.startingPortDefaultValue
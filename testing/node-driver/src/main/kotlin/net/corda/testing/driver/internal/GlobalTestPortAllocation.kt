package net.corda.testing.driver.internal

import net.corda.testing.driver.PortAllocation

fun incrementalPortAllocation(startingPortIfNoEnv: Int): PortAllocation {

    return when {
        System.getenv(GlobalTestPortAllocation.enablingEnvVar)?.toBoolean() == true -> GlobalTestPortAllocation
        else -> PortAllocation.Incremental(startingPortIfNoEnv)
    }
}

private object GlobalTestPortAllocation : PortAllocation.Incremental(startingPort = startingPort) {

    const val enablingEnvVar = "CORDA_TEST_GLOBAL_PORT_ALLOCATION_ENABLED"
    const val startingPortEnvVariable = "CORDA_TEST_GLOBAL_PORT_ALLOCATION_STARTING_PORT"
    const val startingPortDefaultValue = 5000
}

private val startingPort: Int = System.getenv(GlobalTestPortAllocation.startingPortEnvVariable)?.toIntOrNull() ?: GlobalTestPortAllocation.startingPortDefaultValue
package net.corda.testing.driver.internal

import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.internal.GlobalTestPortAllocation.enablingEnvVar
import net.corda.testing.driver.internal.GlobalTestPortAllocation.enablingSystemProperty
import net.corda.testing.driver.internal.GlobalTestPortAllocation.startingPortEnvVariable
import net.corda.testing.driver.internal.GlobalTestPortAllocation.startingPortSystemProperty

fun incrementalPortAllocation(startingPortIfNoEnv: Int): PortAllocation {

    return when {
        System.getProperty(enablingSystemProperty)?.toBoolean() ?: System.getenv(enablingEnvVar)?.toBoolean() == true -> GlobalTestPortAllocation
        else -> PortAllocation.Incremental(startingPortIfNoEnv)
    }
}

private object GlobalTestPortAllocation : PortAllocation.Incremental(startingPort = startingPort) {

    const val enablingEnvVar = "CORDA_TEST_GLOBAL_PORT_ALLOCATION_ENABLED"
    const val startingPortEnvVariable = "CORDA_TEST_GLOBAL_PORT_ALLOCATION_STARTING_PORT"
    val enablingSystemProperty = enablingEnvVar.toLowerCase().replace("_", ".")
    val startingPortSystemProperty = startingPortEnvVariable.toLowerCase().replace("_", ".")
    const val startingPortDefaultValue = 5000
}

private val startingPort: Int = System.getProperty(startingPortSystemProperty)?.toIntOrNull() ?: System.getenv(startingPortEnvVariable)?.toIntOrNull() ?: GlobalTestPortAllocation.startingPortDefaultValue
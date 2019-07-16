package net.corda.testing.driver.internal

import net.corda.testing.driver.PortAllocation

fun incrementalPortAllocation(startingPortIfNoEnv: Int): PortAllocation {

    return when {
        System.getProperty(enablingSystemProperty)?.toBoolean() ?: System.getenv(enablingEnvVar)?.toBoolean() == true -> GlobalTestPortAllocation
        else -> PortAllocation.Incremental(startingPortIfNoEnv)
    }
}

private object GlobalTestPortAllocation : PortAllocation.Incremental(startingPort = startingPort())

private const val enablingEnvVar = "TESTING_GLOBAL_PORT_ALLOCATION_ENABLED"
private const val startingPortEnvVariable = "TESTING_GLOBAL_PORT_ALLOCATION_STARTING_PORT"
private val enablingSystemProperty = enablingEnvVar.toLowerCase().replace("_", ".")
private val startingPortSystemProperty = startingPortEnvVariable.toLowerCase().replace("_", ".")
private const val startingPortDefaultValue = 5000


private fun startingPort(): Int {

    return System.getProperty(startingPortSystemProperty)?.toIntOrNull() ?: System.getenv(startingPortEnvVariable)?.toIntOrNull() ?: startingPortDefaultValue
}
package net.corda.testing

import junit.framework.AssertionFailedError
import org.junit.Test

open class CliBackwardsCompatibleTest(val clazz: Class<*>) {

    @Test(timeout=300_000)
	fun `should always be backwards compatible`() {
        checkBackwardsCompatibility(clazz)
    }

    fun checkBackwardsCompatibility(clazz: Class<*>) {
        val checker = CommandLineCompatibilityChecker()
        val checkResults = checker.checkCommandLineIsBackwardsCompatible(clazz)

        if (checkResults.isNotEmpty()) {
            val exceptionMessage = checkResults.map { it.message }.joinToString(separator = "\n")
            throw AssertionFailedError("Command line is not backwards compatible:\n$exceptionMessage")
        }
    }


}
package net.corda.testing

import junit.framework.AssertionFailedError

open class CliBackwardsCompatibleTest {


    fun checkBackwardsCompatibility(clazz: Class<*>) {
        val checker = CommandLineCompatibilityChecker()
        val checkResults = checker.checkCommandLineIsBackwardsCompatible(clazz)

        if (checkResults.isNotEmpty()) {
            val exceptionMessage= checkResults.map { it.message }.joinToString(separator = "\n")
            throw AssertionFailedError("Command line is not backwards compatible:\n$exceptionMessage")
        }
    }


}
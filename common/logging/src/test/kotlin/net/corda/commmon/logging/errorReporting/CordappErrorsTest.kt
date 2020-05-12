package net.corda.commmon.logging.errorReporting

import net.corda.common.logging.errorReporting.CordappErrors

class CordappErrorsTest : ErrorCodeTest<CordappErrors>(CordappErrors::class.java, true) {
    override val dataForCodes = mapOf(
            CordappErrors.MISSING_VERSION_ATTRIBUTE to listOf("test-attribute"),
            CordappErrors.INVALID_VERSION_IDENTIFIER to listOf(-1, "test-attribute"),
            CordappErrors.MULTIPLE_CORDAPPS_FOR_FLOW to listOf("MyTestFlow", "Jar 1, Jar 2"),
            CordappErrors.DUPLICATE_CORDAPPS_INSTALLED to listOf("TestCordapp", "testapp.jar", "testapp2.jar")
    )
}
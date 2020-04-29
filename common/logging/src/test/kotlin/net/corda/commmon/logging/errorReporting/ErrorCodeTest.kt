package net.corda.commmon.logging.errorReporting

import junit.framework.TestCase.assertFalse
import net.corda.common.logging.errorReporting.ErrorCode
import net.corda.common.logging.errorReporting.ErrorCodes
import net.corda.common.logging.errorReporting.ErrorResource
import net.corda.common.logging.errorReporting.ResourceBundleProperties
import org.junit.Test
import java.util.*
import kotlin.test.assertTrue

/**
 * Utility for testing that error code resource files behave as expected.
 *
 * This allows for testing that error messages are printed correctly if they are provided the correct parameters. The test will fail if any
 * of the parameters of the template are not filled in.
 *
 * To use, override the `dataForCodes` with a map from an error code enum value to a list of parameters the message template takes. If any
 * are missed, the test will fail.
 *
 * `printProperties`, if set to true, will print the properties out the resource files, with the error message filled in. This allows the
 * message to be inspected.
 */
abstract class ErrorCodeTest<T>(private val clazz: Class<T>,
                                private val printProperties: Boolean = false) where T: Enum<T>, T: ErrorCodes {

    abstract val dataForCodes: Map<T, List<Any>>

    private class TestError<T>(override val code: T,
                               override val parameters: List<Any>) : ErrorCode<T> where T: Enum<T>, T: ErrorCodes

    @Test(timeout = 300_000)
    fun `test error codes`() {
        for ((code, params) in dataForCodes) {
            val error = TestError(code, params)
            val resource = ErrorResource.fromErrorCode(error, "error-codes", Locale.forLanguageTag("en-US"))
            val message = resource.getErrorMessage(error.parameters.toTypedArray())
            assertFalse(
                    "The error message reported for code $code contains missing parameters",
                    message.contains("\\{.*}".toRegex())
            )
            val otherProperties = Triple(resource.shortDescription, resource.actionsToFix, resource.aliases)
            if (printProperties) {
                println("Data for $code")
                println("Error Message = $message")
                println("${ResourceBundleProperties.SHORT_DESCRIPTION} = ${otherProperties.first}")
                println("${ResourceBundleProperties.ACTIONS_TO_FIX} = ${otherProperties.second}")
                println("${ResourceBundleProperties.ALIASES} = ${otherProperties.third}")
                println("")
            }
        }
    }

    @Test(timeout = 300_000)
    fun `ensure all error codes tested`() {
        val expected = clazz.enumConstants.toSet()
        val actual = dataForCodes.keys.toSet()
        val missing = expected - actual
        assertTrue(missing.isEmpty(), "The following codes have not been tested: $missing")
    }
}
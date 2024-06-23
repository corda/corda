package net.corda.testing.driver.junit.jupiter

import kotlin.jvm.Throws

/**
 * An exception class to throw when the test is not properly set up and needs to be adjusted.
 * Use this instead of [IllegalArgumentException] to make clear that the test did not fail
 * due to bugs but just due to wrong setup
 * @param specificMessage The exception message to be set for a specific issue/failed requirement during test set up.
 * @param message The full exception message.
 */
class TestSetupException(
    val specificMessage: String,
    override val message: String = "$specificMessage This is not a problem of the code to be tested, but a problem of the test setup. The test needs to be adjusted/fixed!"
): Exception(message)

/**
 * Catch exception class instances of type [ExceptionType] thrown by [block] and rethrow
 * [TestSetupException].
 * @param block code to be executed.
 * @throws TestSetupException
 */
@Throws(TestSetupException::class)
inline fun <reified ExceptionType: Exception, T: Any> catchAndRethrowTestSetupException(
    block: () -> T?
): T? {
    return try {
        block()
    } catch(ex: Exception) {
        when(ex) {
            is ExceptionType -> throw TestSetupException(ex.message ?: "")
            else -> throw ex
        }
    }
}

/**
 * Catch exceptions class instance of type [ExceptionType] thrown by [block] and rethrow
 * [TestSetupException].
 * @param block code to be executed.
 * @throws TestSetupException
 */
@Throws(TestSetupException::class)
inline fun <reified ExceptionType: Exception> catchAndRethrowTestSetupException(block: () -> Unit) {
    catchAndRethrowTestSetupException<ExceptionType, Unit> { block() }
}

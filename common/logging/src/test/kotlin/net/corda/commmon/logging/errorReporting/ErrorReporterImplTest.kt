package net.corda.commmon.logging.errorReporting

import junit.framework.TestCase.assertEquals
import net.corda.common.logging.errorReporting.ErrorCode
import net.corda.common.logging.errorReporting.ErrorCodes
import net.corda.common.logging.errorReporting.ErrorContextProvider
import net.corda.common.logging.errorReporting.ErrorReporterImpl
import org.junit.After
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.slf4j.Logger
import java.text.DateFormat
import java.time.Instant
import java.util.*

class ErrorReporterImplTest {

    companion object {
        private const val TEST_URL = "test-url"
    }

    private val logs: MutableList<Any> = mutableListOf()

    private val loggerMock = Mockito.mock(Logger::class.java).also {
        Mockito.`when`(it.error(anyString())).then { logs.addAll(it.arguments) }
        Mockito.`when`(it.error(anyString(), any(Exception::class.java))).then { params -> logs.addAll(params.arguments) }
    }

    private val contextProvider: ErrorContextProvider  = object : ErrorContextProvider {
        override fun getURL(locale: Locale): String {
            return "$TEST_URL/${locale.toLanguageTag()}"
        }
    }

    private enum class TestNamespaces {
        TEST
    }

    private enum class TestErrors : ErrorCodes {
        CASE1,
        CASE2,
        CASE_3,
        CASE4;

        override val namespace = TestNamespaces.TEST.toString()
    }

    private val TEST_ERROR_1 = object : ErrorCode<TestErrors> {
        override val code = TestErrors.CASE1
        override val parameters = listOf<Any>()
    }

    private class TestError2(currentDate: Date) : ErrorCode<TestErrors> {
        override val code = TestErrors.CASE2
        override val parameters: List<Any> = listOf("foo", 1, currentDate)
    }

    private val TEST_ERROR_3 = object : ErrorCode<TestErrors> {
        override val code = TestErrors.CASE_3
        override val parameters = listOf<Any>()
    }

    private class TestError4(cause: Exception?) : Exception("This is test error 4", cause), ErrorCode<TestErrors> {
        override val code = TestErrors.CASE4
        override val parameters = listOf<Any>()
    }

    private fun createReporterImpl(localeTag: String?) : ErrorReporterImpl {
        val locale = if (localeTag != null) Locale.forLanguageTag(localeTag) else Locale.getDefault()
        return ErrorReporterImpl("errorReporting", locale, contextProvider)
    }

    @After
    fun tearDown() {
        logs.clear()
    }

    @Test(timeout = 300_000)
    fun `error codes logged correctly`() {
        val error = TEST_ERROR_1
        val testReporter = createReporterImpl("en-US")
        testReporter.report(error, loggerMock)
        assertEquals(listOf("This is a test message [Code: test-case1 URL: $TEST_URL/en-US]"), logs)
    }

    @Test(timeout = 300_00)
    fun `error code with parameters correctly reported`() {
        val currentDate = Date.from(Instant.now())
        val error = TestError2(currentDate)
        val testReporter = createReporterImpl("en-US")
        testReporter.report(error, loggerMock)
        val format = DateFormat.getDateInstance(DateFormat.LONG, Locale.forLanguageTag("en-US"))
        assertEquals(listOf("This is the second case with string foo, number 1, date ${format.format(currentDate)} [Code: test-case2 URL: $TEST_URL/en-US]"), logs)
    }

    @Test(timeout = 300_000)
    fun `locale used with no corresponding resource falls back to default`() {
        val error = TEST_ERROR_1
        val testReporter = createReporterImpl("fr-FR")
        testReporter.report(error, loggerMock)
        assertEquals(listOf("This is a test message [Code: test-case1 URL: $TEST_URL/fr-FR]"), logs)
    }

    @Test(timeout = 300_000)
    fun `locale with corresponding resource causes correct error to be printed`() {
        val error = TEST_ERROR_1
        val testReporter = createReporterImpl("ga-IE")
        testReporter.report(error, loggerMock)
        assertEquals(listOf("Is teachtaireacht earráide é seo [Code: test-case1 URL: $TEST_URL/ga-IE]"), logs)
    }

    @Test(timeout = 300_000)
    fun `locale with missing properties falls back to default properties`() {
        val error = TEST_ERROR_1
        val testReporter = createReporterImpl("es-ES")
        testReporter.report(error, loggerMock)
        assertEquals(listOf("This is a test message [Code: test-case1 URL: $TEST_URL/es-ES]"), logs)
    }

    @Test(timeout = 300_000)
    fun `error code with underscore in translated to resource file successfully`() {
        val error = TEST_ERROR_3
        val testReporter = createReporterImpl("en-US")
        testReporter.report(error, loggerMock)
        assertEquals(listOf("This is the third test message [Code: test-case-3 URL: $TEST_URL/en-US]"), logs)
    }

    @Test(timeout = 3_000)
    fun `exception based error code logs the stack trace`() {
        val error = TestError4(Exception("A test exception"))
        val testReporter = createReporterImpl("en-US")
        testReporter.report(error, loggerMock)
        assertEquals(listOf("This is the fourth test message [Code: test-case4 URL: $TEST_URL/en-US]", error), logs)
    }
}
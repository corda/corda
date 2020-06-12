package net.corda.commmon.logging

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.contains
import net.corda.common.logging.withErrorCodeFor
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.message.SimpleMessage
import org.junit.Test
import kotlin.test.assertEquals

class ExceptionsErrorCodeFunctionsTest {

    @Test(timeout=3_000)
    fun `error code for message prints out message and full stack trace`() {
        val originalMessage = SimpleMessage("This is a test message")
        var previous: Exception? = null
        val throwables = (0..10).map {
            val current = TestThrowable(it, previous)
            previous = current
            current
        }
        val exception = throwables.last()
        val message = originalMessage.withErrorCodeFor(exception, Level.ERROR)
        assertThat(message.formattedMessage, contains("This is a test message".toRegex()))
        for (i in (0..10)) {
            assertThat(message.formattedMessage, contains("This is exception $i".toRegex()))
        }
        assertEquals(message.format, originalMessage.format)
        assertEquals(message.parameters, originalMessage.parameters)
        assertEquals(message.throwable, originalMessage.throwable)
    }

    private class TestThrowable(index: Int, cause: Exception?) : Exception("This is exception $index", cause)
}
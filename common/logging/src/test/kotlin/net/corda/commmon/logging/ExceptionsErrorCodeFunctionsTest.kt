package net.corda.commmon.logging

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.contains
import net.corda.common.logging.withErrorCodeFor
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.message.SimpleMessage
import org.junit.Test
import kotlin.test.assertEquals

class ExceptionsErrorCodeFunctionsTest {
    private companion object {
        private const val EXCEPTION_MESSAGE = "This is exception "
        private const val TEST_MESSAGE = "This is a test message"
        private fun makeChain(previous: Exception?, ttl: Int): Exception {
            val current = TestThrowable(ttl, previous)
            return if (ttl == 0) {
                current
            } else {
                makeChain(current, ttl - 1)
            }
        }
    }

    @Test(timeout=5_000)
    fun `error code for message prints out message and full stack trace`() {
        val originalMessage = SimpleMessage(TEST_MESSAGE)
        val exception = makeChain(null, 10)
        val message = originalMessage.withErrorCodeFor(exception, Level.ERROR)
        assertThat(message.formattedMessage, contains(TEST_MESSAGE.toRegex()))
        for (i in (0..10)) {
            assertThat(message.formattedMessage, contains("$EXCEPTION_MESSAGE $i".toRegex()))
        }
        assertEquals(message.format, originalMessage.format)
        assertEquals(message.parameters, originalMessage.parameters)
        assertEquals(message.throwable, originalMessage.throwable)
    }

    private class TestThrowable(index: Int, cause: Exception?) : Exception("$EXCEPTION_MESSAGE $index", cause)
}
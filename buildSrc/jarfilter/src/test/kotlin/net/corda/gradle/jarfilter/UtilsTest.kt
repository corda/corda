package net.corda.gradle.jarfilter

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.junit.Test
import java.io.IOException
import kotlin.test.assertFailsWith

class UtilsTest {
    @Test
    fun testRethrowingCheckedException() {
        val ex = assertFailsWith<GradleException> { rethrowAsUncheckedException(IOException(MESSAGE)) }
        assertThat(ex)
            .hasMessage(MESSAGE)
            .hasCauseExactlyInstanceOf(IOException::class.java)
    }

    @Test
    fun testRethrowingCheckExceptionWithoutMessage() {
        val ex = assertFailsWith<GradleException> { rethrowAsUncheckedException(IOException()) }
        assertThat(ex)
            .hasMessage("")
            .hasCauseExactlyInstanceOf(IOException::class.java)
    }

    @Test
    fun testRethrowingUncheckedException() {
        val ex = assertFailsWith<IllegalArgumentException> { rethrowAsUncheckedException(IllegalArgumentException(MESSAGE)) }
        assertThat(ex)
            .hasMessage(MESSAGE)
            .hasNoCause()
    }

    @Test
    fun testRethrowingGradleException() {
        val ex = assertFailsWith<InvalidUserDataException> { rethrowAsUncheckedException(InvalidUserDataException(MESSAGE)) }
        assertThat(ex)
            .hasMessage(MESSAGE)
            .hasNoCause()
    }
}

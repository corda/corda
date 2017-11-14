package net.corda.core.internal

import com.nhaarman.mockito_kotlin.argThat
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import net.corda.core.internal.concurrent.fork
import net.corda.core.utilities.getOrThrow
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import org.slf4j.Logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun <T> withSingleThreadExecutor(callable: ExecutorService.() -> T) = Executors.newSingleThreadExecutor().run {
    try {
        fork {}.getOrThrow() // Start the thread.
        callable()
    } finally {
        shutdown()
        while (!awaitTermination(1, TimeUnit.SECONDS)) {
            // Do nothing.
        }
    }
}

class ToggleFieldTest {
    @Test
    fun `toggle is enforced`() {
        listOf(SimpleToggleField<String>("simple"), ThreadLocalToggleField<String>("local"), InheritableThreadLocalToggleField("inheritable")).forEach { field ->
            assertNull(field.get())
            assertThatThrownBy { field.set(null) }.isInstanceOf(IllegalStateException::class.java)
            field.set("hello")
            assertEquals("hello", field.get())
            assertThatThrownBy { field.set("world") }.isInstanceOf(IllegalStateException::class.java)
            assertEquals("hello", field.get())
            assertThatThrownBy { field.set("hello") }.isInstanceOf(IllegalStateException::class.java)
            field.set(null)
            assertNull(field.get())
        }
    }

    @Test
    fun `write-at-most-once field works`() {
        val field = SimpleToggleField<String>("field", true)
        assertNull(field.get())
        assertThatThrownBy { field.set(null) }.isInstanceOf(IllegalStateException::class.java)
        field.set("finalValue")
        assertEquals("finalValue", field.get())
        listOf("otherValue", "finalValue", null).forEach { value ->
            assertThatThrownBy { field.set(value) }.isInstanceOf(IllegalStateException::class.java)
            assertEquals("finalValue", field.get())
        }
    }

    @Test
    fun `thread local works`() {
        val field = ThreadLocalToggleField<String>("field")
        assertNull(field.get())
        field.set("hello")
        assertEquals("hello", field.get())
        withSingleThreadExecutor {
            assertNull(fork(field::get).getOrThrow())
        }
        field.set(null)
        assertNull(field.get())
    }

    @Test
    fun `inheritable thread local works`() {
        val field = InheritableThreadLocalToggleField<String>("field")
        assertNull(field.get())
        field.set("hello")
        assertEquals("hello", field.get())
        withSingleThreadExecutor {
            assertEquals("hello", fork(field::get).getOrThrow())
        }
        field.set(null)
        assertNull(field.get())
    }

    @Test
    fun `existing threads do not inherit`() {
        val field = InheritableThreadLocalToggleField<String>("field")
        withSingleThreadExecutor {
            field.set("hello")
            assertEquals("hello", field.get())
            assertNull(fork(field::get).getOrThrow())
        }
    }

    @Test
    fun `with default exception handler, inherited values are poisoned on clear`() {
        `inherited values are poisoned on clear`(InheritableThreadLocalToggleField("field") { throw it })
    }

    @Test
    fun `with lenient exception handler, inherited values are poisoned on clear`() {
        `inherited values are poisoned on clear`(InheritableThreadLocalToggleField("field") {})
    }

    private fun `inherited values are poisoned on clear`(field: InheritableThreadLocalToggleField<String>) {
        field.set("hello")
        withSingleThreadExecutor {
            assertEquals("hello", fork(field::get).getOrThrow())
            val threadName = fork { Thread.currentThread().name }.getOrThrow()
            listOf(null, "world").forEach { value ->
                field.set(value)
                assertEquals(value, field.get())
                val future = fork(field::get)
                assertThatThrownBy { future.getOrThrow() }
                        .isInstanceOf(ThreadLeakException::class.java)
                        .hasMessageContaining(threadName)
            }
        }
        withSingleThreadExecutor {
            assertEquals("world", fork(field::get).getOrThrow())
        }
    }

    @Test
    fun `with default exception handler, leaked thread is detected as soon as it tries to create another`() {
        val field = InheritableThreadLocalToggleField<String>("field") { throw it }
        field.set("hello")
        withSingleThreadExecutor {
            assertEquals("hello", fork(field::get).getOrThrow())
            field.set(null) // The executor thread is now considered leaked.
            val threadName = fork { Thread.currentThread().name }.getOrThrow()
            val future = fork(::Thread)
            assertThatThrownBy { future.getOrThrow() }
                    .isInstanceOf(ThreadLeakException::class.java)
                    .hasMessageContaining(threadName)
        }
    }

    @Test
    fun `with lenient exception handler, leaked thread logs a warning and does not propagate the holder`() {
        val log = mock<Logger>()
        val field = InheritableThreadLocalToggleField<String>("field", log) {}
        field.set("hello")
        withSingleThreadExecutor {
            assertEquals("hello", fork(field::get).getOrThrow())
            field.set(null) // The executor thread is now considered leaked.
            val threadName = fork { Thread.currentThread().name }.getOrThrow()
            fork {
                verifyNoMoreInteractions(log)
                withSingleThreadExecutor {
                    verify(log).warn(argThat { contains(threadName) })
                    // In practice the new thread is for example a static thread we can't get rid of:
                    assertNull(fork(field::get).getOrThrow())
                }
            }.getOrThrow()
        }
        verifyNoMoreInteractions(log)
    }
}

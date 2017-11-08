package net.corda.core.internal

import net.corda.core.internal.concurrent.fork
import net.corda.core.utilities.getOrThrow
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
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
    fun `inherited values are poisoned on clear`() {
        val field = InheritableThreadLocalToggleField<String>("field")
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
    fun `leaked thread is detected as soon as it tries to create another`() {
        val field = InheritableThreadLocalToggleField<String>("field")
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
}

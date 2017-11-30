package net.corda.core.internal

import com.nhaarman.mockito_kotlin.argThat
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import net.corda.core.internal.concurrent.fork
import net.corda.core.utilities.getOrThrow
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.slf4j.Logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun <T> withSingleThreadExecutor(callable: ExecutorService.() -> T) = Executors.newSingleThreadExecutor().run {
    try {
        fork {}.getOrThrow() // Start the thread.
        callable()
    } finally {
        join()
    }
}

class ToggleFieldTest {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val companionName = javaClass.name

        private fun <T> globalThreadCreationMethod(task: () -> T) = task()
    }

    private val log = mock<Logger>()
    @Rule
    @JvmField
    val verifyNoMoreInteractions = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                base.evaluate()
                verifyNoMoreInteractions(log) // Only on success.
            }
        }
    }

    private fun <T> inheritableThreadLocalToggleField() = InheritableThreadLocalToggleField<T>("inheritable", log) { stack ->
        stack.fold(false) { isAGlobalThreadBeingCreated, e ->
            isAGlobalThreadBeingCreated || (e.className == companionName && e.methodName == "globalThreadCreationMethod")
        }
    }

    @Test
    fun `toggle is enforced`() {
        listOf(SimpleToggleField<String>("simple"), ThreadLocalToggleField<String>("local"), inheritableThreadLocalToggleField()).forEach { field ->
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
        val field = inheritableThreadLocalToggleField<String>()
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
        val field = inheritableThreadLocalToggleField<String>()
        withSingleThreadExecutor {
            field.set("hello")
            assertEquals("hello", field.get())
            assertNull(fork(field::get).getOrThrow())
        }
    }

    @Test
    fun `inherited values are poisoned on clear`() {
        val field = inheritableThreadLocalToggleField<String>()
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
                        .hasMessageContaining("hello")
            }
        }
        withSingleThreadExecutor {
            assertEquals("world", fork(field::get).getOrThrow())
        }
    }

    /** We log a warning rather than failing-fast as the new thread may be an undetected global. */
    @Test
    fun `leaked thread propagates holder to non-global thread, with warning`() {
        val field = inheritableThreadLocalToggleField<String>()
        field.set("hello")
        withSingleThreadExecutor {
            assertEquals("hello", fork(field::get).getOrThrow())
            field.set(null) // The executor thread is now considered leaked.
            fork {
                val leakedThreadName = Thread.currentThread().name
                verifyNoMoreInteractions(log)
                withSingleThreadExecutor {
                    // If ThreadLeakException is seen in practice, these warnings form a trail of where the holder has been:
                    verify(log).warn(argThat { contains(leakedThreadName) && contains("hello") })
                    val newThreadName = fork { Thread.currentThread().name }.getOrThrow()
                    val future = fork(field::get)
                    assertThatThrownBy { future.getOrThrow() }
                            .isInstanceOf(ThreadLeakException::class.java)
                            .hasMessageContaining(newThreadName)
                            .hasMessageContaining("hello")
                    fork {
                        verifyNoMoreInteractions(log)
                        withSingleThreadExecutor {
                            verify(log).warn(argThat { contains(newThreadName) && contains("hello") })
                        }
                    }.getOrThrow()
                }
            }.getOrThrow()
        }
    }

    @Test
    fun `leaked thread does not propagate holder to global thread, with warning`() {
        val field = inheritableThreadLocalToggleField<String>()
        field.set("hello")
        withSingleThreadExecutor {
            assertEquals("hello", fork(field::get).getOrThrow())
            field.set(null) // The executor thread is now considered leaked.
            fork {
                val leakedThreadName = Thread.currentThread().name
                globalThreadCreationMethod {
                    verifyNoMoreInteractions(log)
                    withSingleThreadExecutor {
                        verify(log).warn(argThat { contains(leakedThreadName) && contains("hello") })
                        // In practice the new thread is for example a static thread we can't get rid of:
                        assertNull(fork(field::get).getOrThrow())
                    }
                }
            }.getOrThrow()
        }
    }

    @Test
    fun `non-leaked thread does not propagate holder to global thread, without warning`() {
        val field = inheritableThreadLocalToggleField<String>()
        field.set("hello")
        withSingleThreadExecutor {
            fork {
                assertEquals("hello", field.get())
                globalThreadCreationMethod {
                    withSingleThreadExecutor {
                        assertNull(fork(field::get).getOrThrow())
                    }
                }
            }.getOrThrow()
        }
    }
}

package net.corda.core.concurrent

import com.google.common.util.concurrent.SettableFuture
import com.nhaarman.mockito_kotlin.*
import net.corda.core.getOrThrow
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import org.slf4j.Logger
import java.io.EOFException
import java.util.concurrent.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConcurrencyUtilsTest {
    private val f1 = SettableFuture.create<Int>()
    private val f2 = SettableFuture.create<Double>()
    private var invocations = 0
    private val log: Logger = mock<Logger>()
    @Test
    fun `firstOf short circuit`() {
        // Order not significant in this case:
        val g = firstOf(arrayOf(f2, f1), log) {
            ++invocations
            it.getOrThrow()
        }
        f1.set(100)
        assertEquals(100, g.getOrThrow())
        assertEquals(1, invocations)
        verifyNoMoreInteractions(log)
        val throwable = EOFException("log me")
        f2.setException(throwable)
        assertEquals(1, invocations) // Least astonishing to skip handler side-effects.
        verify(log).error(eq(shortCircuitedTaskFailedMessage), same(throwable))
    }

    @Test
    fun `firstOf re-entrant handler attempt due to cancel`() {
        val futures = arrayOf(f1, f2)
        val g = firstOf(futures, log) {
            ++invocations
            futures.forEach { it.cancel(false) } // One handler invocation queued here.
            it.getOrThrow()
        }
        f1.set(100)
        assertEquals(100, g.getOrThrow())
        assertEquals(1, invocations) // Handler didn't run as g was already done.
        verifyNoMoreInteractions(log) // CancellationException is not logged (if due to cancel).
        assertTrue(f2.isCancelled)
    }

    @Test
    fun `firstOf re-entrant handler attempt not due to cancel`() {
        val futures = arrayOf(f1, f2)
        val fakeCancel = CancellationException()
        val g = firstOf(futures, log) {
            ++invocations
            futures.forEach { it.setException(fakeCancel) } // One handler attempt here.
            it.getOrThrow()
        }
        f1.set(100)
        assertEquals(100, g.getOrThrow())
        assertEquals(1, invocations) // Handler didn't run as g was already done.
        verify(log).error(eq(shortCircuitedTaskFailedMessage), same(fakeCancel))
        assertThatThrownBy { f2.getOrThrow() }.isSameAs(fakeCancel)
    }

    @Test
    fun `firstOf cancel is not special`() {
        val g = firstOf(arrayOf(f2, f1), log) {
            ++invocations
            it.getOrThrow() // This can always do something fancy if 'it' was cancelled.
        }
        f1.cancel(false)
        assertThatThrownBy { g.getOrThrow() }.isInstanceOf(CancellationException::class.java)
        assertEquals(1, invocations)
        verifyNoMoreInteractions(log)
    }
}

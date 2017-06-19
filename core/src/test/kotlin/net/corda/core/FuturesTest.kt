package net.corda.core

import com.google.common.util.concurrent.SettableFuture
import com.nhaarman.mockito_kotlin.*
import net.corda.core.ThenContextImpl.Companion.shortCircuitedTaskFailedMessage
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import org.slf4j.Logger
import java.io.EOFException
import java.util.concurrent.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuturesTest {
    private val f1 = SettableFuture.create<Int>()
    private val f2 = SettableFuture.create<Double>()
    private var invocations = 0
    private val log: Logger = mock<Logger>()
    @Test
    fun `then short circuit`() {
        // Order not significant in this case:
        val g = ThenContextImpl(listOf(f2, f1), log) {
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
    fun `re-entrant handler invocation due to cancel`() {
        val futures = listOf(f1, f2)
        val g = ThenContextImpl(futures, log) {
            ++invocations
            futures.forEach { it.cancel(false) } // One handler invocation queued here.
            it.getOrThrow()
        }
        f1.set(100)
        assertEquals(100, g.getOrThrow())
        assertEquals(1, invocations) // Queued handler didn't run as g was already done.
        verifyNoMoreInteractions(log) // CancellationException is not logged (if due to cancel).
        assertTrue(f2.isCancelled)
    }

    @Test
    fun `re-entrant handler invocation not due to cancel`() {
        val futures = listOf(f1, f2)
        val fakeCancel = CancellationException()
        val g = ThenContextImpl(futures, log) {
            ++invocations
            futures.forEach { it.setException(fakeCancel) } // One handler invocation queued here.
            it.getOrThrow()
        }
        f1.set(100)
        assertEquals(100, g.getOrThrow())
        assertEquals(1, invocations) // Queued handler didn't run as g was already done.
        verify(log).error(eq(shortCircuitedTaskFailedMessage), same(fakeCancel))
        assertThatThrownBy { f2.getOrThrow() }.isSameAs(fakeCancel)
    }

    @Test
    fun `cancel is not special`() {
        val g = ThenContextImpl(listOf(f2, f1), log) {
            ++invocations
            it.getOrThrow() // This can always do something fancy if it was cancelled.
        }
        f1.cancel(false)
        assertThatThrownBy { g.getOrThrow() }.isInstanceOf(CancellationException::class.java)
        assertEquals(1, invocations)
        verifyNoMoreInteractions(log)
    }
}

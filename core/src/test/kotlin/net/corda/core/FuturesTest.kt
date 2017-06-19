package net.corda.core

import com.google.common.util.concurrent.SettableFuture
import com.nhaarman.mockito_kotlin.*
import net.corda.core.ThenContextImpl.Companion.shortCircuitedTaskFailedMessage
import org.junit.Test
import org.slf4j.Logger
import java.io.EOFException
import kotlin.test.assertEquals

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
}

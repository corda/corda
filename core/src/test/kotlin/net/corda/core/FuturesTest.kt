package net.corda.core

import com.google.common.util.concurrent.SettableFuture
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.same
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.slf4j.Logger
import java.io.EOFException
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FuturesTest {
    @Test
    fun `thenAgain works`() {
        val f1 = SettableFuture.create<Int>()
        val f2 = SettableFuture.create<Double>()
        val g = listOf(f1, f2).then {
            if (it == f1) throw thenAgain else it.getOrThrow().toString()
        }
        f1.set(100)
        assertFalse(g.isDone)
        f2.set(200.0)
        assertEquals("200.0", g.getOrThrow())
    }

    @Test
    fun `then short circuit`() {
        val f1 = SettableFuture.create<Int>()
        val f2 = SettableFuture.create<Double>()
        val log = mock<Logger>()
        val g = ThenContextImpl(listOf(f2, f1), log) {
            it.getOrThrow()
        }
        f1.set(100)
        assertEquals(100, g.getOrThrow())
        verifyNoMoreInteractions(log)
        val throwable = EOFException("log me")
        f2.setException(throwable)
        verify(log).error(anyString(), same(throwable))
    }
}

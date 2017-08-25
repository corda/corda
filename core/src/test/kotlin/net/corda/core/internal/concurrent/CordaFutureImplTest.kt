package net.corda.core.internal.concurrent

import com.nhaarman.mockito_kotlin.*
import net.corda.core.concurrent.CordaFuture
import net.corda.core.internal.concurrent.CordaFutures.Companion.andForget
import net.corda.core.internal.concurrent.CordaFutures.Companion.flatMap
import net.corda.core.internal.concurrent.CordaFutures.Companion.fork
import net.corda.core.internal.concurrent.CordaFutures.Companion.map
import net.corda.core.internal.concurrent.CordaFutures.Companion.openFuture
import net.corda.core.internal.concurrent.CordaFutures.Companion.transpose
import net.corda.core.utilities.getOrThrow
import org.assertj.core.api.Assertions
import org.junit.Test
import org.slf4j.Logger
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CordaFutureTest {
    @Test
    fun `fork works`() {
        val e = Executors.newSingleThreadExecutor()
        try {
            assertEquals(100, fork(e) { 100 }.getOrThrow())
            val x = Exception()
            val f = fork(e) { throw x }
            Assertions.assertThatThrownBy { f.getOrThrow() }.isSameAs(x)
        } finally {
            e.shutdown()
        }
    }

    @Test
    fun `if a listener fails its throwable is logged`() {
        val f = CordaFutureImpl<Int>()
        val x = Exception()
        val log = mock<Logger>()
        val flag = AtomicBoolean()
        f.thenImpl(log) { throw x }
        f.thenImpl(log) { flag.set(true) } // Must not be affected by failure of previous listener.
        f.set(100)
        verify(log).error(eq(CordaFutureImpl.listenerFailedMessage), same(x))
        verifyNoMoreInteractions(log)
        assertTrue(flag.get())
    }

    @Test
    fun `map works`() {
        run {
            val f = CordaFutureImpl<Int>()
            val g = map(f) { it * 2 }
            f.set(100)
            assertEquals(200, g.getOrThrow())
        }
        run {
            val f = CordaFutureImpl<Int>()
            val x = Exception()
            val g = map(f) { throw x }
            f.set(100)
            Assertions.assertThatThrownBy { g.getOrThrow() }.isSameAs(x)
        }
        run {
            val block = mock<(Any?) -> Any?>()
            val f = CordaFutureImpl<Int>()
            val g = map(f, block)
            val x = Exception()
            f.setException(x)
            Assertions.assertThatThrownBy { g.getOrThrow() }.isSameAs(x)
            verifyNoMoreInteractions(block)
        }
    }

    @Test
    fun `flatMap works`() {
        run {
            val f = CordaFutureImpl<Int>()
            val g = flatMap(f) { CordaFutureImpl<Int>().apply { set(it * 2) } }
            f.set(100)
            assertEquals(200, g.getOrThrow())
        }
        run {
            val f = CordaFutureImpl<Int>()
            val x = Exception()
            val g = flatMap(f) { CordaFutureImpl<Void>().apply { setException(x) } }
            f.set(100)
            Assertions.assertThatThrownBy { g.getOrThrow() }.isSameAs(x)
        }
        run {
            val f = CordaFutureImpl<Int>()
            val x = Exception()
            val g: CordaFuture<Void> = flatMap(f) { throw x }
            f.set(100)
            Assertions.assertThatThrownBy { g.getOrThrow() }.isSameAs(x)
        }
        run {
            val block = mock<(Any?) -> CordaFuture<*>>()
            val f = CordaFutureImpl<Int>()
            val g = flatMap(f, block)
            val x = Exception()
            f.setException(x)
            Assertions.assertThatThrownBy { g.getOrThrow() }.isSameAs(x)
            verifyNoMoreInteractions(block)
        }
    }

    @Test
    fun `andForget works`() {
        val log = mock<Logger>()
        val throwable = Exception("Boom")
        val executor = Executors.newSingleThreadExecutor()
        andForget(fork(executor) { throw throwable }, log)
        executor.shutdown()
        while (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
            // Do nothing.
        }
        verify(log).error(any(), same(throwable))
    }
}

class TransposeTest {
    private val a = openFuture<Int>()
    private val b = openFuture<Int>()
    private val c = openFuture<Int>()
    private val f = transpose(listOf(a, b, c))
    @Test
    fun `transpose empty collection`() {
        assertEquals(emptyList(), transpose(emptyList<CordaFuture<*>>()).getOrThrow())
    }

    @Test
    fun `transpose values are in the same order as the collection of futures`() {
        b.set(2)
        c.set(3)
        assertFalse(f.isDone)
        a.set(1)
        assertEquals(listOf(1, 2, 3), f.getOrThrow())
    }

    @Test
    fun `transpose throwables are reported in the order they were thrown`() {
        val ax = Exception()
        val bx = Exception()
        val cx = Exception()
        b.setException(bx)
        c.setException(cx)
        assertFalse(f.isDone)
        a.setException(ax)
        Assertions.assertThatThrownBy { f.getOrThrow() }.isSameAs(bx)
        assertEquals(listOf(cx, ax), bx.suppressed.asList())
        assertEquals(emptyList(), ax.suppressed.asList())
        assertEquals(emptyList(), cx.suppressed.asList())
    }

    @Test
    fun `transpose mixture of outcomes`() {
        val bx = Exception()
        val cx = Exception()
        b.setException(bx)
        c.setException(cx)
        assertFalse(f.isDone)
        a.set(100) // Discarded.
        Assertions.assertThatThrownBy { f.getOrThrow() }.isSameAs(bx)
        assertEquals(listOf(cx), bx.suppressed.asList())
        assertEquals(emptyList(), cx.suppressed.asList())
    }
}

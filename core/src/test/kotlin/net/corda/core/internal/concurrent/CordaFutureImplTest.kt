package net.corda.core.internal.concurrent

import com.nhaarman.mockito_kotlin.*
import net.corda.core.concurrent.CordaFuture
import net.corda.core.internal.join
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.rigorousMock
import org.assertj.core.api.Assertions
import org.junit.Test
import org.slf4j.Logger
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CordaFutureTest {
    @Test
    fun `fork works`() {
        val e = Executors.newSingleThreadExecutor()
        try {
            assertEquals(100, e.fork { 100 }.getOrThrow())
            val x = Exception()
            val f = e.fork { throw x }
            Assertions.assertThatThrownBy { f.getOrThrow() }.isSameAs(x)
        } finally {
            e.shutdown()
        }
    }

    @Test
    fun `if a listener fails its throwable is logged`() {
        val f = CordaFutureImpl<Int>()
        val x = Exception()
        val log = rigorousMock<Logger>()
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
            val g = f.map { it * 2 }
            f.set(100)
            assertEquals(200, g.getOrThrow())
        }
        run {
            val f = CordaFutureImpl<Int>()
            val x = Exception()
            val g = f.map { throw x }
            f.set(100)
            Assertions.assertThatThrownBy { g.getOrThrow() }.isSameAs(x)
        }
        run {
            val block = rigorousMock<(Any?) -> Any?>()
            val f = CordaFutureImpl<Int>()
            val g = f.map(block)
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
            val g = f.flatMap { CordaFutureImpl<Int>().apply { set(it * 2) } }
            f.set(100)
            assertEquals(200, g.getOrThrow())
        }
        run {
            val f = CordaFutureImpl<Int>()
            val x = Exception()
            val g = f.flatMap { CordaFutureImpl<Void>().apply { setException(x) } }
            f.set(100)
            Assertions.assertThatThrownBy { g.getOrThrow() }.isSameAs(x)
        }
        run {
            val f = CordaFutureImpl<Int>()
            val x = Exception()
            val g: CordaFuture<Void> = f.flatMap { throw x }
            f.set(100)
            Assertions.assertThatThrownBy { g.getOrThrow() }.isSameAs(x)
        }
        run {
            val block = rigorousMock<(Any?) -> CordaFuture<*>>()
            val f = CordaFutureImpl<Int>()
            val g = f.flatMap(block)
            val x = Exception()
            f.setException(x)
            Assertions.assertThatThrownBy { g.getOrThrow() }.isSameAs(x)
            verifyNoMoreInteractions(block)
        }
    }

    @Test
    fun `andForget works`() {
        val log = rigorousMock<Logger>()
        doNothing().whenever(log).error(any(), any<Throwable>())
        val throwable = Exception("Boom")
        val executor = Executors.newSingleThreadExecutor()
        executor.fork { throw throwable }.andForget(log)
        executor.join()
        verify(log).error(any(), same(throwable))
    }

    @Test
    fun `captureLater works`() {
        val failingFuture = CordaFutureImpl<Int>()
        val anotherFailingFuture = CordaFutureImpl<Int>()
        anotherFailingFuture.captureLater(failingFuture)

        val exception = Exception()
        failingFuture.setException(exception)

        Assertions.assertThatThrownBy { anotherFailingFuture.getOrThrow() }.isSameAs(exception)
    }
}

class TransposeTest {
    private val a = openFuture<Int>()
    private val b = openFuture<Int>()
    private val c = openFuture<Int>()
    private val f = listOf(a, b, c).transpose()
    @Test
    fun `transpose empty collection`() {
        assertEquals(emptyList(), emptyList<CordaFuture<*>>().transpose().getOrThrow())
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

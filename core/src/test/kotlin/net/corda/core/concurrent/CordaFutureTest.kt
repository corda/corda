package net.corda.core.concurrent

import com.nhaarman.mockito_kotlin.*
import org.junit.Test
import kotlin.test.assertEquals
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.slf4j.Logger
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertTrue

class CordaFutureTest {
    @Test
    fun `fork works`() {
        val e = Executors.newSingleThreadExecutor()
        try {
            assertEquals(100, e.fork { 100 }.getOrThrow())
            val x = Exception()
            val f = e.fork { throw x }
            assertThatThrownBy { f.getOrThrow() }.isSameAs(x)
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
            val g = f.map { it * 2 }
            f.set(100)
            assertEquals(200, g.getOrThrow())
        }
        run {
            val f = CordaFutureImpl<Int>()
            val x = Exception()
            val g = f.map { throw x }
            f.set(100)
            assertThatThrownBy { g.getOrThrow() }.isSameAs(x)
        }
        run {
            val block = mock<(Any?) -> Any?>()
            val f = CordaFutureImpl<Int>()
            val g = f.map(block)
            val x = Exception()
            f.setException(x)
            assertThatThrownBy { g.getOrThrow() }.isSameAs(x)
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
            assertThatThrownBy { g.getOrThrow() }.isSameAs(x)
        }
        run {
            val f = CordaFutureImpl<Int>()
            val x = Exception()
            val g: CordaFuture<Void> = f.flatMap { throw x }
            f.set(100)
            assertThatThrownBy { g.getOrThrow() }.isSameAs(x)
        }
        run {
            val block = mock<(Any?) -> CordaFuture<*>>()
            val f = CordaFutureImpl<Int>()
            val g = f.flatMap(block)
            val x = Exception()
            f.setException(x)
            assertThatThrownBy { g.getOrThrow() }.isSameAs(x)
            verifyNoMoreInteractions(block)
        }
    }

    @Test
    fun `andForget works`() {
        val log = mock<Logger>()
        val throwable = Exception("Boom")
        val executor = Executors.newSingleThreadExecutor()
        executor.fork { throw throwable }.andForget(log)
        executor.shutdown()
        while (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
            // Do nothing.
        }
        verify(log).error(any(), same(throwable))
    }
}

package net.corda.core

import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.same
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import net.corda.core.ThenContext.then
import org.assertj.core.api.Assertions.*
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.slf4j.Logger
import rx.subjects.PublishSubject
import java.io.EOFException
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UtilsTest {
    @Test
    fun `toFuture - single item observable`() {
        val subject = PublishSubject.create<String>()
        val future = subject.toFuture()
        subject.onNext("Hello")
        assertThat(future.getOrThrow()).isEqualTo("Hello")
    }

    @Test
    fun `toFuture - empty obserable`() {
        val subject = PublishSubject.create<String>()
        val future = subject.toFuture()
        subject.onCompleted()
        assertThatExceptionOfType(NoSuchElementException::class.java).isThrownBy {
            future.getOrThrow()
        }
    }

    @Test
    fun `toFuture - more than one item observable`() {
        val subject = PublishSubject.create<String>()
        val future = subject.toFuture()
        subject.onNext("Hello")
        subject.onNext("World")
        subject.onCompleted()
        assertThat(future.getOrThrow()).isEqualTo("Hello")
    }

    @Test
    fun `toFuture - erroring observable`() {
        val subject = PublishSubject.create<String>()
        val future = subject.toFuture()
        val exception = Exception("Error")
        subject.onError(exception)
        assertThatThrownBy {
            future.getOrThrow()
        }.isSameAs(exception)
    }

    @Test
    fun `toFuture - cancel`() {
        val subject = PublishSubject.create<String>()
        val future = subject.toFuture()
        future.cancel(false)
        assertThat(subject.hasObservers()).isFalse()
        subject.onNext("Hello")
        assertThatExceptionOfType(CancellationException::class.java).isThrownBy {
            future.get()
        }
    }

    @Test
    fun `andForget works`() {
        val log = mock<Logger>()
        val throwable = Exception("Boom")
        val executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
        executor.submit { throw throwable }.andForget(log)
        executor.shutdown()
        while (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
            // Do nothing.
        }
        verify(log).error(anyString(), same(throwable))
    }

    @Test
    fun `thenAgain works`() {
        val f1 = SettableFuture.create<Int>()
        val f2 = SettableFuture.create<Double>()
        val g = listOf(f1, f2) then {
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
        val g = ThenContext.then(log, listOf(f2, f1)) {
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

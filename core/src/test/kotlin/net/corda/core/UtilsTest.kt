package net.corda.core

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.warnOnce
import org.assertj.core.api.Assertions.*
import org.junit.Test
import org.slf4j.Logger
import rx.subjects.PublishSubject
import java.util.*
import java.util.concurrent.CancellationException

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
    fun `warnOnce works, but the backing cache grows only to a maximum size`() {
        val MAX_SIZE = 100

        val logger = mock<Logger>()
        logger.warnOnce("a")
        logger.warnOnce("b")
        logger.warnOnce("b")

        // This should cause the eviction of "a".
        (1..MAX_SIZE).forEach { logger.warnOnce("$it") }
        logger.warnOnce("a")

        // "a" should be logged twice because it was evicted.
        verify(logger, times(2)).warn("a")

        // "b" should be logged only once because there was no eviction.
        verify(logger, times(1)).warn("b")
    }
}

package net.corda.core

import org.assertj.core.api.Assertions.*
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import rx.subjects.PublishSubject
import java.util.concurrent.CancellationException
import java.util.stream.Stream
import kotlin.NoSuchElementException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
    fun `IntProgression stream works`() {
        assertArrayEquals(intArrayOf(1, 2, 3, 4), (1..4).stream().toArray())
        assertArrayEquals(intArrayOf(1, 2, 3, 4), (1 until 5).stream().toArray())
        assertArrayEquals(intArrayOf(1, 3), (1..4 step 2).stream().toArray())
        assertArrayEquals(intArrayOf(1, 3), (1..3 step 2).stream().toArray())
        assertArrayEquals(intArrayOf(), (1..0).stream().toArray())
    }

    @Test
    fun `Stream toTypedArray works`() {
        val a: Array<String> = Stream.of("one", "two").toTypedArray()
        assertEquals(Array<String>::class.java, a.javaClass)
        assertArrayEquals(arrayOf("one", "two"), a)
        val b: Array<String?> = Stream.of("one", "two", null).toTypedArray()
        assertEquals(Array<String?>::class.java, b.javaClass)
        assertArrayEquals(arrayOf("one", "two", null), b)
    }

    @Test
    fun `Stream single works`() {
        assertEquals("item", Stream.of("item").single())
        assertFailsWith(NoSuchElementException::class, "E") { Stream.empty<Any>().single(emptyMessage = "E") }
        assertFailsWith(IllegalArgumentException::class, "+") { Stream.of(1, 2).single(moreThanOneMessage = "+") }
    }
}

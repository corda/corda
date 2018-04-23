/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.concurrent

import com.nhaarman.mockito_kotlin.*
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.rigorousMock
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import org.slf4j.Logger
import java.io.EOFException
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConcurrencyUtilsTest {
    private val f1 = openFuture<Int>()
    private val f2 = openFuture<Double>()
    private var invocations = 0
    private val log = rigorousMock<Logger>().also {
        doNothing().whenever(it).error(any(), any<Throwable>())
    }

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
        verifyNoMoreInteractions(log)
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

    /**
     * Note that if you set CancellationException on CompletableFuture it will report isCancelled.
     */
    @Test
    fun `firstOf re-entrant handler attempt not due to cancel`() {
        val futures = arrayOf(f1, f2)
        val nonCancel = IllegalStateException()
        val g = firstOf(futures, log) {
            ++invocations
            futures.forEach { it.setException(nonCancel) } // One handler attempt here.
            it.getOrThrow()
        }
        f1.set(100)
        assertEquals(100, g.getOrThrow())
        assertEquals(1, invocations) // Handler didn't run as g was already done.
        verify(log).error(eq(shortCircuitedTaskFailedMessage), same(nonCancel))
        verifyNoMoreInteractions(log)
        assertThatThrownBy { f2.getOrThrow() }.isSameAs(nonCancel)
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

    @Test
    fun `match does not pass failure of success block into the failure block`() {
        val f = CompletableFuture.completedFuture(100)
        val successes = mutableListOf<Any?>()
        val failures = mutableListOf<Any?>()
        val x = Throwable()
        assertThatThrownBy {
            f.match({
                successes.add(it)
                throw x
            }, failures::add)
        }.isSameAs(x)
        assertEquals(listOf<Any?>(100), successes)
        assertEquals(emptyList<Any?>(), failures)
    }

    @Test
    fun `match does not pass ExecutionException to failure block`() {
        val e = Throwable()
        val f = CompletableFuture<Void>().apply { completeExceptionally(e) }
        val successes = mutableListOf<Any?>()
        val failures = mutableListOf<Any?>()
        val x = Throwable()
        assertThatThrownBy {
            f.match(successes::add, {
                failures.add(it)
                throw x
            })
        }.isSameAs(x)
        assertEquals(emptyList<Any?>(), successes)
        assertEquals(listOf<Any?>(e), failures)
    }
}

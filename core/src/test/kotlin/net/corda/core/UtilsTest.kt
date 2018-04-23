/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core

import net.corda.core.utilities.getOrThrow
import org.assertj.core.api.Assertions.*
import org.junit.Test
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
}

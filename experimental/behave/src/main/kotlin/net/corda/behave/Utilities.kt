package net.corda.behave

import java.time.Duration
import java.util.concurrent.CountDownLatch

// TODO Most of these are available in corda core

val Int.millisecond: Duration
    get() = Duration.ofMillis(this.toLong())

val Int.milliseconds: Duration
    get() = Duration.ofMillis(this.toLong())

val Int.second: Duration
    get() = Duration.ofSeconds(this.toLong())

val Int.seconds: Duration
    get() = Duration.ofSeconds(this.toLong())

val Int.minute: Duration
    get() = Duration.ofMinutes(this.toLong())

val Int.minutes: Duration
    get() = Duration.ofMinutes(this.toLong())

fun CountDownLatch.await(duration: Duration) =
        this.await(duration.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)

fun Process.waitFor(duration: Duration) =
        this.waitFor(duration.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)

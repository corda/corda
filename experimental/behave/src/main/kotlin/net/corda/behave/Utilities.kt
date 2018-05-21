package net.corda.behave

import java.time.Duration
import java.util.concurrent.CountDownLatch

fun CountDownLatch.await(duration: Duration) =
        this.await(duration.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)

fun Process.waitFor(duration: Duration) =
        this.waitFor(duration.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)

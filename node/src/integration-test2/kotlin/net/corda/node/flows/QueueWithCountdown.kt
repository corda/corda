package net.corda.node.flows

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Helper class for waiting until another thread has put a set number of objects
 * into a queue.
 */
internal class QueueWithCountdown<E> private constructor(
        count: Int = 0,
        private val queue: ConcurrentLinkedQueue<E>
) : Collection<E> by queue {

    constructor(count: Int = 0) : this(count, ConcurrentLinkedQueue<E>())

    private val latch: CountDownLatch = CountDownLatch(count)

    fun add(element: E) {
        queue.add(element)
        latch.countDown()
    }

    fun await() = latch.await()

    fun await(timeout: Long, unit: TimeUnit) = latch.await(timeout, unit)
}